package com.ritsync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    
    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;
    
    @Value("${spring.mail.username:noreply@ritsync.com}")
    private String fromEmail;

    @Async
    public void sendDeviceVerificationEmail(String toEmail, String userName, String deviceName, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Verify Your New Device - RIT-Sync");
            
            String verificationLink = baseUrl + "/verify-device?token=" + token;
            
            String htmlContent = String.format("""
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #4F46E5;">New Device Verification</h2>
                    <p>Hi %s,</p>
                    <p>A new device is trying to access your RIT-Sync account:</p>
                    <div style="background: #F3F4F6; padding: 15px; border-radius: 8px; margin: 20px 0;">
                        <p style="margin: 5px 0;"><strong>Device:</strong> %s</p>
                    </div>
                    <p>If this was you, click the button below to verify this device:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; display: inline-block;">
                            Verify Device
                        </a>
                    </div>
                    <p style="color: #6B7280; font-size: 12px;">
                        This link expires in 24 hours. If you didn't request this, you can safely ignore this email.
                    </p>
                </div>
                """, userName, deviceName, verificationLink);
            
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Device verification email sent to {}", toEmail);
            
        } catch (MessagingException e) {
            log.error("Failed to send device verification email to {}: {}", toEmail, e.getMessage());
        }
    }
}
