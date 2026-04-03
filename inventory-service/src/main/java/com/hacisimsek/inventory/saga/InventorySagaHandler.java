package com.hacisimsek.inventory.saga;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventorySagaHandler {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "inventory-service-group")
    public void handleOrderEvents(Object event) {
        OrderCreatedEvent orderCreatedEvent = toEvent(event, OrderCreatedEvent.class);
        if (orderCreatedEvent == null) return;

        log.info("Received OrderCreatedEvent for order: {}", orderCreatedEvent.getOrderId());
        inventoryService.reserveInventory(orderCreatedEvent);
    }

    @KafkaListener(topics = "payment-events", groupId = "inventory-service-group")
    public void handlePaymentEvents(Object event) {
        PaymentFailedEvent paymentFailedEvent = toEvent(event, PaymentFailedEvent.class);
        if (paymentFailedEvent == null) return;

        log.info("Received PaymentFailedEvent for order: {}, cancelling inventory reservation",
                paymentFailedEvent.getOrderId());
        inventoryService.cancelReservation(paymentFailedEvent.getOrderId());
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
