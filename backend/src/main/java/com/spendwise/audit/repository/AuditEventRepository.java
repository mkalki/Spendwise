package com.spendwise.audit.repository;

import com.spendwise.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
