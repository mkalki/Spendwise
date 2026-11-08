package com.spendwise.payment.repository;

import com.spendwise.payment.entity.PaymentTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByOwnerIdAndId(String ownerId, Long id);

    Optional<PaymentTransaction> findByOwnerIdAndPaypalOrderId(String ownerId, String paypalOrderId);

    Optional<PaymentTransaction> findByPaypalOrderId(String paypalOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tx from PaymentTransaction tx where tx.ownerId = :ownerId and tx.id = :id")
    Optional<PaymentTransaction> findByOwnerIdAndIdForUpdate(
            @Param("ownerId") String ownerId,
            @Param("id") Long id);

    List<PaymentTransaction> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<PaymentTransaction> findByOwnerIdAndIdempotencyKey(String ownerId, String idempotencyKey);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentTransaction tx
            set tx.status = 'EXPIRED', tx.reservationExpiresAt = null
            where tx.ownerId = :ownerId
              and tx.status in ('CREATED', 'APPROVED')
              and tx.reservationExpiresAt <= :now
            """)
    int expireReservations(@Param("ownerId") String ownerId, @Param("now") Instant now);

    @Query("""
            select coalesce(sum(tx.amount), 0)
            from PaymentTransaction tx
            where tx.ownerId = :ownerId
              and tx.status = 'COMPLETED'
              and tx.completedAt >= :start
              and tx.completedAt < :end
            """)
    BigDecimal completedSpend(
            @Param("ownerId") String ownerId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            select count(tx)
            from PaymentTransaction tx
            where tx.ownerId = :ownerId
              and tx.status = 'COMPLETED'
              and tx.completedAt >= :start
              and tx.completedAt < :end
            """)
    long completedOrderCount(
            @Param("ownerId") String ownerId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            select coalesce(sum(tx.amount), 0)
            from PaymentTransaction tx
            where tx.ownerId = :ownerId
              and (
                tx.status in ('REQUIRES_RECONCILIATION', 'CAPTURING')
                or (
                  tx.status in ('CREATED', 'APPROVED')
                  and tx.reservationExpiresAt > :now
                )
              )
            """)
    BigDecimal activeReservationAmount(@Param("ownerId") String ownerId, @Param("now") Instant now);

    @Query("""
            select count(tx)
            from PaymentTransaction tx
            where tx.ownerId = :ownerId
              and (
                tx.status in ('REQUIRES_RECONCILIATION', 'CAPTURING')
                or (
                  tx.status in ('CREATED', 'APPROVED')
                  and tx.reservationExpiresAt > :now
                )
              )
            """)
    long activeReservationCount(@Param("ownerId") String ownerId, @Param("now") Instant now);
}
