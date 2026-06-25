package com.aspcs.progressreport;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

// Everything PdfGenerationService needs to render one report. Built by the
// orchestrating service (ProgressReportService, next file) from
// StudentAssessment + Student + Subject + ReportingCycle data, so this class
// itself has no JPA/repository dependencies — it just lays out a PDF.
record ReportPdfData(
        String studentName,
        String admissionNo,
        String className,
        String section,
        String cycleName,
        String cycleDateRange,
        String studentPhotoUrl,         // nullable
        Integer workingDays,
        Integer presentDays,
        String attendancePct,           // pre-formatted, e.g. "94.00%"
        List<SubjectRow> subjects,
        Short disciplineScore, Short homeworkScore, Short participationScore,
        Short punctualityScore, Short communicationScore, Short teamworkScore,
        String behaviourOverall,        // pre-formatted, e.g. "4.50 / 5"
        String teacherRemarks,
        String principalRemarks,
        String overallPerformance,
        String generatedDate,           // pre-formatted
        String qrVerificationUrl
) {}

record SubjectRow(String subjectName, String marksOrRating) {}

@Service
@RequiredArgsConstructor
class PdfGenerationService {

    @Value("${app.school.name:Acharya Shree Sudarshan Patna Central School}")
    private String schoolName;

    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    // Maroon/crimson brand colors, matching the frontend palette.
    private static final int[] MAROON = {0x6B, 0x0F, 0x1A};
    private static final int[] CRIMSON = {0xC4, 0x1E, 0x3A};
    private static final int[] GOLD = {0xD4, 0xA8, 0x43};
    private static final int[] DARK = {0x0D, 0x06, 0x08};

    public byte[] generate(ReportPdfData rawData) throws IOException {
        ReportPdfData data = sanitizeData(rawData);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_HEIGHT - MARGIN;

                y = drawHeader(doc, cs, bold, regular, data, y);
                y -= 20;
                y = drawStudentInfo(cs, bold, regular, data, y);
                y -= 15;
                y = drawAttendanceSummary(cs, bold, regular, data, y);
                y -= 15;
                y = drawSubjectTable(cs, bold, regular, data, y);
                y -= 15;
                y = drawBehaviourSection(cs, bold, regular, data, y);
                y -= 15;
                y = drawRemarks(cs, bold, regular, data, y);
                y -= 15;
                drawFooterWithQr(doc, cs, bold, regular, data, y);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private float drawHeader(PDDocument doc, PDPageContentStream cs, PDFont bold, PDFont regular,
                              ReportPdfData data, float y) throws IOException {
        // Logo (bundled in resources/static)
        try (InputStream logoStream = getClass().getResourceAsStream("/static/school-logo.png")) {
            if (logoStream != null) {
                PDImageXObject logo = PDImageXObject.createFromByteArray(doc, logoStream.readAllBytes(), "logo");
                float logoSize = 50f;
                cs.drawImage(logo, MARGIN, y - logoSize + 12, logoSize, logoSize);
            }
        } catch (IOException e) {
            // Logo is decorative; never fail report generation because of it.
        }

        setColor(cs, MAROON, true);
        cs.beginText();
        cs.setFont(bold, 16);
        cs.newLineAtOffset(MARGIN + 65, y);
        cs.showText(schoolName);
        cs.endText();

        setColor(cs, DARK, true);
        cs.beginText();
        cs.setFont(regular, 10);
        cs.newLineAtOffset(MARGIN + 65, y - 18);
        cs.showText("Student Progress Report");
        cs.endText();

        setColor(cs, CRIMSON, true);
        cs.setLineWidth(1.5f);
        cs.moveTo(MARGIN, y - 35);
        cs.lineTo(PAGE_WIDTH - MARGIN, y - 35);
        cs.stroke();

        drawStudentPhoto(doc, cs, data, y);

        return y - 35;
    }

    // Top-right corner. Fetched live from the studentPhotoUrl (Cloudinary).
    // A missing photo or any fetch failure is non-fatal — the report still
    // generates, just without the photo box filled in.
    private void drawStudentPhoto(PDDocument doc, PDPageContentStream cs, ReportPdfData data, float headerY) {
        if (data.studentPhotoUrl() == null || data.studentPhotoUrl().isBlank()) return;
        try {
            byte[] photoBytes;
            java.net.URLConnection conn = new URL(data.studentPhotoUrl()).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            try (InputStream in = conn.getInputStream()) {
                photoBytes = in.readAllBytes();
            }
            PDImageXObject photo = PDImageXObject.createFromByteArray(doc, photoBytes, "studentPhoto");
            float boxSize = 60f;
            float x = PAGE_WIDTH - MARGIN - boxSize;
            float y = headerY - boxSize + 12;
            cs.drawImage(photo, x, y, boxSize, boxSize);
        } catch (Exception e) {
            // Non-fatal: photo is a nice-to-have, not a blocker for report generation.
        }
    }

    private float drawStudentInfo(PDPageContentStream cs, PDFont bold, PDFont regular,
                                    ReportPdfData data, float y) throws IOException {
        setColor(cs, DARK, false);
        float lineHeight = 16f;
        float labelX = MARGIN;
        float valueX = MARGIN + 110;

        Object[][] rows = {
            {"Student Name:", data.studentName()},
            {"Admission No:", data.admissionNo()},
            {"Class / Section:", data.className() + (data.section() != null ? " - " + data.section() : "")},
            {"Reporting Cycle:", data.cycleName() + "  (" + data.cycleDateRange() + ")"},
        };

        for (Object[] row : rows) {
            drawLabelValue(cs, bold, regular, labelX, valueX, y, (String) row[0], (String) row[1]);
            y -= lineHeight;
        }
        return y;
    }

    private void drawLabelValue(PDPageContentStream cs, PDFont bold, PDFont regular,
                                 float labelX, float valueX, float y, String label, String value) throws IOException {
        cs.beginText();
        cs.setFont(bold, 10);
        cs.newLineAtOffset(labelX, y);
        cs.showText(label);
        cs.endText();

        cs.beginText();
        cs.setFont(regular, 10);
        cs.newLineAtOffset(valueX, y);
        cs.showText(value != null ? value : "-");
        cs.endText();
    }

    private float drawAttendanceSummary(PDPageContentStream cs, PDFont bold, PDFont regular,
                                         ReportPdfData data, float y) throws IOException {
        y = drawSectionHeading(cs, bold, "Attendance Summary", y);
        Object[][] rows = {
            {"Working Days:", String.valueOf(data.workingDays() != null ? data.workingDays() : "-")},
            {"Present Days:", String.valueOf(data.presentDays() != null ? data.presentDays() : "-")},
            {"Attendance %:", data.attendancePct() != null ? data.attendancePct() : "-"},
        };
        for (Object[] row : rows) {
            drawLabelValue(cs, bold, regular, MARGIN, MARGIN + 110, y, (String) row[0], (String) row[1]);
            y -= 16f;
        }
        return y;
    }

    private float drawSubjectTable(PDPageContentStream cs, PDFont bold, PDFont regular,
                                    ReportPdfData data, float y) throws IOException {
        y = drawSectionHeading(cs, bold, "Subject-wise Performance", y);

        float colSubjectX = MARGIN;
        float colScoreX = MARGIN + 280;
        float rowHeight = 16f;

        cs.beginText();
        cs.setFont(bold, 10);
        cs.newLineAtOffset(colSubjectX, y);
        cs.showText("Subject");
        cs.endText();
        cs.beginText();
        cs.setFont(bold, 10);
        cs.newLineAtOffset(colScoreX, y);
        cs.showText("Marks / Rating");
        cs.endText();
        y -= rowHeight;

        setColor(cs, MAROON, true);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y + 4);
        cs.lineTo(PAGE_WIDTH - MARGIN, y + 4);
        cs.stroke();
        setColor(cs, DARK, false);
        y -= 4;

        if (data.subjects() != null) {
            for (SubjectRow row : data.subjects()) {
                cs.beginText();
                cs.setFont(regular, 10);
                cs.newLineAtOffset(colSubjectX, y);
                cs.showText(row.subjectName());
                cs.endText();
                cs.beginText();
                cs.setFont(regular, 10);
                cs.newLineAtOffset(colScoreX, y);
                cs.showText(row.marksOrRating() != null ? row.marksOrRating() : "-");
                cs.endText();
                y -= rowHeight;
            }
        }
        return y;
    }

