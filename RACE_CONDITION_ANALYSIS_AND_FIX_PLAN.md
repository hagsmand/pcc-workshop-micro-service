# Race Condition Analysis and Fix Plan

## Executive Summary
This document outlines a comprehensive plan to identify, test, and fix race condition vulnerabilities in the Inventory Service's `reserveInventory()` method.

---

## Step 1: Race Condition Vulnerability Analysis

### Identified Vulnerable API
**Service:** Inventory Service  
**Method:** `reserveInventory(OrderCreatedEvent orderCreatedEvent)`  
**Location:** `inventory-service/src/main/java/com/hacisimsek/inventory/service/impl/InventoryServiceImpl.java`

### Vulnerability Details

#### Type: Check-Then-Act Race Condition
The method performs a **non-atomic** read-modify-write operation:

```java
@Transactional
public void reserveInventory(OrderCreatedEvent orderCreatedEvent) {
    // STEP 1: CHECK - Read current inventory (lines 42-61)
    for (var orderItem : orderCreatedEvent.getItems()) {
        InventoryItem item = inventoryRepository.findById(orderItem.getProductId()).orElse(null);
        if (item.getAvailableQuantity() < orderItem.getQuantity()) {
            allItemsAvailable = false;
        }
    }
    
    // STEP 2: ACT - Update inventory (lines 97-102)
    for (var reservationItem : reservationItems) {
        InventoryItem item = inventoryRepository.findById(reservationItem.getProductId()).orElseThrow();
        item.setAvailableQuantity(item.getAvailableQuantity() - reservationItem.getQuantity());
        item.setReservedQuantity(item.getReservedQuantity() + reservationItem.getQuantity());
        inventoryRepository.save(item);
    }
}
```

#### Why It's Vulnerable
1. **Async Processing:** Kafka consumers process `OrderCreatedEvent` messages concurrently
2. **MongoDB Operations:** Uses MongoDB (not ACID-compliant for multi-document transactions)
3. **Time Gap:** Window between CHECK and ACT allows concurrent modifications
4. **No Locking:** No pessimistic or optimistic locking mechanism

#### Attack Scenario
```
Time | Thread A (Order 1)              | Thread B (Order 2)              | Inventory State
-----|----------------------------------|----------------------------------|------------------
T0   | -                                | -                                | Available: 10
T1   | CHECK: Read available = 10      | -                                | Available: 10
T2   | -                                | CHECK: Read available = 10      | Available: 10
T3   | ACT: Reserve 8 units            | -                                | Available: 2
T4   | -                                | ACT: Reserve 8 units            | Available: -6 ❌
```

**Result:** Overselling by 6 units!

---

## Step 2: Test Case Design and Implementation

### Test Strategy
Create 4 concurrent test scenarios that exploit the race condition vulnerability.

### Test File Creation
**Location:** `inventory-service/src/test/java/com/hacisimsek/inventoryservice/service/InventoryRaceConditionTest.java`

**Test Framework:** JUnit 5 + Spring Boot Test
**Concurrency Mechanism:** ExecutorService with CountDownLatch for synchronized thread execution
**Test Infrastructure:**
- Uses `@SpringBootTest` with test profile
- Mocks Kafka to prevent actual message publishing
- Uses embedded MongoDB for test isolation
- Each test uses `@BeforeEach` to clean repositories

**Key Testing Patterns:**
1. **CountDownLatch Synchronization:** All threads wait at a start gate, then execute simultaneously to maximize race condition exposure
2. **AtomicInteger Counters:** Track success/failure counts thread-safely
3. **Timeout Protection:** 30-second timeout prevents hanging tests
4. **Detailed Logging:** Each thread logs its outcome for debugging

### Test Case 1: Basic Concurrent Reservation (2 Threads)
**Objective:** Demonstrate basic race condition with 2 concurrent orders
- **Setup:** Product with 10 units available
- **Action:** 2 threads simultaneously reserve 8 units each
- **Expected (Vulnerable):** Both succeed, resulting in -6 available ❌
- **Expected (Fixed):** One succeeds, one fails ✅

### Test Case 2: High Concurrency (10 Threads)
**Objective:** Stress test with multiple concurrent requests
- **Setup:** Product with 50 units available
- **Action:** 10 threads simultaneously reserve 10 units each
- **Expected (Vulnerable):** All succeed, resulting in -50 available ❌
- **Expected (Fixed):** First 5 succeed, last 5 fail ✅

### Test Case 3: Multiple Products Race Condition
**Objective:** Test race condition across multiple products in same order
- **Setup:** 
  - Product A: 5 units available
  - Product B: 5 units available
