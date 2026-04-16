# Unit Testing Workshop Guide: Test-Driven Development with Order Service

## Workshop Overview
**Duration:** 30-45 minutes  
**Target Service:** Order Service  
**Focus:** Writing meaningful unit tests using Given-When-Then (GWT) pattern with AI assistance

---

## 📚 Part 1: TDD Testing Patterns (5 minutes)

### Understanding Testing Patterns

#### 1. Given-When-Then (GWT)
A narrative, behavior-driven approach that tells a story:
- **Given** (Setup): The initial context - what's the state before action?
- **When** (Action): The trigger - what action occurs?
- **Then** (Assertion): The outcome - what should happen?

**Example Story:**
```
Given a customer with ID "123" wants to order 2 laptops at $1000 each
When the order is created
Then the total amount should be $2000 and status should be PENDING
```

#### 2. Arrange-Act-Assert (AAA)
Technical execution focused on test structure:
- **Arrange**: Set up test data and dependencies
- **Act**: Execute the method under test
- **Assert**: Verify the results

**Example Story:**
```
Arrange a customer with ID "123" ordering 2 laptops at $1000 each
Act by creating the order
Assert that the total amount is $2000 and the status is PENDING
```

#### 3. Setup-Exercise-Verify-Teardown (SEVT)
For integration and system testing with cleanup needs.

### Why GWT for This Workshop?
- **Collaborative**: Easy for business stakeholders to understand
- **Clear Intent**: Each test reads like a specification
- **AI-Friendly**: LLMs understand narrative context better

---

## 🎯 Part 2: Order Service Architecture (5 minutes)

### Service Under Test: OrderServiceImpl

```
┌─────────────────────────────────────────────────────────────┐
│                    OrderServiceImpl                          │
├─────────────────────────────────────────────────────────────┤
│  Dependencies:                                               │
│  • OrderRepository (Database)                                │
│  • KafkaTemplate (Event Publishing)                          │
├─────────────────────────────────────────────────────────────┤
│  Key Methods:                                                │
│  1. createOrder(OrderRequest) → OrderResponse                │
│     - Converts items                                         │
│     - Calculates total amount                                │
│     - Saves order                                            │
│     - Publishes OrderCreatedEvent                            │
│                                                              │
│  2. getOrderById(UUID) → OrderResponse                       │
│     - Retrieves order                                        │
│     - Throws exception if not found                          │
│                                                              │
│  3. updateOrderStatus(UUID, OrderStatus) → void              │
│     - Updates order status                                   │
│     - Saves changes                                          │
│                                                              │
│  4. getOrdersByCustomerId(UUID) → List<OrderResponse>        │
│     - Retrieves all orders for customer                      │
└─────────────────────────────────────────────────────────────┘
```

### Business Rules to Test:
1. **Total Calculation**: Sum of (price × quantity) for all items
2. **Status Transitions**: PENDING → INVENTORY_CHECKING after creation
3. **Event Publishing**: OrderCreatedEvent sent to Kafka
4. **Error Handling**: Exception when order not found
5. **Data Mapping**: Correct conversion between DTOs and entities

---

## 🧪 Part 3: Hands-On Testing with AI (30-35 minutes)

### Test Scenarios to Implement

#### Scenario 1: Create Order with Single Item (Easy)
**Business Context:**
```
Given a customer wants to order 1 product
When the order is created
Then the order should be saved with correct total amount
And the status should be PENDING initially
And then updated to INVENTORY_CHECKING
And an OrderCreatedEvent should be published to Kafka
```

#### Scenario 2: Create Order with Multiple Items (Medium)
**Business Context:**
```
Given a customer wants to order 3 different products:
  - Product A: 2 units at $50 each
  - Product B: 1 unit at $100
  - Product C: 3 units at $25 each
When the order is created
Then the total amount should be $275 (2×50 + 1×100 + 3×25)
And all items should be saved with the order
And the order should contain exactly 3 items
```

