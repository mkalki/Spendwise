package com.spendwise.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.audit.entity.AuditEvent;
import com.spendwise.audit.repository.AuditEventRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AuditServiceTest {
    private final AuditEventRepository repository = Mockito.mock(AuditEventRepository.class);
    private final AuditService service = new AuditService(repository, new ObjectMapper());

    @Test
    void storesStructuredBusinessDetailsAndCorrelationId() {
        service.record("owner-1", "PAYMENT_CAPTURED", "request-1", Map.of("checkoutId", 12, "currency", "USD"));

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().getOwnerId()).isEqualTo("owner-1");
        assertThat(event.getValue().getEventType()).isEqualTo("PAYMENT_CAPTURED");
        assertThat(event.getValue().getRequestId()).isEqualTo("request-1");
        assertThat(event.getValue().getDetails().path("checkoutId").asInt()).isEqualTo(12);
        assertThat(event.getValue().getDetails().path("currency").asText()).isEqualTo("USD");
    }
}
