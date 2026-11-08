package com.spendwise.product.repository;

import com.spendwise.product.entity.ShoppingPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingPreferencesRepository extends JpaRepository<ShoppingPreferences, String> {
}
