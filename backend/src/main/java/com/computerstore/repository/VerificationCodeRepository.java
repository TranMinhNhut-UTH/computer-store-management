package com.computerstore.repository;

import com.computerstore.entity.VerificationCode;
import com.computerstore.entity.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, VerificationPurpose purpose);

    Optional<VerificationCode> findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            Long userId, VerificationPurpose purpose);

    List<VerificationCode> findByUserIdAndPurposeAndUsedAtIsNull(Long userId, VerificationPurpose purpose);
}