- **Action:** 3 threads reserve (Product A: 3 units, Product B: 3 units)
- **Expected (Vulnerable):** All succeed, both products oversold ❌
- **Expected (Fixed):** First succeeds, others fail ✅

### Test Case 4: Rapid Sequential Requests
**Objective:** Test with minimal time gap between requests
- **Setup:** Product with 20 units available
- **Action:** 5 threads with 0ms delay reserve 5 units each
- **Expected (Vulnerable):** All succeed, resulting in -5 available ❌
- **Expected (Fixed):** First 4 succeed, last 1 fails ✅

### Test Implementation Details
```java
// Test framework: JUnit 5 + Spring Boot Test
// Concurrency: ExecutorService with CountDownLatch
// Assertions: Verify final inventory state and reservation counts
```

---

## Step 3: Initial Test Execution

### Test Execution Environment
**Container Orchestration:** Podman Compose
**Configuration File:** `podman-compose.race-condition-test.yml`

### Infrastructure Services
The test environment includes:
1. **MongoDB** (`mongodb-race-test`) - Port 27017
   - Database: `race-condition-tests`
   - Health check: mongosh ping
   
2. **Redis** (`redis-race-test`) - Port 6379
   - Used for distributed locking (if implemented)
   - Health check: redis-cli ping
   
3. **Kafka + Zookeeper** (`kafka-race-test`, `zookeeper-race-test`)
   - Port 9092 for Kafka
   - Required for event publishing (mocked in tests)

4. **Maven Test Runner** (`race-condition-test-runner`)
   - Uses Maven 3.9.9 with Eclipse Temurin 17
   - Mounts workspace directory
   - Runs with test profile

### Test Execution Command
```bash
# Start infrastructure and run tests
podman-compose -f podman-compose.race-condition-test.yml up --abort-on-container-exit

# Or manually:
podman-compose -f podman-compose.race-condition-test.yml up -d mongodb redis kafka zookeeper
podman-compose -f podman-compose.race-condition-test.yml run race-condition-tests
```

### Test Execution Steps
The Maven test runner executes:
1. Install parent POM: `mvn -B -ntp -N install`
2. Build common-library: `mvn -B -ntp -pl common-library clean install -DskipTests`
3. Run race condition tests: `mvn -B -ntp -pl inventory-service -Dtest=InventoryRaceConditionTest test`

### Expected Results (Before Fix)
```
Test Case 1: ❌ FAILED - Overselling detected (-6 units)
  - Assertion failure: "Available quantity should never be negative"
  - Both threads succeed when only one should
  
Test Case 2: ❌ FAILED - Overselling detected (-50 units)
  - Assertion failure: "Available quantity should never be negative"
  - All 10 threads succeed when only 5 should
  
Test Case 3: ❌ FAILED - Multiple products oversold
  - Assertion failures on both Product A and Product B
  - All 3 threads succeed when only 1 should
  
Test Case 4: ❌ FAILED - Overselling detected (-5 units)
  - Assertion failure: "Available quantity should never be negative"
  - All 5 threads succeed when only 4 should
```

### Results File
`test-results/race-condition-test-results-BEFORE-FIX.md`

### Cleanup After Testing
```bash
# Stop and remove containers
podman-compose -f podman-compose.race-condition-test.yml down

# Or use cleanup script
./scripts/cleanup-race-test-containers.sh
```

---

## Step 4: Implementation of Thread-Safe Solution

### Critical Code Location
**File:** `inventory-service/src/main/java/com/hacisimsek/inventory/service/impl/InventoryServiceImpl.java`
**Method:** `reserveInventory(OrderCreatedEvent orderCreatedEvent)` (lines 33-112)

### Current Vulnerable Code Pattern
```java
@Transactional
public void reserveInventory(OrderCreatedEvent orderCreatedEvent) {
    // STEP 1: CHECK (lines 42-68) - NON-ATOMIC READ
    for (var orderItem : orderCreatedEvent.getItems()) {
        InventoryItem item = inventoryRepository.findById(orderItem.getProductId()).orElse(null);
        if (item.getAvailableQuantity() < orderItem.getQuantity()) {
            allItemsAvailable = false;
        }
    }
    
    // STEP 2: ACT (lines 97-102) - NON-ATOMIC WRITE
    for (var reservationItem : reservationItems) {
        InventoryItem item = inventoryRepository.findById(reservationItem.getProductId()).orElseThrow();
        item.setAvailableQuantity(item.getAvailableQuantity() - reservationItem.getQuantity());
        item.setReservedQuantity(item.getReservedQuantity() + reservationItem.getQuantity());
        inventoryRepository.save(item);
    }
}
```

