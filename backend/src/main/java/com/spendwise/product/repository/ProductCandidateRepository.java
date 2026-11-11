package com.spendwise.product.repository;

import com.spendwise.product.entity.ProductCandidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCandidateRepository extends JpaRepository<ProductCandidate, UUID> {

    List<ProductCandidate> findBySearchSessionOwnerIdAndEligibleFalseOrderByRetrievedAtDescIdDesc(
            String ownerId, Pageable pageable);

    @Query("""
            select candidate
            from ProductCandidate candidate
            join fetch candidate.searchSession searchSession
            where candidate.id = :candidateId
              and searchSession.ownerId = :ownerId
            """)
    Optional<ProductCandidate> findOwnedById(
            @Param("candidateId") UUID candidateId,
            @Param("ownerId") String ownerId);

    @Query("""
            select candidate
            from ProductCandidate candidate
            join fetch candidate.searchSession searchSession
            where searchSession.id = :sessionId
              and searchSession.ownerId = :ownerId
              and candidate.eligible = true
            """)
    List<ProductCandidate> findEligibleBySearchSession(
            @Param("sessionId") UUID sessionId,
            @Param("ownerId") String ownerId);
}
