# Unit Testing Workshop - Quick Reference Card

## 🎯 Workshop Goal
Write meaningful unit tests for **OrderServiceImpl** using **Given-When-Then** pattern with AI assistance.

---

## 📋 Quick Setup

### Test Class Template
```java
package com.hacisimsek.order.service;

import com.hacisimsek.order.dto.*;
import com.hacisimsek.order.model.*;
import com.hacisimsek.order.repository.OrderRepository;
import com.hacisimsek.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @InjectMocks
    private OrderServiceImpl orderService;
    
    // Your tests go here
}
```

---

## 🤖 AI Prompt Template (Copy & Paste)

```
I need to write a unit test for the OrderServiceImpl class in a Spring Boot microservice.

CONTEXT:
- Service: OrderServiceImpl
- Method: [METHOD_NAME - e.g., createOrder, getOrderById, updateOrderStatus]
- Dependencies: OrderRepository (mocked), KafkaTemplate (mocked)
- Testing Framework: JUnit 5, Mockito
- Pattern: Given-When-Then

BUSINESS SCENARIO:
Given [describe initial state in plain English]
When [describe the action being tested]
Then [describe expected outcomes]

REQUIREMENTS:
1. Use @ExtendWith(MockitoExtension.class)
2. Mock dependencies with @Mock
3. Inject mocks with @InjectMocks
4. Test method name: should[ExpectedBehavior]When[Condition]
5. Include assertions for:
   - Return values
   - Method invocations (verify)
   - Exception handling (assertThrows)
   - Object state
6. DO NOT create fake/trivial tests
7. Test REAL business logic from OrderServiceImpl
8. Consider edge cases

CODE CONTEXT:
[Paste the relevant method from OrderServiceImpl.java]

Generate a complete, runnable test method with Given-When-Then comments.
```

---

## 📝 7 Test Scenarios

### ✅ Scenario 1: Create Order - Single Item (EASY)
**Given:** Customer orders 1 product  
**When:** createOrder() is called  
**Then:** Order saved, total calculated, status transitions, event published

### ✅ Scenario 2: Create Order - Multiple Items (MEDIUM)
**Given:** Customer orders 3 different products  
**When:** createOrder() is called  
**Then:** Total = sum of all items, all items saved

### ✅ Scenario 3: Get Order - Not Found (MEDIUM)
**Given:** Order ID doesn't exist  
**When:** getOrderById() is called  
**Then:** RuntimeException thrown with "Order not found"

### ✅ Scenario 4: Update Order Status (MEDIUM)
**Given:** Existing order with PENDING status  
**When:** updateOrderStatus() is called  
**Then:** Status updated, repository.save() called once

### ✅ Scenario 5: Get Orders By Customer (ADVANCED)
**Given:** Customer has 3 orders  
**When:** getOrdersByCustomerId() is called  
**Then:** All 3 orders returned, properly mapped

### ✅ Scenario 6: Zero Quantity Edge Case (ADVANCED)
**Given:** Product with 0 quantity  
**When:** createOrder() is called  
**Then:** Total = $0, order still created

### ✅ Scenario 7: Negative Price Edge Case (ADVANCED)
**Given:** Product with negative price  
**When:** createOrder() is called  
**Then:** Verify refund/credit logic

---

## 🔧 Common Mockito Patterns

### Mock Repository Save
```java
when(orderRepository.save(any(Order.class)))
    .thenAnswer(invocation -> invocation.getArgument(0));
```

### Mock Repository FindById - Found
```java
Order mockOrder = Order.builder()
    .id(orderId)
    .customerId(customerId)
    .status(Order.OrderStatus.PENDING)
    .build();
    
when(orderRepository.findById(orderId))
    .thenReturn(Optional.of(mockOrder));
```

### Mock Repository FindById - Not Found
```java
when(orderRepository.findById(any(UUID.class)))
    .thenReturn(Optional.empty());
```

### Verify Method Called
```java
verify(orderRepository, times(2)).save(any(Order.class));
verify(kafkaTemplate, times(1)).send(eq("order-events"), any());
```

### Capture Arguments
```java
ArgumentCaptor<OrderCreatedEvent> eventCaptor = 
    ArgumentCaptor.forClass(OrderCreatedEvent.class);
    
verify(kafkaTemplate).send(eq("order-events"), eventCaptor.capture());

OrderCreatedEvent capturedEvent = eventCaptor.getValue();
assertEquals(orderId, capturedEvent.getOrderId());
```

---