#### Scenario 3: Get Order By ID - Not Found (Medium)
**Business Context:**
```
Given an order ID that doesn't exist in the database
When we try to retrieve the order
Then a RuntimeException should be thrown
And the exception message should contain "Order not found"
```

#### Scenario 4: Update Order Status (Medium)
**Business Context:**
```
Given an existing order with status PENDING
When the status is updated to PAYMENT_COMPLETED
Then the order should be saved with the new status
And the repository save method should be called exactly once
```

#### Scenario 5: Get Orders By Customer ID (Advanced)
**Business Context:**
```
Given a customer has 3 orders in the system
When we retrieve orders by customer ID
Then all 3 orders should be returned
And each order should be properly mapped to OrderResponse
And the list should contain the correct customer ID for all orders
```

#### Scenario 6: Create Order with Zero Quantity (Edge Case)
**Business Context:**
```
Given a customer tries to order a product with 0 quantity
When the order is created
Then the total amount should be $0
And the order should still be created (business decision)
```

#### Scenario 7: Create Order with Negative Price (Edge Case)
**Business Context:**
```
Given a customer tries to order a product with negative price
When the order is created
Then the total calculation should handle negative values
And verify the business logic for refunds/credits
```

---

## 🤖 Part 4: AI-Assisted Test Generation

### Prompt Template for LLM

Use this prompt structure to generate meaningful tests:

```
I need to write a unit test for the OrderServiceImpl class in a Spring Boot microservice.

CONTEXT:
- Service: OrderServiceImpl
- Method: [METHOD_NAME]
- Dependencies: OrderRepository (mocked), KafkaTemplate (mocked)
- Testing Framework: JUnit 5, Mockito
- Pattern: Given-When-Then

BUSINESS SCENARIO:
[Describe the scenario in plain English using Given-When-Then]

REQUIREMENTS:
1. Use @ExtendWith(MockitoExtension.class)
2. Mock dependencies with @Mock
3. Inject mocks with @InjectMocks
4. Use meaningful test method names following pattern: should[ExpectedBehavior]When[Condition]
5. Include proper assertions for:
   - Return values
   - Method invocations (verify)
   - Exception handling (assertThrows)
   - Object state
6. DO NOT create fake/trivial tests that always pass
7. Test REAL business logic from the codebase
8. Consider edge cases and error scenarios

CODE CONTEXT:
[Paste relevant code from OrderServiceImpl.java]

Generate a complete, runnable test method with:
- Proper Given (Arrange) section with test data
- Clear When (Act) section
- Comprehensive Then (Assert) section with multiple assertions
- Comments explaining the test scenario
```

### Example Prompt for Scenario 1:

```
I need to write a unit test for the OrderServiceImpl class in a Spring Boot microservice.

CONTEXT:
- Service: OrderServiceImpl
- Method: createOrder(OrderRequest orderRequest)
- Dependencies: OrderRepository (mocked), KafkaTemplate (mocked)
- Testing Framework: JUnit 5, Mockito
- Pattern: Given-When-Then

BUSINESS SCENARIO:
Given a customer with ID "550e8400-e29b-41d4-a716-446655440000" wants to order 2 units of "Laptop" (product ID: "660e8400-e29b-41d4-a716-446655440000") at $1000 per unit
When the createOrder method is called
Then:
- The order should be saved to the repository
- The total amount should be calculated as $2000
- The initial status should be PENDING
- The status should be updated to INVENTORY_CHECKING
- An OrderCreatedEvent should be published to Kafka topic "order-events"
- The saved order should be returned as OrderResponse

REQUIREMENTS:
1. Use @ExtendWith(MockitoExtension.class)
2. Mock OrderRepository and KafkaTemplate
3. Use @InjectMocks for OrderServiceImpl
4. Test method name: shouldCreateOrderWithCorrectTotalAndPublishEventWhenValidRequestProvided
5. Verify:
   - repository.save() called twice (initial save and status update)
   - kafkaTemplate.send() called once with correct topic and event
   - Total amount calculation is correct
   - Status transitions are correct
6. DO NOT create a fake test - use actual business logic
7. Include assertions for all critical behaviors

CODE CONTEXT:
See order-service/src/main/java/com/hacisimsek/order/service/impl/OrderServiceImpl.java
Lines 31-86 contain the createOrder method implementation

Generate a complete, runnable test method.
```

