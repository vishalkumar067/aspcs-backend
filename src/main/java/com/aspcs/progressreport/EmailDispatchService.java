package com.aspcs.progressreport;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

// Result of attempting to send. Always returned, never throws — the caller
// (ProgressReportService) logs this into communication_logs regardless of
// success or failure, so a failed send is recorded, not lost.
record EmailResult(boolean success, String providerRef, String errorMessage) {}

@Slf4j
@Service
@RequiredArgsConstructor
class EmailDispatchService {

    private final JavaMailSender mailSender;

    @Value("${app.school.name:Acharya Shree Sudarshan Patna Central School}")
    private String schoolName;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public EmailResult sendProgressReport(String toEmail, String studentName, String className,
                                           String section, String cycleName, byte[] pdfBytes,
                                           String pdfFileName) {
        if (toEmail == null || toEmail.isBlank()) {
            return new EmailResult(false, null, "No parent email on file for this student");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress, schoolName);
            }
            helper.setSubject("ASPCS Progress Report | " + studentName + " | " + className +
                    (section != null ? section : "") + " | " + cycleName);

            String body = "Dear Parent,\n\n" +
                    "Please find attached the latest Progress Report of your ward.\n\n" +
                    "The report includes attendance, academic performance, behaviour assessment, " +
                    "and teacher observations for the current reporting cycle.\n\n" +
                    "Regards,\n" + schoolName;
            helper.setText(body, false);

            helper.addAttachment(pdfFileName, new org.springframework.core.io.ByteArrayResource(pdfBytes), "application/pdf");

            mailSender.send(message);

            String messageId = message.getMessageID();
            log.info("Email sent to {} for student {} (cycle {})", toEmail, studentName, cycleName);
            return new EmailResult(true, messageId, null);

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send progress report email to {}: {}", toEmail, e.getMessage());
            return new EmailResult(false, null, e.getMessage());
        } catch (org.springframework.mail.MailException e) {
            // Covers SMTP auth failures, connection refused, etc. — anything
            // Spring's mail sender throws once handed off to the transport.
            log.error("SMTP send failure to {}: {}", toEmail, e.getMessage());
            return new EmailResult(false, null, "SMTP delivery failed: " + e.getMessage());
        }
    }
}