**Problem:** Time gap between CHECK and ACT allows concurrent threads to read stale data.

### Solution: Pessimistic Locking with MongoDB

Since the service uses **MongoDB** (not a traditional RDBMS), we need a MongoDB-compatible solution:

#### Option A: Application-Level Synchronized Lock (Recommended for Single Instance)
```java
private final Object inventoryLock = new Object();

@Transactional
public void reserveInventory(OrderCreatedEvent orderCreatedEvent) {
    synchronized (inventoryLock) {
        // Existing logic here - now thread-safe
    }
}
```

#### Option B: Distributed Lock with Redis (Recommended for Multi-Instance)
```java
@Autowired
private RedissonClient redissonClient;

@Transactional
public void reserveInventory(OrderCreatedEvent orderCreatedEvent) {
    RLock lock = redissonClient.getLock("inventory:reservation");
    try {
        lock.lock(10, TimeUnit.SECONDS);
        // Existing logic here - now distributed-safe
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

#### Option C: MongoDB Optimistic Locking with Version Field
```java
// Add to InventoryItem.java
@Version
private Long version;

// Repository method with retry logic
@Retryable(value = OptimisticLockingFailureException.class, maxAttempts = 3)
public void reserveInventory(OrderCreatedEvent orderCreatedEvent) {
    // Existing logic - Spring will handle version conflicts
}
```

### Chosen Solution
**Option A (Synchronized Lock)** for initial implementation because:
- ✅ Simple to implement
- ✅ No additional dependencies
- ✅ Suitable for single-instance deployment
- ✅ Can be upgraded to Option B for production

### Detailed Implementation Instructions for Bob

**CRITICAL:** The following changes must be made to `InventoryServiceImpl.java` to pass all race condition tests:

#### 1. Add Lock Management Infrastructure (Add to class fields)
```java
// Add these fields after line 30 (after kafkaTemplate declaration)
private final ConcurrentHashMap<UUID, ReentrantLock> productLocks = new ConcurrentHashMap<>();
```

#### 2. Add Lock Acquisition Helper Method (Add as new method)
```java
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
```

#### 3. Add Lock Release Helper Method (Add as new method)
```java
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
```

#### 4. Modify reserveInventory() Method (Wrap entire method body)
```java
@Override
@Transactional
public void reserveInventory(OrderCreatedEvent orderCreatedEvent) {
    log.info("Processing inventory reservation for order: {}", orderCreatedEvent.getOrderId());
    
    // Extract all product IDs from the order
    List<UUID> productIds = orderCreatedEvent.getItems().stream()
            .map(item -> item.getProductId())
            .collect(Collectors.toList());
    
    // Acquire locks for all products in sorted order
    List<ReentrantLock> locks = acquireLocksInOrder(productIds);
    
    try {
        // EXISTING LOGIC GOES HERE (lines 37-111)
        // Keep all the existing code from the original method:
        // - List<InventoryReservation.ReservationItem> reservationItems = new ArrayList<>();
        // - boolean allItemsAvailable = true;
        // - StringBuilder insufficientItemsMessage = new StringBuilder();
        // - for loop checking availability
        // - if (!allItemsAvailable) block
        // - Create reservation
        // - Update inventory quantities
        // - Send success event
        
    } finally {
        // Always release locks, even if an exception occurs
        releaseAllLocks(locks);
        log.debug("Released all locks for order: {}", orderCreatedEvent.getOrderId());
    }
}
```

#### 5. Add Required Imports (Add to imports section)
```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
```

### Implementation Summary
The fix works by:
1. **Product-Level Locking:** Each product gets its own `ReentrantLock` stored in a `ConcurrentHashMap`
2. **Sorted Lock Acquisition:** Locks are acquired in sorted UUID order to prevent deadlocks
3. **Timeout Protection:** `tryLock(10, TimeUnit.SECONDS)` prevents indefinite blocking
4. **Guaranteed Release:** `finally` block ensures locks are always released
5. **Atomic Operations:** The entire CHECK-THEN-ACT sequence is now atomic per product

### What This Fixes
- ✅ **Test Case 1:** Only 1 of 2 threads will successfully reserve 8 units
- ✅ **Test Case 2:** Only 5 of 10 threads will successfully reserve 10 units each
- ✅ **Test Case 3:** Only 1 of 3 threads will successfully reserve from both products
- ✅ **Test Case 4:** Only 4 of 5 threads will successfully reserve 5 units each
- ✅ **No Overselling:** `availableQuantity` will never go negative

---

## Step 5: Documentation of Improvements

### Changes Made
1. **Added Product-Level Locking Mechanism**
   - Uses `ConcurrentHashMap<UUID, ReentrantLock>` for fine-grained locking
   - Each product has its own lock to maximize concurrency
   
2. **Lock Acquisition Strategy**
   - Locks acquired in sorted order to prevent deadlocks
   - Timeout mechanism (10 seconds) to prevent indefinite blocking
   
3. **Error Handling**
   - Proper lock release in finally blocks
   - Timeout exceptions logged and handled gracefully

### Code Changes
- **Modified:** `InventoryServiceImpl.java`
- **Added:** Lock management methods
- **Updated:** `reserveInventory()` method with synchronized access

---

## Step 6: Post-Fix Test Execution

### Test Execution Plan
1. Run the same 4 test cases from Step 2
2. Verify all tests pass
3. Confirm no overselling occurs
4. Measure performance impact

### Expected Results (After Fix)
```
Test Case 1: ✅ PASSED - One reservation succeeds, one fails correctly
Test Case 2: ✅ PASSED - First 5 succeed, last 5 fail correctly
Test Case 3: ✅ PASSED - One reservation succeeds, others fail correctly
Test Case 4: ✅ PASSED - First 4 succeed, last 1 fails correctly
```

---

## Step 7: Final Test Results Documentation

### Results File
`test-results/race-condition-test-results-AFTER-FIX.md`

### Metrics to Capture
- Test execution time
- Success/failure counts
- Final inventory states
- Lock contention statistics
- Performance overhead

---

## Step 8: Summary Report

### Report Contents
1. **Vulnerability Summary**
   - Description of race condition
   - Impact assessment
   - Risk level

2. **Test Results Comparison**
   - Before vs After tables
   - Visual representation of improvements

3. **Solution Effectiveness**
   - Performance metrics
   - Scalability considerations
   - Production readiness assessment

4. **Recommendations**
   - Monitoring strategies
   - Future improvements (distributed locking)
   - Best practices for similar scenarios

### Report File
`RACE_CONDITION_FIX_SUMMARY_REPORT.md`

---

## Timeline and Dependencies

### Execution Order
```
Step 1 (Analysis) → Step 2 (Test Creation) → Step 3 (Initial Test Run) →
Step 4 (Implementation) → Step 5 (Documentation) → Step 6 (Verification) →
Step 7 (Results) → Step 8 (Summary)
```

### Estimated Time (Human Implementation)
- Steps 1-2: 1 hour (Analysis + Test Design)
- Step 3: 30 minutes (Test Execution)
- Step 4: 2 hours (Implementation)
- Step 5: 30 minutes (Documentation)
- Step 6: 30 minutes (Re-testing)
- Step 7-8: 1 hour (Results + Summary)
- **Total: ~5.5 hours**

### Estimated Time (Bob/AI Implementation)
- Steps 1-2: 5 minutes (Read existing code + Generate test file)
- Step 3: 5-10 minutes (Execute tests + Wait for results)
- Step 4: 10 minutes (Apply code changes with locking mechanism)
- Step 5: 3 minutes (Generate documentation)
- Step 6: 5-10 minutes (Re-run tests + Wait for results)
- Step 7-8: 5 minutes (Compile results + Generate summary report)
- **Active Tool Time: ~30-40 minutes**
- **Wall-Clock Time: ~45-60 minutes** (including test execution and user confirmations)

**Note:** Bob implementation is significantly faster due to:
- No context switching or decision fatigue
- Parallel information processing
- Automated code generation and testing
- However, wall-clock time depends on test execution duration and user response times for confirmations between steps

---

## Success Criteria

### Must Have
- ✅ All 4 test cases fail before fix
- ✅ All 4 test cases pass after fix
- ✅ No overselling in any scenario
- ✅ Thread-safe implementation verified

### Nice to Have
- ✅ Performance overhead < 10%
- ✅ Lock contention metrics collected
- ✅ Comprehensive documentation
- ✅ Production-ready solution

---

## Risk Mitigation

### Potential Issues
1. **Performance Degradation:** Locking may slow down throughput
   - **Mitigation:** Use fine-grained product-level locks
   
2. **Deadlocks:** Multiple product reservations could deadlock
   - **Mitigation:** Acquire locks in sorted order
   
3. **Single Point of Failure:** Synchronized lock only works for single instance
   - **Mitigation:** Document upgrade path to distributed locking

---

## Next Steps

After completing this plan:
1. Monitor production metrics for lock contention
2. Consider implementing distributed locking for multi-instance deployment
3. Add alerting for inventory anomalies
4. Implement circuit breaker for reservation failures
5. Create runbook for handling race condition incidents

---

**Document Version:** 1.0  
**Created:** 2026-04-24  
**Status:** Ready for Implementation