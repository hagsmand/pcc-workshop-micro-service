package com.hacisimsek.inventoryservice.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.inventory.saga.InventorySagaHandler;
import com.hacisimsek.inventory.service.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventorySagaHandlerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InventorySagaHandler inventorySagaHandler;

    private UUID orderId;
    private UUID correlationId;
    private OrderCreatedEvent orderCreatedEvent;
    private PaymentFailedEvent paymentFailedEvent;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        correlationId = UUID.randomUUID();

        OrderItemDto orderItem = new OrderItemDto(UUID.randomUUID(), 5, BigDecimal.valueOf(100));

        orderCreatedEvent = OrderCreatedEvent.builder()
                .orderId(orderId)
                .correlationId(correlationId)
                .items(List.of(orderItem))
                .totalAmount(BigDecimal.valueOf(500))
                .build();

        paymentFailedEvent = PaymentFailedEvent.builder()
                .correlationId(correlationId)
                .orderId(orderId)
                .reason("Insufficient funds")
                .build();
    }

    @Test
    void handleOrderEvents_WithOrderCreatedEvent_ShouldReserveInventory() {
        // Arrange
        doNothing().when(inventoryService).reserveInventory(any(OrderCreatedEvent.class));

        // Act
        inventorySagaHandler.handleOrderEvents(orderCreatedEvent);

        // Assert
        verify(inventoryService, times(1)).reserveInventory(orderCreatedEvent);
    }

    @Test
    void handleOrderEvents_WithConsumerRecord_ShouldReserveInventory() {
        // Arrange
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "order-events", 0, 0L, "key", orderCreatedEvent
        );
        doNothing().when(inventoryService).reserveInventory(any(OrderCreatedEvent.class));

        // Act
        inventorySagaHandler.handleOrderEvents(record);

        // Assert
        verify(inventoryService, times(1)).reserveInventory(orderCreatedEvent);
    }

    @Test
    void handleOrderEvents_WithMessage_ShouldReserveInventory() {
        // Arrange
        Message<OrderCreatedEvent> message = new GenericMessage<>(orderCreatedEvent);
        doNothing().when(inventoryService).reserveInventory(any(OrderCreatedEvent.class));

        // Act
        inventorySagaHandler.handleOrderEvents(message);

        // Assert
        verify(inventoryService, times(1)).reserveInventory(orderCreatedEvent);
    }

    @Test
    void handleOrderEvents_WithJsonString_ShouldReserveInventory() throws Exception {
        // Arrange
        String jsonEvent = "{\"orderId\":\"" + orderId + "\",\"correlationId\":\"" + correlationId + "\"}";
        when(objectMapper.readValue(eq(jsonEvent), eq(OrderCreatedEvent.class)))
                .thenReturn(orderCreatedEvent);
        doNothing().when(inventoryService).reserveInventory(any(OrderCreatedEvent.class));

        // Act
        inventorySagaHandler.handleOrderEvents(jsonEvent);

        // Assert
        verify(objectMapper, times(1)).readValue(eq(jsonEvent), eq(OrderCreatedEvent.class));
        verify(inventoryService, times(1)).reserveInventory(orderCreatedEvent);
    }

    @Test
    void handleOrderEvents_WithMap_ShouldReserveInventory() throws Exception {
        // Arrange
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("orderId", orderId.toString());
        eventMap.put("correlationId", correlationId.toString());
        
        when(objectMapper.convertValue(eq(eventMap), eq(OrderCreatedEvent.class)))
                .thenReturn(orderCreatedEvent);
        doNothing().when(inventoryService).reserveInventory(any(OrderCreatedEvent.class));

        // Act
        inventorySagaHandler.handleOrderEvents(eventMap);

        // Assert
        verify(objectMapper, times(1)).convertValue(eq(eventMap), eq(OrderCreatedEvent.class));
        verify(inventoryService, times(1)).reserveInventory(orderCreatedEvent);
    }

    @Test
    void handleOrderEvents_WithInvalidEvent_ShouldNotReserveInventory() throws Exception {
        // Arrange
        String invalidJson = "invalid json";
        when(objectMapper.readValue(eq(invalidJson), eq(OrderCreatedEvent.class)))
                .thenThrow(new RuntimeException("Invalid JSON"));

        // Act
        inventorySagaHandler.handleOrderEvents(invalidJson);

        // Assert
        verify(inventoryService, never()).reserveInventory(any());
    }

    @Test
    void handleOrderEvents_WithNullEvent_ShouldNotReserveInventory() {
        // Act
        inventorySagaHandler.handleOrderEvents(null);

        // Assert
        verify(inventoryService, never()).reserveInventory(any());
    }

    @Test
    void handlePaymentEvents_WithPaymentFailedEvent_ShouldCancelReservation() {
        // Arrange
        doNothing().when(inventoryService).cancelReservation(orderId);

        // Act
        inventorySagaHandler.handlePaymentEvents(paymentFailedEvent);

        // Assert
        verify(inventoryService, times(1)).cancelReservation(orderId);
    }

    @Test
    void handlePaymentEvents_WithConsumerRecord_ShouldCancelReservation() {
        // Arrange
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "payment-events", 0, 0L, "key", paymentFailedEvent
        );
        doNothing().when(inventoryService).cancelReservation(orderId);

        // Act
        inventorySagaHandler.handlePaymentEvents(record);

        // Assert
        verify(inventoryService, times(1)).cancelReservation(orderId);
    }

    @Test
    void handlePaymentEvents_WithMessage_ShouldCancelReservation() {
        // Arrange
        Message<PaymentFailedEvent> message = new GenericMessage<>(paymentFailedEvent);
        doNothing().when(inventoryService).cancelReservation(orderId);

        // Act
        inventorySagaHandler.handlePaymentEvents(message);

        // Assert
        verify(inventoryService, times(1)).cancelReservation(orderId);
    }

    @Test
    void handlePaymentEvents_WithJsonString_ShouldCancelReservation() throws Exception {
        // Arrange
        String jsonEvent = "{\"orderId\":\"" + orderId + "\",\"reason\":\"Insufficient funds\"}";
        when(objectMapper.readValue(eq(jsonEvent), eq(PaymentFailedEvent.class)))
                .thenReturn(paymentFailedEvent);
        doNothing().when(inventoryService).cancelReservation(orderId);

        // Act
        inventorySagaHandler.handlePaymentEvents(jsonEvent);

        // Assert
        verify(objectMapper, times(1)).readValue(eq(jsonEvent), eq(PaymentFailedEvent.class));
        verify(inventoryService, times(1)).cancelReservation(orderId);
    }

    @Test
    void handlePaymentEvents_WithInvalidEvent_ShouldNotCancelReservation() throws Exception {
        // Arrange
        String invalidJson = "invalid json";
        when(objectMapper.readValue(eq(invalidJson), eq(PaymentFailedEvent.class)))
                .thenThrow(new RuntimeException("Invalid JSON"));

        // Act
        inventorySagaHandler.handlePaymentEvents(invalidJson);

        // Assert
        verify(inventoryService, never()).cancelReservation(any());
    }

    @Test
    void handlePaymentEvents_WithNullEvent_ShouldNotCancelReservation() {
        // Act
        inventorySagaHandler.handlePaymentEvents(null);

        // Assert
        verify(inventoryService, never()).cancelReservation(any());
    }

    @Test
    void handlePaymentEvents_WithByteArray_ShouldCancelReservation() throws Exception {
        // Arrange
        byte[] jsonBytes = "{\"orderId\":\"test\"}".getBytes();
        when(objectMapper.readValue(eq(jsonBytes), eq(PaymentFailedEvent.class)))
                .thenReturn(paymentFailedEvent);
        doNothing().when(inventoryService).cancelReservation(orderId);

        // Act
        inventorySagaHandler.handlePaymentEvents(jsonBytes);

        // Assert
        verify(objectMapper, times(1)).readValue(eq(jsonBytes), eq(PaymentFailedEvent.class));
        verify(inventoryService, times(1)).cancelReservation(orderId);
    }
}

// Made with Bob