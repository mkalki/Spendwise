package com.spendwise.policy.repository;

import com.spendwise.policy.entity.SpendingPolicy;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpendingPolicyRepository extends JpaRepository<SpendingPolicy, Long> {

    Optional<SpendingPolicy> findByOwnerId(String ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from SpendingPolicy policy where policy.ownerId = :ownerId")
    Optional<SpendingPolicy> findByOwnerIdForUpdate(@Param("ownerId") String ownerId);
}
