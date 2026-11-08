package com.spendwise.product.repository;

import com.spendwise.product.entity.ProductCandidate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCandidateRepository extends JpaRepository<ProductCandidate, UUID> {

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
}
