package com.computerstore.service;

import com.computerstore.entity.VerificationPurpose;

public interface OtpSender {
    void send(String target, boolean isEmail, String code, VerificationPurpose purpose);
}
