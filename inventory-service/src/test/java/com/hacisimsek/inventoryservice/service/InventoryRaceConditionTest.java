package com.hacisimsek.inventoryservice.service;

import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.inventory.InventoryServiceApplication;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.repository.ReservationRepository;
import com.hacisimsek.inventory.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Race Condition Test Suite for Inventory Service
 * 
 * This test suite demonstrates and verifies the fix for race condition vulnerabilities
 * in the reserveInventory() method. The tests exploit the Check-Then-Act pattern
 * where concurrent threads can read the same inventory state before any updates occur.
 * 
 * Test Strategy:
 * - Use ExecutorService to simulate concurrent order processing
 * - Use CountDownLatch to synchronize thread execution for maximum race condition exposure
 * - Verify final inventory state to detect overselling
 * 
 * Expected Behavior:
 * - BEFORE FIX: Tests should fail, showing negative inventory (overselling)
 * - AFTER FIX: Tests should pass, with proper inventory management
 */
@SpringBootTest(
        classes = InventoryServiceApplication.class,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "eureka.client.enabled=false"
        }
)
public class InventoryRaceConditionTest {

    @Autowired
    private InventoryServiceImpl inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final UUID PRODUCT_A_ID = UUID.randomUUID();
    private static final UUID PRODUCT_B_ID = UUID.randomUUID();
    private static final UUID PRODUCT_C_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Clean up before each test
        inventoryRepository.deleteAll();
        reservationRepository.deleteAll();
    }

    /**
     * Test Case 1: Basic Concurrent Reservation (2 Threads)
     * 
     * Objective: Demonstrate basic race condition with 2 concurrent orders
     * Setup: Product with 10 units available
     * Action: 2 threads simultaneously reserve 8 units each
     * 
     * Expected (Vulnerable): Both succeed, resulting in -6 available ❌
     * Expected (Fixed): One succeeds, one fails ✅
     */
    @Test
    @DisplayName("Test Case 1: Basic Concurrent Reservation - 2 threads competing for limited inventory")
    void testBasicConcurrentReservation() throws InterruptedException {
        // Setup: Create product with 10 units
        InventoryItem product = InventoryItem.builder()
                .id(PRODUCT_A_ID)
                .name("Product A")
                .description("Test Product A")
                .availableQuantity(10)
                .reservedQuantity(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        inventoryRepository.save(product);

        // Action: 2 threads try to reserve 8 units each (total 16 units requested, only 10 available)
        int numberOfThreads = 2;
        int quantityPerOrder = 8;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    // Create order event
                    OrderCreatedEvent event = createOrderEvent(
                            UUID.randomUUID(),
                            List.of(new OrderItemDto(PRODUCT_A_ID, quantityPerOrder, BigDecimal.valueOf(10.00)))
                    );
                    
                    // Attempt reservation
                    inventoryService.reserveInventory(event);
                    successCount.incrementAndGet();
                    System.out.println("Thread " + threadId + " - Reservation SUCCEEDED");
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("Thread " + threadId + " - Reservation FAILED: " + e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        
        assertTrue(completed, "All threads should complete within timeout");

        // Verify results
        InventoryItem finalProduct = inventoryRepository.findById(PRODUCT_A_ID).orElseThrow();
        
        System.out.println("\n=== Test Case 1 Results ===");
        System.out.println("Initial Available: 10 units");
        System.out.println("Requests: 2 threads × 8 units = 16 units total");
        System.out.println("Success Count: " + successCount.get());
        System.out.println("Failure Count: " + failureCount.get());
        System.out.println("Final Available: " + finalProduct.getAvailableQuantity());
        System.out.println("Final Reserved: " + finalProduct.getReservedQuantity());
        
        // Assertions
        assertTrue(finalProduct.getAvailableQuantity() >= 0, 
                "Available quantity should never be negative (overselling detected!)");
        assertEquals(1, successCount.get(), 
                "Only 1 reservation should succeed (10 units available, 8 units per order)");
        assertEquals(1, failureCount.get(), 
                "1 reservation should fail due to insufficient inventory");
        assertEquals(2, finalProduct.getAvailableQuantity(), 
                "Should have 2 units remaining (10 - 8)");
        assertEquals(8, finalProduct.getReservedQuantity(), 
                "Should have 8 units reserved");
    }

    /**
     * Test Case 2: High Concurrency (10 Threads)
     * 
     * Objective: Stress test with multiple concurrent requests
     * Setup: Product with 50 units available
     * Action: 10 threads simultaneously reserve 10 units each
     * 
     * Expected (Vulnerable): All succeed, resulting in -50 available ❌
     * Expected (Fixed): First 5 succeed, last 5 fail ✅
     */
    @Test
    @DisplayName("Test Case 2: High Concurrency - 10 threads stress test")
    void testHighConcurrency() throws InterruptedException {
        // Setup: Create product with 50 units
        InventoryItem product = InventoryItem.builder()
                .id(PRODUCT_B_ID)
                .name("Product B")
                .description("Test Product B")
                .availableQuantity(50)
                .reservedQuantity(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        inventoryRepository.save(product);

        // Action: 10 threads try to reserve 10 units each (total 100 units requested, only 50 available)
        int numberOfThreads = 10;
        int quantityPerOrder = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    OrderCreatedEvent event = createOrderEvent(
                            UUID.randomUUID(),
                            List.of(new OrderItemDto(PRODUCT_B_ID, quantityPerOrder, BigDecimal.valueOf(10.00)))
                    );
                    
                    inventoryService.reserveInventory(event);
                    successCount.incrementAndGet();
                    System.out.println("Thread " + threadId + " - Reservation SUCCEEDED");
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("Thread " + threadId + " - Reservation FAILED");
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        
        assertTrue(completed, "All threads should complete within timeout");

        // Verify results
        InventoryItem finalProduct = inventoryRepository.findById(PRODUCT_B_ID).orElseThrow();
        
        System.out.println("\n=== Test Case 2 Results ===");
        System.out.println("Initial Available: 50 units");
        System.out.println("Requests: 10 threads × 10 units = 100 units total");
        System.out.println("Success Count: " + successCount.get());
        System.out.println("Failure Count: " + failureCount.get());
        System.out.println("Final Available: " + finalProduct.getAvailableQuantity());
        System.out.println("Final Reserved: " + finalProduct.getReservedQuantity());
        
        // Assertions
        assertTrue(finalProduct.getAvailableQuantity() >= 0, 
                "Available quantity should never be negative (overselling detected!)");
        assertEquals(5, successCount.get(), 
                "Exactly 5 reservations should succeed (50 units / 10 units per order)");
        assertEquals(5, failureCount.get(), 
                "Exactly 5 reservations should fail");
        assertEquals(0, finalProduct.getAvailableQuantity(), 
                "Should have 0 units remaining (50 - 50)");
        assertEquals(50, finalProduct.getReservedQuantity(), 
                "Should have 50 units reserved");
    }

    /**
     * Test Case 3: Multiple Products Race Condition
     * 
     * Objective: Test race condition across multiple products in same order
     * Setup: Product A: 5 units, Product B: 5 units
     * Action: 3 threads reserve (Product A: 3 units, Product B: 3 units)
     * 
     * Expected (Vulnerable): All succeed, both products oversold ❌
     * Expected (Fixed): First succeeds, others fail ✅
     */
    @Test
    @DisplayName("Test Case 3: Multiple Products - Race condition across multiple products")
    void testMultipleProductsRaceCondition() throws InterruptedException {
        // Setup: Create two products with 5 units each
        InventoryItem productA = InventoryItem.builder()
                .id(PRODUCT_A_ID)
                .name("Product A")
                .description("Test Product A")
                .availableQuantity(5)
                .reservedQuantity(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        InventoryItem productB = InventoryItem.builder()
                .id(PRODUCT_B_ID)
                .name("Product B")
                .description("Test Product B")
                .availableQuantity(5)
                .reservedQuantity(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        inventoryRepository.save(productA);
        inventoryRepository.save(productB);

        // Action: 3 threads try to reserve 3 units of each product
        int numberOfThreads = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    OrderCreatedEvent event = createOrderEvent(
                            UUID.randomUUID(),
                            List.of(
                                    new OrderItemDto(PRODUCT_A_ID, 3, BigDecimal.valueOf(10.00)),
                                    new OrderItemDto(PRODUCT_B_ID, 3, BigDecimal.valueOf(10.00))
                            )
                    );
                    
                    inventoryService.reserveInventory(event);
                    successCount.incrementAndGet();
                    System.out.println("Thread " + threadId + " - Reservation SUCCEEDED");
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("Thread " + threadId + " - Reservation FAILED");
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        
        assertTrue(completed, "All threads should complete within timeout");

        // Verify results
        InventoryItem finalProductA = inventoryRepository.findById(PRODUCT_A_ID).orElseThrow();
        InventoryItem finalProductB = inventoryRepository.findById(PRODUCT_B_ID).orElseThrow();
        
        System.out.println("\n=== Test Case 3 Results ===");
        System.out.println("Initial: Product A = 5 units, Product B = 5 units");
        System.out.println("Requests: 3 threads × (3 units A + 3 units B)");
        System.out.println("Success Count: " + successCount.get());
        System.out.println("Failure Count: " + failureCount.get());
        System.out.println("Product A - Available: " + finalProductA.getAvailableQuantity() + ", Reserved: " + finalProductA.getReservedQuantity());
        System.out.println("Product B - Available: " + finalProductB.getAvailableQuantity() + ", Reserved: " + finalProductB.getReservedQuantity());
        
        // Assertions
        assertTrue(finalProductA.getAvailableQuantity() >= 0, 
                "Product A available quantity should never be negative");
        assertTrue(finalProductB.getAvailableQuantity() >= 0, 
                "Product B available quantity should never be negative");
        assertEquals(1, successCount.get(), 
                "Only 1 reservation should succeed");
        assertEquals(2, failureCount.get(), 
                "2 reservations should fail");
        assertEquals(2, finalProductA.getAvailableQuantity(), 
                "Product A should have 2 units remaining");
        assertEquals(2, finalProductB.getAvailableQuantity(), 
                "Product B should have 2 units remaining");
    }

    /**
     * Test Case 4: Rapid Sequential Requests
     * 
     * Objective: Test with minimal time gap between requests
     * Setup: Product with 20 units available
     * Action: 5 threads with 0ms delay reserve 5 units each
     * 
     * Expected (Vulnerable): All succeed, resulting in -5 available ❌
     * Expected (Fixed): First 4 succeed, last 1 fails ✅
     */
    @Test
    @DisplayName("Test Case 4: Rapid Sequential - Minimal time gap between requests")
    void testRapidSequentialRequests() throws InterruptedException {
        // Setup: Create product with 20 units
        InventoryItem product = InventoryItem.builder()
                .id(PRODUCT_C_ID)
                .name("Product C")
                .description("Test Product C")
                .availableQuantity(20)
                .reservedQuantity(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        inventoryRepository.save(product);

        // Action: 5 threads try to reserve 5 units each with 0ms delay
        int numberOfThreads = 5;
        int quantityPerOrder = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    OrderCreatedEvent event = createOrderEvent(
                            UUID.randomUUID(),
                            List.of(new OrderItemDto(PRODUCT_C_ID, quantityPerOrder, BigDecimal.valueOf(10.00)))
                    );
                    
                    inventoryService.reserveInventory(event);
                    successCount.incrementAndGet();
                    System.out.println("Thread " + threadId + " - Reservation SUCCEEDED");
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("Thread " + threadId + " - Reservation FAILED");
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        
        assertTrue(completed, "All threads should complete within timeout");

        // Verify results
        InventoryItem finalProduct = inventoryRepository.findById(PRODUCT_C_ID).orElseThrow();
        
        System.out.println("\n=== Test Case 4 Results ===");
        System.out.println("Initial Available: 20 units");
        System.out.println("Requests: 5 threads × 5 units = 25 units total");
        System.out.println("Success Count: " + successCount.get());
        System.out.println("Failure Count: " + failureCount.get());
        System.out.println("Final Available: " + finalProduct.getAvailableQuantity());
        System.out.println("Final Reserved: " + finalProduct.getReservedQuantity());
        
        // Assertions
        assertTrue(finalProduct.getAvailableQuantity() >= 0, 
                "Available quantity should never be negative (overselling detected!)");
        assertEquals(4, successCount.get(), 
                "Exactly 4 reservations should succeed (20 units / 5 units per order)");
        assertEquals(1, failureCount.get(), 
                "Exactly 1 reservation should fail");
        assertEquals(0, finalProduct.getAvailableQuantity(), 
                "Should have 0 units remaining (20 - 20)");
        assertEquals(20, finalProduct.getReservedQuantity(), 
                "Should have 20 units reserved");
    }

    /**
     * Helper method to create OrderCreatedEvent
     */
    private OrderCreatedEvent createOrderEvent(UUID orderId, List<OrderItemDto> items) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setCorrelationId(UUID.randomUUID());
        event.setItems(items);
        return event;
    }
}

// Made with Bob
