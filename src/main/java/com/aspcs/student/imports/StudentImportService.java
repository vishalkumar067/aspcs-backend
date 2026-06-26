package com.aspcs.student.imports;

import com.aspcs.student.Student;
import com.aspcs.student.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

// ─── DTOs ──────────────────────────────────────────────────────
class ImportRowResult {
    public int rowNumber;
    public String admissionNo;
    public String outcome; // CREATED, UPDATED, FAILED
    public String errorMessage;
}

class ImportSummary {
    public UUID batchId;
    public String fileName;
    public int totalRows;
    public int createdCount;
    public int updatedCount;
    public int failedCount;
    public List<ImportRowResult> rows = new ArrayList<>();
}

// ─── Service ───────────────────────────────────────────────────
// Accepts .csv or .xlsx. Column headers are matched case-insensitively and
// order-independently (anyone can upload, so the file's exact column order
// shouldn't matter) against a fixed set of recognized header names — see
// HEADER_ALIASES. Unrecognized columns are ignored rather than rejected,
// so an extra "Notes" column some staff member adds doesn't break the
// whole import.
@Slf4j
@Service
@RequiredArgsConstructor
class StudentImportService {

    private final StudentRepository studentRepo;
    private final StudentImportBatchRepository batchRepo;
    private final StudentImportRowRepository rowRepo;
    private final PasswordEncoder encoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
    };

    // canonical field -> accepted header spellings (lowercase, trimmed)
    private static final Map<String, List<String>> HEADER_ALIASES = Map.ofEntries(
            Map.entry("admissionNo",   List.of("admission no", "admission number", "admissionno")),
            Map.entry("rollNo",        List.of("roll no", "roll number", "rollno")),
            Map.entry("fullName",      List.of("full name", "student name", "name", "fullname")),
            Map.entry("dateOfBirth",   List.of("date of birth", "dob", "birth date")),
            Map.entry("gender",        List.of("gender", "sex")),
            Map.entry("religion",      List.of("religion")),
            Map.entry("category",      List.of("category", "caste category")),
            Map.entry("currentClass",  List.of("class", "current class")),
            Map.entry("section",       List.of("section")),
            Map.entry("admissionDate", List.of("admission date")),
            Map.entry("fatherName",    List.of("father name", "father's name")),
            Map.entry("motherName",    List.of("mother name", "mother's name")),
            Map.entry("guardianPhone", List.of("guardian phone", "parent phone", "phone", "mobile", "contact number")),
            Map.entry("guardianEmail", List.of("guardian email", "parent email", "email")),
            Map.entry("address",       List.of("address")),
            Map.entry("city",          List.of("city")),
            Map.entry("state",         List.of("state")),
            Map.entry("pincode",       List.of("pincode", "pin code", "zip", "postal code")),
            Map.entry("previousSchool", List.of("previous school"))
    );

    public ImportSummary importFile(MultipartFile file, UUID importedBy) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        List<Map<String, String>> rows = filename.toLowerCase().endsWith(".csv")
                ? parseCsv(file)
                : parseXlsx(file);

        StudentImportBatch batch = new StudentImportBatch();
        batch.setFileName(filename);
        batch.setTotalRows(rows.size());
        batch = batchRepo.save(batch);

        ImportSummary summary = new ImportSummary();
        summary.batchId = batch.getId();
        summary.fileName = filename;
        summary.totalRows = rows.size();

        int rowNum = 1; // 1-indexed, matching the spreadsheet's first data row
        for (Map<String, String> row : rows) {
            ImportRowResult result = processRow(batch.getId(), rowNum, row);
            summary.rows.add(result);
            switch (result.outcome) {
                case "CREATED" -> summary.createdCount++;
                case "UPDATED" -> summary.updatedCount++;
                default -> summary.failedCount++;
            }
            rowNum++;
        }

        batch.setCreatedCount(summary.createdCount);
        batch.setUpdatedCount(summary.updatedCount);
        batch.setFailedCount(summary.failedCount);
        batch.setImportedBy(importedBy);
        batchRepo.save(batch);

        return summary;
    }

    private ImportRowResult processRow(UUID batchId, int rowNum, Map<String, String> row) {
        ImportRowResult result = new ImportRowResult();
        result.rowNumber = rowNum;

        StudentImportRow auditRow = new StudentImportRow();
        auditRow.setBatchId(batchId);
        auditRow.setRowNumber(rowNum);

        try {
            String admissionNo = trimToNull(row.get("admissionNo"));
            String fullName = trimToNull(row.get("fullName"));
            String currentClass = trimToNull(row.get("currentClass"));
            String guardianPhone = trimToNull(row.get("guardianPhone"));

            result.admissionNo = admissionNo;
            auditRow.setAdmissionNo(admissionNo);

            // Required-field validation — fail this row only, never the batch.
            List<String> missing = new ArrayList<>();
            if (admissionNo == null) missing.add("Admission No");
            if (fullName == null) missing.add("Full Name");
            if (currentClass == null) missing.add("Class");
            if (guardianPhone == null) missing.add("Guardian Phone");
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Missing required field(s): " + String.join(", ", missing));
            }

            Optional<Student> existing = studentRepo.findByAdmissionNoIgnoreCase(admissionNo);

            boolean isUpdate = existing.isPresent();
            Student s = existing.orElseGet(Student::new);

            String beforeJson = isUpdate ? toJson(s) : null;

            s.setAdmissionNo(admissionNo);
            s.setFullName(fullName);
            s.setCurrentClass(currentClass);
            s.setGuardianPhone(guardianPhone);
            setIfPresent(row, "rollNo", s::setRollNo);
            setIfPresent(row, "gender", s::setGender);
            setIfPresent(row, "religion", s::setReligion);
            setIfPresent(row, "category", s::setCategory);
            setIfPresent(row, "section", s::setSection);
            setIfPresent(row, "fatherName", s::setFatherName);
            setIfPresent(row, "motherName", s::setMotherName);
            setIfPresent(row, "guardianEmail", s::setGuardianEmail);
            setIfPresent(row, "address", s::setAddress);
            setIfPresent(row, "city", s::setCity);
            setIfPresent(row, "state", s::setState);
            setIfPresent(row, "pincode", s::setPincode);
            setIfPresent(row, "previousSchool", s::setPreviousSchool);

            LocalDate dob = parseDate(row.get("dateOfBirth"));
            if (dob != null) s.setDateOfBirth(dob);

            LocalDate admissionDate = parseDate(row.get("admissionDate"));
            s.setAdmissionDate(admissionDate != null ? admissionDate : (isUpdate ? s.getAdmissionDate() : LocalDate.now()));

            if (!isUpdate) {
                s.setStatus("ACTIVE");
                s.setUsername(admissionNo.replaceAll("[/\\\\]", "").toLowerCase());
                s.setPassword(encoder.encode(guardianPhone));
            }

            Student saved = studentRepo.save(s);

            result.outcome = isUpdate ? "UPDATED" : "CREATED";
            auditRow.setOutcome(result.outcome);
            auditRow.setStudentId(saved.getId());
            auditRow.setBeforeData(beforeJson);
            auditRow.setAfterData(toJson(saved));

        } catch (Exception e) {
            result.outcome = "FAILED";
            result.errorMessage = e.getMessage();
            auditRow.setOutcome("FAILED");
            auditRow.setErrorMessage(e.getMessage());
            log.warn("Import row {} failed: {}", rowNum, e.getMessage());
        }

        rowRepo.save(auditRow);
        return result;
    }

    private void setIfPresent(Map<String, String> row, String field, java.util.function.Consumer<String> setter) {
        String value = trimToNull(row.get(field));
        if (value != null) setter.accept(value);
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate parseDate(String raw) {
        String value = trimToNull(raw);
        if (value == null) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(value, fmt); } catch (Exception ignored) { /* try next format */ }
        }
        return null; // unparseable date is silently skipped, not a row failure
    }

    private String toJson(Student s) {
        try { return objectMapper.writeValueAsString(s); }
        catch (Exception e) { return null; }
    }

    // ─── File parsing ────────────────────────────────────────
    private List<Map<String, String>> parseCsv(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) return rows;
            List<String> canonicalHeaders = mapHeaders(splitCsvLine(headerLine));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> values = splitCsvLine(line);
                rows.add(zipToMap(canonicalHeaders, values));
            }
        }
        return rows;
    }

    // Minimal CSV split: handles quoted fields with embedded commas, which
    // is the one thing a naive String.split(",") gets wrong on real-world
    // exports (e.g. an address field containing a comma).
    private List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }

    private List<Map<String, String>> parseXlsx(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        // WorkbookFactory.create(InputStream) requires a stream that supports
        // mark/reset; a raw MultipartFile stream does not guarantee this, so
        // it must be wrapped — without this it can throw at runtime on a real
        // upload despite working fine against some in-memory test streams.
        try (Workbook wb = WorkbookFactory.create(
                new java.io.BufferedInputStream(file.getInputStream()))) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) return rows;

            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            List<String> rawHeaders = new ArrayList<>();
            for (Cell cell : headerRow) {
                rawHeaders.add(formatter.formatCellValue(cell));
            }
            List<String> canonicalHeaders = mapHeaders(rawHeaders);

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row dataRow = sheet.getRow(r);
                if (dataRow == null) continue;
                List<String> values = new ArrayList<>();
                for (int c = 0; c < rawHeaders.size(); c++) {
                    Cell cell = dataRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values.add(cell != null ? formatter.formatCellValue(cell) : "");
                }
                // Skip fully-blank rows (common at the end of a sheet).
                if (values.stream().allMatch(v -> v == null || v.isBlank())) continue;
                rows.add(zipToMap(canonicalHeaders, values));
            }
        }
        return rows;
    }

    private List<String> mapHeaders(List<String> rawHeaders) {
        List<String> canonical = new ArrayList<>();
        for (String raw : rawHeaders) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase();
            String matched = null;
            for (Map.Entry<String, List<String>> entry : HEADER_ALIASES.entrySet()) {
                if (entry.getValue().contains(normalized)) {
                    matched = entry.getKey();
                    break;
                }
            }
            canonical.add(matched); // null = unrecognized column, ignored downstream
        }
        return canonical;
    }

    private Map<String, String> zipToMap(List<String> canonicalHeaders, List<String> values) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < canonicalHeaders.size() && i < values.size(); i++) {
            String key = canonicalHeaders.get(i);
            if (key != null) map.put(key, values.get(i));
        }
        return map;
    }
}
