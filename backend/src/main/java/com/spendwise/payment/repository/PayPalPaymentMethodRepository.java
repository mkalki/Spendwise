package com.spendwise.payment.repository;

import com.spendwise.payment.entity.PayPalPaymentMethod;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayPalPaymentMethodRepository extends JpaRepository<PayPalPaymentMethod, UUID> {
    List<PayPalPaymentMethod> findAllByOwnerIdAndStatusOrderByCreatedAtDesc(String ownerId, String status);
    Optional<PayPalPaymentMethod> findByOwnerIdAndIdAndStatus(String ownerId, UUID id, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select method from PayPalPaymentMethod method where method.ownerId = :ownerId and method.id = :id")
    Optional<PayPalPaymentMethod> findOwnedForUpdate(@Param("ownerId") String ownerId, @Param("id") UUID id);
}
