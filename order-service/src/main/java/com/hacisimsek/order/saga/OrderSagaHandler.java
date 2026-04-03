package com.hacisimsek.order.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaHandler {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInventoryEvents(Object event) {
        InventoryReservedEvent reservedEvent = toEvent(event, InventoryReservedEvent.class);
        if (reservedEvent != null) {
            orderService.updateOrderStatus(reservedEvent.getOrderId(), Order.OrderStatus.INVENTORY_RESERVED);
            log.info("Inventory reserved for order: {}", reservedEvent.getOrderId());
            return;
        }

        InventoryReservationFailedEvent failedEvent = toEvent(event, InventoryReservationFailedEvent.class);
        if (failedEvent != null) {
            orderService.updateOrderStatus(failedEvent.getOrderId(), Order.OrderStatus.CANCELLED);
            log.error("Inventory reservation failed for order: {}, reason: {}",
                    failedEvent.getOrderId(), failedEvent.getReason());
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvents(Object event) {
        PaymentProcessedEvent processedEvent = toEvent(event, PaymentProcessedEvent.class);
        if (processedEvent != null) {
            orderService.updateOrderStatus(processedEvent.getOrderId(), Order.OrderStatus.PAYMENT_COMPLETED);
            log.info("Payment processed for order: {}", processedEvent.getOrderId());
            return;
        }

        PaymentFailedEvent failedEvent = toEvent(event, PaymentFailedEvent.class);
        if (failedEvent != null) {
            orderService.updateOrderStatus(failedEvent.getOrderId(), Order.OrderStatus.FAILED);
            log.error("Payment failed for order: {}, reason: {}",
                    failedEvent.getOrderId(), failedEvent.getReason());
        }
    }

    @KafkaListener(topics = "shipping-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleShippingEvents(Object event) {
        ShipmentProcessedEvent processedEvent = toEvent(event, ShipmentProcessedEvent.class);
        if (processedEvent != null) {
            orderService.updateOrderStatus(processedEvent.getOrderId(), Order.OrderStatus.SHIPPED);
            log.info("Order shipped: {}, tracking number: {}",
                    processedEvent.getOrderId(), processedEvent.getTrackingNumber());
            return;
        }

        ShipmentFailedEvent failedEvent = toEvent(event, ShipmentFailedEvent.class);
        if (failedEvent != null) {
            orderService.updateOrderStatus(failedEvent.getOrderId(), Order.OrderStatus.FAILED);
            log.error("Shipping failed for order: {}, reason: {}",
                    failedEvent.getOrderId(), failedEvent.getReason());
        }
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
