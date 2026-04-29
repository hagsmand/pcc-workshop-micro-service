# Race Condition Test Case Creation Guide for Bob AI

## Overview
This guide provides instructions for Bob AI to create a comprehensive race condition test suite for the Inventory Service. The test file should be created at:
```
inventory-service/src/test/java/com/hacisimsek/inventoryservice/service/InventoryRaceConditionTest.java
```

---

## Test File Requirements

### Package and Imports
```java
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
```

---

## Class Structure

### Class Declaration and Configuration
```java
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
```

### Required Dependencies
```java
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
```

### Setup Method
```java
    @BeforeEach
    void setUp() {
        // Clean up before each test
        inventoryRepository.deleteAll();
        reservationRepository.deleteAll();
    }
```

---

## Test Cases to Implement

### Test Case 1: Basic Concurrent Reservation (2 Threads)

**Objective**: Demonstrate basic race condition with 2 concurrent orders

**Setup**:
- Product with 10 units available
- 2 threads simultaneously reserve 8 units each

**Expected Results**:
- **Vulnerable System**: Both succeed → -6 available (overselling) ❌
- **Fixed System**: One succeeds, one fails → 2 available ✅

**Implementation Pattern**:
```java
@Test
@DisplayName("Test Case 1: Basic Concurrent Reservation - 2 threads competing for limited inventory")
void testBasicConcurrentReservation() throws InterruptedException {
    // 1. Setup: Create product with initial inventory
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

    // 2. Configure concurrent execution
    int numberOfThreads = 2;
    int quantityPerOrder = 8;
    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    // 3. Submit concurrent tasks
    for (int i = 0; i < numberOfThreads; i++) {
        final int threadId = i;
        executorService.submit(() -> {
            try {
                startLatch.await(); // Wait for all threads to be ready
                
                OrderCreatedEvent event = createOrderEvent(
                        UUID.randomUUID(),
                        List.of(new OrderItemDto(PRODUCT_A_ID, quantityPerOrder, BigDecimal.valueOf(10.00)))
                );
                
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

    // 4. Start all threads simultaneously
    startLatch.countDown();
    
    // 5. Wait for completion
    boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
    executorService.shutdown();
    assertTrue(completed, "All threads should complete within timeout");

    // 6. Verify results
    InventoryItem finalProduct = inventoryRepository.findById(PRODUCT_A_ID).orElseThrow();
    
    System.out.println("\n=== Test Case 1 Results ===");
    System.out.println("Initial Available: 10 units");
    System.out.println("Requests: 2 threads × 8 units = 16 units total");
    System.out.println("Success Count: " + successCount.get());
    System.out.println("Failure Count: " + failureCount.get());
    System.out.println("Final Available: " + finalProduct.getAvailableQuantity());
    System.out.println("Final Reserved: " + finalProduct.getReservedQuantity());
    
    // 7. Assertions
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
```

---

### Test Case 2: High Concurrency (10 Threads)

**Objective**: Stress test with multiple concurrent requests

**Setup**:
- Product with 50 units available
- 10 threads simultaneously reserve 10 units each

**Expected Results**:
- **Vulnerable System**: All succeed → -50 available ❌
- **Fixed System**: First 5 succeed, last 5 fail → 0 available ✅

**Key Parameters**:
- `numberOfThreads = 10`
- `quantityPerOrder = 10`
- `initialAvailableQuantity = 50`
- Expected success: 5 threads
- Expected failure: 5 threads

---

### Test Case 3: Multiple Products Race Condition

**Objective**: Test race condition across multiple products in same order

**Setup**:
- Product A: 5 units available
- Product B: 5 units available
- 3 threads reserve (Product A: 3 units, Product B: 3 units)

**Expected Results**:
- **Vulnerable System**: All succeed → both products oversold ❌
- **Fixed System**: First succeeds, others fail ✅

**Implementation Notes**:
- Create TWO products (PRODUCT_A_ID and PRODUCT_B_ID)
- Each order contains BOTH products
- Verify BOTH products don't go negative

---

### Test Case 4: Rapid Sequential Requests

**Objective**: Test with minimal time gap between requests

**Setup**:
- Product with 20 units available
- 5 threads with 0ms delay reserve 5 units each

**Expected Results**:
- **Vulnerable System**: All succeed → -5 available ❌
- **Fixed System**: First 4 succeed, last 1 fails → 0 available ✅

**Key Parameters**:
- `numberOfThreads = 5`
- `quantityPerOrder = 5`
- `initialAvailableQuantity = 20`
- Expected success: 4 threads
- Expected failure: 1 thread

---

## Helper Methods

### Order Event Creation Helper
```java
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
```

