package com.spendwise.auth.repository;

import com.spendwise.auth.entity.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailVerificationToken token where token.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