## ✅ Assertion Cheat Sheet

### Basic Assertions
```java
assertNotNull(response);
assertEquals(expected, actual);
assertTrue(condition);
assertFalse(condition);
```

### Collection Assertions
```java
assertEquals(3, list.size());
assertTrue(list.contains(item));
assertFalse(list.isEmpty());
```

### Exception Assertions
```java
RuntimeException exception = assertThrows(
    RuntimeException.class,
    () -> orderService.getOrderById(invalidId)
);
assertTrue(exception.getMessage().contains("Order not found"));
```

### BigDecimal Assertions
```java
assertEquals(0, 
    new BigDecimal("2000").compareTo(response.getTotalAmount())
);
// OR
assertEquals(new BigDecimal("2000"), response.getTotalAmount());
```

---

## 🚫 Common Mistakes to Avoid

### ❌ DON'T: Fake Test
```java
@Test
void shouldPass() {
    assertTrue(true); // USELESS!
}
```

### ✅ DO: Real Test
```java
@Test
void shouldCalculateTotalCorrectly() {
    // Given: Real test data
    OrderRequest request = createRequest();
    when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    
    // When: Real method call
    OrderResponse response = orderService.createOrder(request);
    
    // Then: Real assertions
    assertEquals(new BigDecimal("2000"), response.getTotalAmount());
    verify(orderRepository, times(2)).save(any());
}
```

---

## 🏃 Running Tests

### Run Single Test Class
```bash
cd order-service
mvn test -Dtest=OrderServiceImplTest
```

### Run Single Test Method
```bash
mvn test -Dtest=OrderServiceImplTest#shouldCreateOrderWithCorrectTotal
```

### Run All Tests
```bash
mvn test
```

### With Coverage Report
```bash
mvn clean test jacoco:report
# Report: target/site/jacoco/index.html
```

---

## 📊 Success Checklist

- [ ] All 7 scenarios implemented
- [ ] Each test follows GWT pattern
- [ ] No fake/trivial assertions
- [ ] Proper mocking used
- [ ] Multiple assertions per test
- [ ] Descriptive test names
- [ ] Edge cases covered
- [ ] verify() calls included
- [ ] All tests pass (green)
- [ ] Code coverage > 80%

---

## 🆘 Troubleshooting

### Problem: NullPointerException
**Solution:** Check if all @Mock dependencies are injected

### Problem: Test fails with "Wanted but not invoked"
**Solution:** Verify the method is actually called in the code

### Problem: ArgumentCaptor returns null
**Solution:** Ensure verify() is called before getValue()

### Problem: BigDecimal assertion fails
**Solution:** Use compareTo() or ensure scale matches

### Problem: Test passes but doesn't test anything
**Solution:** Add verify() calls and check actual business logic

---

## 📚 Key Concepts

### Given-When-Then
- **Given** = Setup/Arrange (test data, mocks)
- **When** = Action/Act (call the method)
- **Then** = Assert/Verify (check results)

### Mocking
- **Mock** = Fake object that simulates real dependency
- **Stub** = Pre-programmed response (when...thenReturn)
- **Verify** = Check if method was called

### Unit Test
- Tests **one unit** in isolation
- **Fast** execution (no DB, no network)
- **Deterministic** (same input = same output)
- **Independent** (can run in any order)

---

## 🎯 Time Management

| Activity | Time | Cumulative |
|----------|------|------------|
| Setup & Review | 5 min | 5 min |
| Scenario 1 (Easy) | 5 min | 10 min |
| Scenario 2 (Medium) | 5 min | 15 min |
| Scenario 3 (Medium) | 5 min | 20 min |
| Scenario 4 (Medium) | 5 min | 25 min |
| Scenario 5 (Advanced) | 7 min | 32 min |
| Scenario 6 (Edge) | 5 min | 37 min |
| Scenario 7 (Edge) | 5 min | 42 min |
| Review & Discussion | 5 min | 47 min |

**Target: 30-45 minutes total**

---

## 💡 Pro Tips

1. **Read the code first** - Understand what you're testing
2. **Start simple** - Begin with easy scenarios
3. **Use descriptive names** - Test names should tell a story
4. **Test behavior, not implementation** - Focus on what, not how
5. **One assertion concept per test** - Keep tests focused
6. **Verify side effects** - Use verify() for void methods
7. **Test edge cases** - Null, empty, zero, negative values
8. **Keep tests independent** - No shared state between tests

---

**Print this page and keep it handy during the workshop! 📄**