    private float drawBehaviourSection(PDPageContentStream cs, PDFont bold, PDFont regular,
                                        ReportPdfData data, float y) throws IOException {
        y = drawSectionHeading(cs, bold, "Behaviour & Participation", y);
        Object[][] rows = {
            {"Discipline:", fmt(data.disciplineScore())},
            {"Homework:", fmt(data.homeworkScore())},
            {"Participation:", fmt(data.participationScore())},
            {"Punctuality:", fmt(data.punctualityScore())},
            {"Communication:", fmt(data.communicationScore())},
            {"Teamwork:", fmt(data.teamworkScore())},
        };
        float colWidth = (PAGE_WIDTH - 2 * MARGIN) / 2;
        for (int i = 0; i < rows.length; i++) {
            float x = MARGIN + (i % 2) * colWidth;
            float rowY = y - (i / 2) * 16f;
            drawLabelValue(cs, bold, regular, x, x + 90, rowY, (String) rows[i][0], (String) rows[i][1]);
        }
        y -= ((rows.length + 1) / 2) * 16f;
        y -= 4;
        drawLabelValue(cs, bold, regular, MARGIN, MARGIN + 110, y, "Overall Behaviour:", data.behaviourOverall());
        return y - 16f;
    }

    private String fmt(Short score) {
        return score != null ? score + " / 5" : "-";
    }

