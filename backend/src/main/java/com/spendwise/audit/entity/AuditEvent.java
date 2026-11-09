package com.spendwise.audit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", length = 120)
    private String ownerId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private JsonNode details;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() { }

    public AuditEvent(String ownerId, String eventType, String requestId, JsonNode details) {
        this.ownerId = ownerId;
        this.eventType = eventType;
        this.requestId = requestId;
        this.details = details;
    }

    public Long getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getEventType() { return eventType; }
    public String getRequestId() { return requestId; }
    public JsonNode getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
