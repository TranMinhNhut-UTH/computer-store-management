package com.computerstore.service;

import com.computerstore.entity.VerificationPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bản tạm cho giai đoạn phát triển: chỉ in mã OTP ra console.
 * Khi triển khai thật: viết lớp implement OtpSender gửi email (JavaMailSender/SendGrid)
 * và SMS (eSMS, SpeedSMS, Twilio...) rồi thay bean này.
 */
@Slf4j
@Component
public class ConsoleOtpSender implements OtpSender {

    @Override
    public void send(String target, boolean isEmail, String code, VerificationPurpose purpose) {
        log.warn("[DEV-OTP] {} -> {} | mã {} | mục đích {}", isEmail ? "EMAIL" : "SMS", target, code, purpose);
    }
}
