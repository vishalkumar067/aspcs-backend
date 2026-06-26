package com.aspcs.student.imports;

import com.aspcs.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/students/import")
@RequiredArgsConstructor
class StudentImportController {

    private final StudentImportService importService;
    private final StudentImportBatchRepository batchRepo;
    private final StudentImportRowRepository rowRepo;

    private static final String[] TEMPLATE_HEADERS = {
            "Admission No", "Roll No", "Full Name", "Date of Birth", "Gender", "Religion",
            "Category", "Class", "Section", "Admission Date", "Father Name", "Mother Name",
            "Guardian Phone", "Guardian Email", "Address", "City", "State", "Pincode", "Previous School"
    };

    // Any authenticated ERP user can run an import, per the school's decision —
    // including TEACHER. Every run is fully audited (see /import/history) so an
    // admin can review exactly who changed what.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<ImportSummary>> importStudents(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal com.aspcs.auth.entity.AdminUser user) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!name.endsWith(".csv") && !name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new IllegalArgumentException("Only .csv, .xlsx, or .xls files are supported");
        }
        // 5MB cap — generous for a few thousand rows of text, small enough that
        // a mistaken upload (e.g. someone picks the wrong, huge file) fails fast
        // with a clear error instead of tying up the request thread.
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File too large (max 5MB)");
        }

        ImportSummary summary = importService.importFile(file, user.getId());
        String message = String.format("Processed %d rows: %d created, %d updated, %d failed",
                summary.totalRows, summary.createdCount, summary.updatedCount, summary.failedCount);
        return ResponseEntity.ok(ApiResponse.ok(summary, message));
    }

    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        try (Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Students");
            Row header = sheet.createRow(0);
            CellStyle boldStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            boldStyle.setFont(bold);

            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(boldStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }
            // One example row so a non-technical uploader sees the expected format,
            // especially the date format, which is the single most common mistake.
            Row example = sheet.createRow(1);
            String[] sample = {"ADM2026001", "12", "Rahul Kumar", "2015-04-12", "Male", "Hindu",
                    "GEN", "Class V", "A", "2026-04-01", "Suresh Kumar", "Anita Kumar",
                    "9123456780", "parent@example.com", "123 MG Road", "Patna", "Bihar", "800001", ""};
            for (int i = 0; i < sample.length; i++) {
                example.createCell(i).setCellValue(sample[i]);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename("student_import_template.xlsx", java.nio.charset.StandardCharsets.UTF_8)
                            .build());
            return new ResponseEntity<>(out.toByteArray(), headers, HttpStatus.OK);
        }
    }

    // Admin-only oversight: every import batch, by anyone, with full row detail.
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<StudentImportBatch>>> getHistory() {
        return ResponseEntity.ok(ApiResponse.ok(batchRepo.findAllByOrderByImportedAtDesc()));
    }

    @GetMapping("/history/{batchId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<StudentImportRow>>> getBatchDetail(@PathVariable UUID batchId) {
        return ResponseEntity.ok(ApiResponse.ok(rowRepo.findByBatchIdOrderByRowNumber(batchId)));
    }
}
