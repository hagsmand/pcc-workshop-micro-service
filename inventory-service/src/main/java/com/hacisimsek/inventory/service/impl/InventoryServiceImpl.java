package com.hacisimsek.inventory.service.impl;

import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryReservation;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.repository.ReservationRepository;
import com.hacisimsek.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ConcurrentHashMap<UUID, ReentrantLock> productLocks = new ConcurrentHashMap<>();

    /**
     * Acquires locks for all products in sorted order to prevent deadlocks
     * @param productIds List of product IDs to lock
     * @return List of acquired locks
     */
    private List<ReentrantLock> acquireLocksInOrder(List<UUID> productIds) {
        // Sort product IDs to ensure consistent lock ordering (prevents deadlocks)
        List<UUID> sortedIds = productIds.stream()
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        
        List<ReentrantLock> acquiredLocks = new ArrayList<>();
        
        try {
            for (UUID productId : sortedIds) {
                ReentrantLock lock = productLocks.computeIfAbsent(productId, k -> new ReentrantLock());
                boolean acquired = lock.tryLock(10, TimeUnit.SECONDS);
                
                if (!acquired) {
                    // Release all previously acquired locks
                    releaseAllLocks(acquiredLocks);
                    throw new RuntimeException("Failed to acquire lock for product: " + productId);
                }
                
                acquiredLocks.add(lock);
                log.debug("Acquired lock for product: {}", productId);
            }
            return acquiredLocks;
        } catch (InterruptedException e) {
            releaseAllLocks(acquiredLocks);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition interrupted", e);
        }
    }

    /**
     * Releases all locks in reverse order
     * @param locks List of locks to release
     */
    private void releaseAllLocks(List<ReentrantLock> locks) {
        // Release in reverse order
        for (int i = locks.size() - 1; i >= 0; i--) {
            ReentrantLock lock = locks.get(i);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock");
            }
        }
    }

    @Override
    public void reserveInventory(OrderCreatedEvent orderCreatedEvent) {
        log.info("Processing inventory reservation for order: {}", orderCreatedEvent.getOrderId());
        
        // Extract all product IDs from the order
        List<UUID> productIds = orderCreatedEvent.getItems().stream()
                .map(item -> item.getProductId())
                .collect(Collectors.toList());
        
        // Acquire locks for all products in sorted order
        List<ReentrantLock> locks = acquireLocksInOrder(productIds);
        
        try {
            List<InventoryReservation.ReservationItem> reservationItems = new ArrayList<>();
            boolean allItemsAvailable = true;
            StringBuilder insufficientItemsMessage = new StringBuilder();

            // Check if all items are available in sufficient quantity
            for (var orderItem : orderCreatedEvent.getItems()) {
                InventoryItem item = inventoryRepository.findById(orderItem.getProductId())
                        .orElse(null);

                if (item == null) {
                    allItemsAvailable = false;
                    insufficientItemsMessage.append("Product not found: ")
                            .append(orderItem.getProductId()).append("; ");
                    continue;
                }

                if (item.getAvailableQuantity() < orderItem.getQuantity()) {
                    allItemsAvailable = false;
                    insufficientItemsMessage.append("Insufficient quantity for product: ")
                            .append(orderItem.getProductId())
                            .append(", requested: ").append(orderItem.getQuantity())
                            .append(", available: ").append(item.getAvailableQuantity())
                            .append("; ");
                    continue;
                }

                // Add to reservation items
                reservationItems.add(InventoryReservation.ReservationItem.builder()
                        .productId(orderItem.getProductId())
                        .quantity(orderItem.getQuantity())
                        .build());
            }

            if (!allItemsAvailable) {
                // Send failure event
                InventoryReservationFailedEvent failedEvent = new InventoryReservationFailedEvent(
                        orderCreatedEvent.getCorrelationId(),
                        orderCreatedEvent.getOrderId(),
                        insufficientItemsMessage.toString()
                );

                kafkaTemplate.send("inventory-events", failedEvent);
                log.error("Inventory reservation failed: {}", insufficientItemsMessage);
                throw new RuntimeException("Insufficient inventory: " + insufficientItemsMessage.toString());
            }

            // Create reservation
            InventoryReservation reservation = InventoryReservation.builder()
                    .id(UUID.randomUUID())
                    .orderId(orderCreatedEvent.getOrderId())
                    .correlationId(orderCreatedEvent.getCorrelationId())
                    .items(reservationItems)
                    .status(InventoryReservation.ReservationStatus.CONFIRMED)
                    .createdAt(LocalDateTime.parse("2025-05-24T13:39:46"))
                    .updatedAt(LocalDateTime.parse("2025-05-24T13:39:46"))
                    .build();

            reservationRepository.save(reservation);

            // Update inventory quantities
            for (var reservationItem : reservationItems) {
                InventoryItem item = inventoryRepository.findById(reservationItem.getProductId()).orElseThrow();
                item.setAvailableQuantity(item.getAvailableQuantity() - reservationItem.getQuantity());
                item.setReservedQuantity(item.getReservedQuantity() + reservationItem.getQuantity());
                inventoryRepository.save(item);
            }

            // Send success event
            InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                    orderCreatedEvent.getCorrelationId(),
                    orderCreatedEvent.getOrderId()
            );

            kafkaTemplate.send("inventory-events", reservedEvent);
            log.info("Inventory successfully reserved for order: {}", orderCreatedEvent.getOrderId());
            
        } finally {
            // Always release locks, even if an exception occurs
            releaseAllLocks(locks);
            log.debug("Released all locks for order: {}", orderCreatedEvent.getOrderId());
        }
    }

    @Override
    @Transactional
    public void confirmReservation(UUID orderId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Reservation not found for order: " + orderId));

        reservation.setStatus(InventoryReservation.ReservationStatus.CONFIRMED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        log.info("Reservation confirmed for order: {}", orderId);
    }

    @Override
    @Transactional
    public void cancelReservation(UUID orderId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Reservation not found for order: " + orderId));

        if (reservation.getStatus() == InventoryReservation.ReservationStatus.CANCELLED) {
            log.info("Reservation for order {} already cancelled", orderId);
            return;
        }

        // Return quantities back to available inventory
        for (var reservationItem : reservation.getItems()) {
            InventoryItem item = inventoryRepository.findById(reservationItem.getProductId()).orElseThrow();
            item.setAvailableQuantity(item.getAvailableQuantity() + reservationItem.getQuantity());
            item.setReservedQuantity(item.getReservedQuantity() - reservationItem.getQuantity());
            inventoryRepository.save(item);
        }

        reservation.setStatus(InventoryReservation.ReservationStatus.CANCELLED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        log.info("Reservation cancelled and inventory released for order: {}", orderId);
    }
}