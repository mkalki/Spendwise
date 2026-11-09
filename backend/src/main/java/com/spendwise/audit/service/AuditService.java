package com.spendwise.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.audit.entity.AuditEvent;
import com.spendwise.audit.repository.AuditEventRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String ownerId, String eventType, Map<String, ?> details) {
        record(ownerId, eventType, null, details);
    }

    @Transactional
    public void record(String ownerId, String eventType, String requestId, Map<String, ?> details) {
        JsonNode safeDetails = objectMapper.valueToTree(details == null ? Map.of() : details);
        repository.save(new AuditEvent(ownerId, eventType, requestId, safeDetails));
    }
}