---

## 🎓 Part 5: Workshop Execution Steps

### Step 1: Setup (5 minutes)
1. Open the project in your IDE
2. Navigate to `order-service/src/test/java/com/hacisimsek/order/service/`
3. Create new test class: `OrderServiceImplTest.java`
4. Add basic test structure

### Step 2: Generate Tests with AI (20 minutes)
For each scenario (1-7):
1. Copy the prompt template
2. Fill in the specific scenario details
3. Paste into your AI assistant (Claude, ChatGPT, etc.)
4. Review the generated test
5. Paste into your test class
6. Run the test
7. Fix any issues
8. Verify it tests real logic (not fake assertions)

### Step 3: Run and Verify (5 minutes)
```bash
cd order-service
mvn test -Dtest=OrderServiceImplTest
```

### Step 4: Review and Discuss (5 minutes)
- Which tests were easiest to generate?
- Which required the most refinement?
- What edge cases did you discover?
- How did GWT pattern help clarify requirements?

---

## ✅ Success Criteria

Your tests should:
1. ✅ Follow Given-When-Then structure
2. ✅ Test actual business logic (not fake assertions)
3. ✅ Use proper mocking with Mockito
4. ✅ Include multiple assertions per test
5. ✅ Have descriptive test names
6. ✅ Cover happy paths AND edge cases
7. ✅ Verify method invocations (verify())
8. ✅ All tests pass when run

---

## 🚫 Common Pitfalls to Avoid

### ❌ Fake Test Example (DON'T DO THIS):
```java
@Test
void shouldReturnTrue() {
    assertTrue(true); // This is useless!
}
```

### ✅ Real Test Example (DO THIS):
```java
@Test
void shouldCalculateCorrectTotalAmountWhenMultipleItemsProvided() {
    // Given: Customer orders 2 laptops at $1000 each
    OrderRequest request = createOrderRequestWithItems(
        new OrderItemRequest(productId, "Laptop", 2, new BigDecimal("1000"))
    );
    
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    
    // When: Order is created
    OrderResponse response = orderService.createOrder(request);
    
    // Then: Total should be $2000
    assertEquals(new BigDecimal("2000"), response.getTotalAmount());
    verify(orderRepository, times(2)).save(any(Order.class));
}
```

---

## 📊 Workshop Completion Checklist

- [ ] Understood Given-When-Then pattern
- [ ] Reviewed OrderServiceImpl architecture
- [ ] Generated test for Scenario 1 (single item)
- [ ] Generated test for Scenario 2 (multiple items)
- [ ] Generated test for Scenario 3 (not found exception)
- [ ] Generated test for Scenario 4 (status update)
- [ ] Generated test for Scenario 5 (customer orders)
- [ ] Generated test for Scenario 6 (edge case: zero quantity)
- [ ] Generated test for Scenario 7 (edge case: negative price)
- [ ] All tests pass
- [ ] Tests verify real business logic
- [ ] Code coverage > 80% for OrderServiceImpl

---

## 🎯 Key Takeaways

1. **GWT makes tests readable**: Anyone can understand what's being tested
2. **AI accelerates testing**: But you must provide good context
3. **Real tests catch real bugs**: Fake tests waste time
4. **Edge cases matter**: They reveal business rule gaps
5. **Mocking is essential**: Isolate the unit under test
6. **Verification is key**: Assert both return values AND side effects

---

## 📚 Additional Resources

- JUnit 5 Documentation: https://junit.org/junit5/docs/current/user-guide/
- Mockito Documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
- Given-When-Then: https://martinfowler.com/bliki/GivenWhenThen.html

**Happy Testing! 🚀**