---

## Critical Testing Patterns

### 1. Thread Synchronization Pattern
```java
// Use CountDownLatch to ensure all threads start simultaneously
CountDownLatch startLatch = new CountDownLatch(1);
CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);

// In each thread:
startLatch.await(); // Wait for signal
// ... perform operation ...
completionLatch.countDown(); // Signal completion

// In main test:
startLatch.countDown(); // Start all threads
completionLatch.await(30, TimeUnit.SECONDS); // Wait for all to complete
```

### 2. Result Tracking Pattern
```java
AtomicInteger successCount = new AtomicInteger(0);
AtomicInteger failureCount = new AtomicInteger(0);

// In each thread:
try {
    inventoryService.reserveInventory(event);
    successCount.incrementAndGet();
} catch (Exception e) {
    failureCount.incrementAndGet();
}
```

### 3. Assertion Pattern
```java
// Always check for negative inventory (overselling)
assertTrue(finalProduct.getAvailableQuantity() >= 0, 
        "Available quantity should never be negative (overselling detected!)");

// Verify exact success/failure counts
assertEquals(expectedSuccess, successCount.get(), 
        "Expected number of successful reservations");
assertEquals(expectedFailure, failureCount.get(), 
        "Expected number of failed reservations");

// Verify final inventory state
assertEquals(expectedAvailable, finalProduct.getAvailableQuantity(), 
        "Final available quantity should match expected");
assertEquals(expectedReserved, finalProduct.getReservedQuantity(), 
        "Final reserved quantity should match expected");
```

---

## Console Output Format

Each test should print results in this format:
```
=== Test Case X Results ===
Initial Available: X units
Requests: Y threads × Z units = Total units total
Success Count: X
Failure Count: Y
Final Available: X
Final Reserved: Y
```

---

## Important Notes for Bob AI

1. **File Location**: Create the file at exactly this path:
   ```
   inventory-service/src/test/java/com/hacisimsek/inventoryservice/service/InventoryRaceConditionTest.java
   ```

2. **All Four Test Cases Required**: Implement all 4 test cases as specified

3. **Thread Safety**: Use `AtomicInteger` for counters, `CountDownLatch` for synchronization

4. **Timeout Handling**: All tests should have 30-second timeout for thread completion

5. **Cleanup**: `@BeforeEach` must clean both repositories

6. **Assertions**: Always check for negative inventory first (critical for detecting overselling)

7. **Console Output**: Include detailed console output for debugging

8. **Comments**: Add clear comments explaining the race condition scenario

9. **Expected Failures**: These tests are EXPECTED to fail on vulnerable code (before fix)

10. **Product IDs**: Use the static UUID constants (PRODUCT_A_ID, PRODUCT_B_ID, PRODUCT_C_ID)

---

## Verification Checklist

Before considering the test complete, verify:
- ✅ All 4 test cases implemented
- ✅ Proper package declaration
- ✅ All required imports included
- ✅ `@SpringBootTest` configuration correct
- ✅ `@BeforeEach` cleanup implemented
- ✅ Thread synchronization using `CountDownLatch`
- ✅ Result tracking using `AtomicInteger`
- ✅ Comprehensive assertions for each test
- ✅ Console output for debugging
- ✅ Helper method for creating events
- ✅ Proper exception handling
- ✅ Timeout handling (30 seconds)
- ✅ File ends with "// Made with Bob"

---

## Example Bob AI Prompt

To generate this test file, use a prompt like:

```
Create a comprehensive race condition test suite for the Inventory Service following the specifications in CREATE_TEST_CASE.md. The test file should:

1. Be created at: inventory-service/src/test/java/com/hacisimsek/inventoryservice/service/InventoryRaceConditionTest.java

2. Include all 4 test cases:
   - Test Case 1: Basic Concurrent Reservation (2 threads, 10 units, 8 per order)
   - Test Case 2: High Concurrency (10 threads, 50 units, 10 per order)
   - Test Case 3: Multiple Products (3 threads, 2 products with 5 units each, 3 per order)
   - Test Case 4: Rapid Sequential (5 threads, 20 units, 5 per order)

3. Use ExecutorService and CountDownLatch for proper thread synchronization

4. Include comprehensive assertions to detect overselling (negative inventory)

5. Add detailed console output for debugging

6. Follow the exact structure and patterns specified in CREATE_TEST_CASE.md

Please generate the complete test file with all imports, setup, test cases, and helper methods.
```

---

**Document Version**: 1.0  
**Last Updated**: 2026-04-29  
**Target File**: `inventory-service/src/test/java/com/hacisimsek/inventoryservice/service/InventoryRaceConditionTest.java`