package com.hacisimsek.shipping.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShippingSagaHandler {

    private final ShippingService shippingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "shipping-service-group")
    public void handlePaymentEvents(Object event) {
        PaymentProcessedEvent paymentProcessedEvent = toEvent(event, PaymentProcessedEvent.class);
        if (paymentProcessedEvent == null) return;

        log.info("Received PaymentProcessedEvent for order: {}, processing shipment",
                paymentProcessedEvent.getOrderId());
        shippingService.processShipping(paymentProcessedEvent);
    }

    private <T> T toEvent(Object event, Class<T> targetType) {
        Object payload = unwrapPayload(event);
        if (payload == null) {
            return null;
        }

        if (targetType.isInstance(payload)) {
            return targetType.cast(payload);
        }

        try {
            if (payload instanceof String json) {
                return objectMapper.readValue(json, targetType);
            }
            if (payload instanceof byte[] bytes) {
                return objectMapper.readValue(bytes, targetType);
            }
            if (payload instanceof java.util.Map<?, ?>) {
                return objectMapper.convertValue(payload, targetType);
            }
            log.warn("Ignoring event payload type {} for target {}",
                    payload.getClass().getName(), targetType.getSimpleName());
        } catch (Exception ex) {
            log.warn("Unable to convert event payload type {} to {}",
                    payload.getClass().getName(), targetType.getSimpleName());
        }
        return null;
    }

    private Object unwrapPayload(Object event) {
        if (event instanceof ConsumerRecord<?, ?> record) {
            return record.value();
        }
        if (event instanceof Message<?> message) {
            return message.getPayload();
        }
        if (event == null) {
            return null;
        }
        return event;
    }
}