    private float drawRemarks(PDPageContentStream cs, PDFont bold, PDFont regular,
                               ReportPdfData data, float y) throws IOException {
        y = drawSectionHeading(cs, bold, "Remarks", y);

        y = drawWrappedParagraph(cs, bold, regular, "Teacher's Remarks:",
                data.teacherRemarks(), y);
        y -= 6;
        if (data.principalRemarks() != null && !data.principalRemarks().isBlank()) {
            y = drawWrappedParagraph(cs, bold, regular, "Principal's Remarks:",
                    data.principalRemarks(), y);
        }
        y -= 6;
        drawLabelValue(cs, bold, regular, MARGIN, MARGIN + 130, y, "Overall Performance:",
                humanize(data.overallPerformance()));
        return y - 16f;
    }

    private String humanize(String label) {
        if (label == null || label.isBlank()) return "-";
        String spaced = label.replace("_", " ");
        return spaced.substring(0, 1) + spaced.substring(1).toLowerCase();
    }

    private float drawWrappedParagraph(PDPageContentStream cs, PDFont bold, PDFont regular,
                                        String label, String text, float y) throws IOException {
        cs.beginText();
        cs.setFont(bold, 10);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(label);
        cs.endText();
        y -= 14f;

        String content = (text == null || text.isBlank()) ? "Not provided." : text;
        float maxWidth = PAGE_WIDTH - 2 * MARGIN;
        for (String line : wrapText(content, regular, 10, maxWidth)) {
            cs.beginText();
            cs.setFont(regular, 10);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(line);
            cs.endText();
            y -= 13f;
        }
        return y;
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(candidate) / 1000 * fontSize;
            if (width > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private float drawSectionHeading(PDPageContentStream cs, PDFont bold, String title, float y) throws IOException {
        setColor(cs, CRIMSON, true);
        cs.beginText();
        cs.setFont(bold, 12);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(title);
        cs.endText();
        setColor(cs, DARK, false);
        return y - 18f;
    }

    private void drawFooterWithQr(PDDocument doc, PDPageContentStream cs, PDFont bold, PDFont regular,
                                   ReportPdfData data, float y) throws IOException {
        try {
            byte[] qrBytes = generateQrPng(data.qrVerificationUrl(), 90);
            PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, qrBytes, "qr");
            cs.drawImage(qrImage, MARGIN, Math.max(y - 90, MARGIN), 90, 90);
        } catch (WriterException e) {
            // QR is a verification convenience; never fail the whole report for it.
        }

        cs.beginText();
        cs.setFont(regular, 8);
        cs.newLineAtOffset(MARGIN + 100, y - 30);
        cs.showText("Scan to verify this report");
        cs.endText();

        cs.beginText();
        cs.setFont(regular, 8);
        cs.newLineAtOffset(MARGIN + 100, y - 45);
        cs.showText("Report Generated: " + data.generatedDate());
        cs.endText();

        cs.beginText();
        cs.setFont(regular, 8);
        cs.newLineAtOffset(MARGIN + 100, y - 60);
        cs.showText(schoolName);
        cs.endText();
    }

    private byte[] generateQrPng(String content, int size) throws WriterException, IOException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    private ReportPdfData sanitizeData(ReportPdfData d) {
        return new ReportPdfData(
                sanitizeForWinAnsi(d.studentName()),
                sanitizeForWinAnsi(d.admissionNo()),
                sanitizeForWinAnsi(d.className()),
                sanitizeForWinAnsi(d.section()),
                sanitizeForWinAnsi(d.cycleName()),
                sanitizeForWinAnsi(d.cycleDateRange()),
                d.studentPhotoUrl(),
                d.workingDays(),
                d.presentDays(),
                sanitizeForWinAnsi(d.attendancePct()),
                d.subjects() == null ? null : d.subjects().stream()
                        .map(s -> new SubjectRow(sanitizeForWinAnsi(s.subjectName()), sanitizeForWinAnsi(s.marksOrRating())))
                        .toList(),
                d.disciplineScore(), d.homeworkScore(), d.participationScore(),
                d.punctualityScore(), d.communicationScore(), d.teamworkScore(),
                sanitizeForWinAnsi(d.behaviourOverall()),
                sanitizeForWinAnsi(d.teacherRemarks()),
                sanitizeForWinAnsi(d.principalRemarks()),
                d.overallPerformance(),
                sanitizeForWinAnsi(d.generatedDate()),
                d.qrVerificationUrl()
        );
    }

    // Standard14 fonts (Helvetica) only support WinAnsi/Latin-1 encoding.
    // Names or remarks containing Devanagari or other non-Latin script would
    // throw at render time otherwise. This is a known limitation: any such
    // text is shown as "?" rather than crashing the whole report. A proper
    // fix is to embed a Unicode TTF (e.g. Noto Sans Devanagari) via
    // PDType0Font.load(doc, fontStream) if Hindi-script remarks/names turn
    // out to be common in practice.
    private String sanitizeForWinAnsi(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            sb.append(c <= 0xFF ? c : '?');
        }
        return sb.toString();
    }

    private void setColor(PDPageContentStream cs, int[] rgb, boolean stroking) throws IOException {
        float r = rgb[0] / 255f, g = rgb[1] / 255f, b = rgb[2] / 255f;
        if (stroking) {
            cs.setStrokingColor(r, g, b);
            cs.setNonStrokingColor(r, g, b);
        } else {
            cs.setNonStrokingColor(r, g, b);
        }
    }
}
