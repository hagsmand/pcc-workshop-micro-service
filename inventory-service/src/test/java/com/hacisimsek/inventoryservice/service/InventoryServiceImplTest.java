package com.hacisimsek.inventoryservice.service;

import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryReservation;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.repository.ReservationRepository;
import com.hacisimsek.inventory.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private UUID orderId;
    private UUID productId;
    private InventoryReservation reservation;
    private InventoryItem inventoryItem;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        productId = UUID.randomUUID();

        // Create a reservation item
        InventoryReservation.ReservationItem reservationItem = InventoryReservation.ReservationItem.builder()
                .productId(productId)
                .quantity(5)
                .build();

        // Create a reservation
        reservation = InventoryReservation.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .correlationId(UUID.randomUUID())
                .items(List.of(reservationItem))
                .status(InventoryReservation.ReservationStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Create an inventory item
        inventoryItem = InventoryItem.builder()
                .id(productId)
                .name("Test Product")
                .description("Test Description")
                .availableQuantity(10)
                .reservedQuantity(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void cancelReservation_ShouldReturnInventoryAndCancelReservation() {
        // Arrange
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findById(productId)).thenReturn(Optional.of(inventoryItem));
        when(inventoryRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);
        when(reservationRepository.save(any(InventoryReservation.class))).thenReturn(reservation);

        // Act
        inventoryService.cancelReservation(orderId);

        // Assert
        verify(reservationRepository, times(1)).findByOrderId(orderId);
        verify(inventoryRepository, times(1)).findById(productId);
        
        // Verify inventory quantities were updated correctly
        verify(inventoryRepository, times(1)).save(argThat(item -> 
            item.getAvailableQuantity() == 15 && // 10 + 5 returned
            item.getReservedQuantity() == 0      // 5 - 5 released
        ));
        
        // Verify reservation status was updated
        verify(reservationRepository, times(1)).save(argThat(res -> 
            res.getStatus() == InventoryReservation.ReservationStatus.CANCELLED
        ));
    }

    @Test
    void cancelReservation_WhenAlreadyCancelled_ShouldNotUpdateInventory() {
        // Arrange
        reservation.setStatus(InventoryReservation.ReservationStatus.CANCELLED);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(reservation));

        // Act
        inventoryService.cancelReservation(orderId);

        // Assert
        verify(reservationRepository, times(1)).findByOrderId(orderId);
        verify(inventoryRepository, never()).findById(any());
        verify(inventoryRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelReservation_WhenReservationNotFound_ShouldThrowException() {
        // Arrange
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            inventoryService.cancelReservation(orderId);
        });

        assertTrue(exception.getMessage().contains("Reservation not found for order"));
        verify(reservationRepository, times(1)).findByOrderId(orderId);
        verify(inventoryRepository, never()).findById(any());
    }

    @Test
    void confirmReservation_ShouldUpdateReservationStatus() {
        // Arrange
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(InventoryReservation.class))).thenReturn(reservation);

        // Act
        inventoryService.confirmReservation(orderId);

        // Assert
        verify(reservationRepository, times(1)).findByOrderId(orderId);
        verify(reservationRepository, times(1)).save(argThat(res ->
            res.getStatus() == InventoryReservation.ReservationStatus.CONFIRMED
        ));
    }

    @Test
    void reserveInventory_WithSufficientStock_ShouldReserveSuccessfully() {
        // Arrange
        OrderItemDto orderItem = new OrderItemDto(productId, 5, BigDecimal.valueOf(100));

        OrderCreatedEvent orderEvent = OrderCreatedEvent.builder()
                .orderId(orderId)
                .correlationId(UUID.randomUUID())
                .items(List.of(orderItem))
                .totalAmount(BigDecimal.valueOf(500))
                .build();

        when(inventoryRepository.findById(productId)).thenReturn(Optional.of(inventoryItem));
        when(inventoryRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);
        when(reservationRepository.save(any(InventoryReservation.class))).thenReturn(reservation);

        // Act
        inventoryService.reserveInventory(orderEvent);

        // Assert
        // findById is called twice: once for checking availability, once for updating
        verify(inventoryRepository, times(2)).findById(productId);
        verify(reservationRepository, times(1)).save(any(InventoryReservation.class));
        
        // Verify inventory was updated
        verify(inventoryRepository, times(1)).save(argThat(item ->
            item.getAvailableQuantity() == 5 && // 10 - 5
            item.getReservedQuantity() == 10    // 5 + 5
        ));

        // Verify success event was sent
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(1)).send(eq("inventory-events"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue() instanceof InventoryReservedEvent);
    }

    @Test
    void reserveInventory_WithInsufficientStock_ShouldSendFailureEvent() {
        // Arrange
        OrderItemDto orderItem = new OrderItemDto(productId, 20, BigDecimal.valueOf(100)); // More than available (10)

        OrderCreatedEvent orderEvent = OrderCreatedEvent.builder()
                .orderId(orderId)
                .correlationId(UUID.randomUUID())
                .items(List.of(orderItem))
                .totalAmount(BigDecimal.valueOf(2000))
                .build();

        when(inventoryRepository.findById(productId)).thenReturn(Optional.of(inventoryItem));

        // Act
        inventoryService.reserveInventory(orderEvent);

        // Assert
        verify(inventoryRepository, times(1)).findById(productId);
        verify(reservationRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any());

        // Verify failure event was sent
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(1)).send(eq("inventory-events"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue() instanceof InventoryReservationFailedEvent);
        
        InventoryReservationFailedEvent failedEvent = (InventoryReservationFailedEvent) eventCaptor.getValue();
        assertTrue(failedEvent.getReason().contains("Insufficient quantity"));
    }

    @Test
    void reserveInventory_WithNonExistentProduct_ShouldSendFailureEvent() {
        // Arrange
        OrderItemDto orderItem = new OrderItemDto(productId, 5, BigDecimal.valueOf(100));

        OrderCreatedEvent orderEvent = OrderCreatedEvent.builder()
                .orderId(orderId)
                .correlationId(UUID.randomUUID())
                .items(List.of(orderItem))
                .totalAmount(BigDecimal.valueOf(500))
                .build();

        when(inventoryRepository.findById(productId)).thenReturn(Optional.empty());

        // Act
        inventoryService.reserveInventory(orderEvent);

        // Assert
        verify(inventoryRepository, times(1)).findById(productId);
        verify(reservationRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any());

        // Verify failure event was sent
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(1)).send(eq("inventory-events"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue() instanceof InventoryReservationFailedEvent);
        
        InventoryReservationFailedEvent failedEvent = (InventoryReservationFailedEvent) eventCaptor.getValue();
        assertTrue(failedEvent.getReason().contains("Product not found"));
    }

    @Test
    void reserveInventory_WithMultipleItems_ShouldReserveAll() {
        // Arrange
        UUID productId2 = UUID.randomUUID();
        InventoryItem inventoryItem2 = InventoryItem.builder()
                .id(productId2)
                .name("Test Product 2")
                .availableQuantity(20)
                .reservedQuantity(0)
                .build();

        OrderItemDto orderItem1 = new OrderItemDto(productId, 5, BigDecimal.valueOf(100));
        OrderItemDto orderItem2 = new OrderItemDto(productId2, 10, BigDecimal.valueOf(50));

        OrderCreatedEvent orderEvent = OrderCreatedEvent.builder()
                .orderId(orderId)
                .correlationId(UUID.randomUUID())
                .items(List.of(orderItem1, orderItem2))
                .totalAmount(BigDecimal.valueOf(1000))
                .build();

        when(inventoryRepository.findById(productId)).thenReturn(Optional.of(inventoryItem));
        when(inventoryRepository.findById(productId2)).thenReturn(Optional.of(inventoryItem2));
        when(inventoryRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);
        when(reservationRepository.save(any(InventoryReservation.class))).thenReturn(reservation);

        // Act
        inventoryService.reserveInventory(orderEvent);

        // Assert
        // Each product's findById is called twice: once for checking, once for updating
        verify(inventoryRepository, times(2)).findById(productId);
        verify(inventoryRepository, times(2)).findById(productId2);
        verify(inventoryRepository, times(2)).save(any(InventoryItem.class));
        verify(reservationRepository, times(1)).save(any(InventoryReservation.class));

        // Verify success event was sent
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(1)).send(eq("inventory-events"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue() instanceof InventoryReservedEvent);
    }

    @Test
    void confirmReservation_WhenReservationNotFound_ShouldThrowException() {
        // Arrange
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            inventoryService.confirmReservation(orderId);
        });

        assertTrue(exception.getMessage().contains("Reservation not found for order"));
        verify(reservationRepository, times(1)).findByOrderId(orderId);
    }
}

// Made with Bob
