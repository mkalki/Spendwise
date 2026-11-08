package com.spendwise.product.repository;

import com.spendwise.product.entity.ShoppingSearchSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingSearchSessionRepository extends JpaRepository<ShoppingSearchSession, UUID> {

    Optional<ShoppingSearchSession> findByIdAndOwnerId(UUID id, String ownerId);
}
