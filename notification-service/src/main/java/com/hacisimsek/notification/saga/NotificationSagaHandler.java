package com.hacisimsek.notification.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSagaHandler {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "notification-service-group")
    public void handleOrderEvents(Object event) {
        OrderCreatedEvent orderCreatedEvent = toEvent(event, OrderCreatedEvent.class);
        if (orderCreatedEvent == null) return;

        log.info("Received OrderCreatedEvent for order: {}", orderCreatedEvent.getOrderId());
        notificationService.sendOrderCreatedNotification(
                orderCreatedEvent.getOrderId(),
                orderCreatedEvent.getCustomerId()
        );
    }

    @KafkaListener(topics = "payment-events", groupId = "notification-service-group")
    public void handlePaymentEvents(Object event) {
        PaymentProcessedEvent paymentProcessedEvent = toEvent(event, PaymentProcessedEvent.class);
        if (paymentProcessedEvent != null) {
            log.info("Received PaymentProcessedEvent for order: {}", paymentProcessedEvent.getOrderId());
            notificationService.sendPaymentSuccessNotification(
                    paymentProcessedEvent.getOrderId(),
                    UUID.randomUUID()
            );
            return;
        }

        PaymentFailedEvent paymentFailedEvent = toEvent(event, PaymentFailedEvent.class);
        if (paymentFailedEvent != null) {
            log.info("Received PaymentFailedEvent for order: {}", paymentFailedEvent.getOrderId());
            notificationService.sendPaymentFailedNotification(
                    paymentFailedEvent.getOrderId(),
                    UUID.randomUUID()
            );
        }
    }

    @KafkaListener(topics = "shipping-events", groupId = "notification-service-group")
    public void handleShippingEvents(Object event) {
        ShipmentProcessedEvent shipmentProcessedEvent = toEvent(event, ShipmentProcessedEvent.class);
        if (shipmentProcessedEvent == null) return;

        log.info("Received ShipmentProcessedEvent for order: {}", shipmentProcessedEvent.getOrderId());
        notificationService.sendOrderShippedNotification(
                shipmentProcessedEvent.getOrderId(),
                UUID.randomUUID(),
                shipmentProcessedEvent.getTrackingNumber()
        );
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
