package com.spendwise.payment.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.checkout.service.CheckoutService;
import com.spendwise.payment.service.PayPalWebhookService;
import org.junit.jupiter.api.Test;

class PayPalWebhookServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CheckoutService checkoutService = mock(CheckoutService.class);
    private final PayPalWebhookService webhookService = new PayPalWebhookService(checkoutService);

    @Test
    void acknowledgesUnsupportedEventsEvenWhenTheyHaveNoResource() throws Exception {
        var event = objectMapper.readTree("""
                {"id":"event-1","event_type":"PAYMENT.PAYOUTSBATCH.SUCCESS"}
                """);

        assertThatCode(() -> webhookService.receive(event)).doesNotThrowAnyException();
        verifyNoInteractions(checkoutService);
    }

    @Test
    void reconcilesCompletedCheckoutOrder() throws Exception {
        var event = objectMapper.readTree("""
                {"id":"event-2","event_type":"CHECKOUT.ORDER.COMPLETED","resource":{"id":"order-1"}}
                """);

        webhookService.receive(event);

        verify(checkoutService).reconcilePaypalOrder("order-1");
    }

    @Test
    void reconcilesRefundedCaptureAgainstPayPalCaptureStatus() throws Exception {
        var event = objectMapper.readTree("""
                {"id":"event-3","event_type":"PAYMENT.CAPTURE.REFUNDED","resource":{"id":"capture-1"}}
                """);

        webhookService.receive(event);

        verify(checkoutService).reconcilePaypalCapture("capture-1", false);
    }
}
