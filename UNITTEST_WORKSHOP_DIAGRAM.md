# Unit Testing Workshop - Visual Diagrams

![Workshop Duration](https://img.shields.io/badge/duration-30--45%20minutes-blue.svg)
![Difficulty](https://img.shields.io/badge/difficulty-beginner%20to%20advanced-green.svg)
![Test Pattern](https://img.shields.io/badge/pattern-Given--When--Then-orange.svg)
![Last Updated](https://img.shields.io/badge/last%20updated-2026--04--16-brightgreen.svg)

Visual reference guide for understanding Test-Driven Development (TDD) patterns and unit testing concepts using the Order Service as a practical example.

*Workshop Focus: Order Service Unit Testing*  
*Pattern: Given-When-Then (GWT)*  
*Last Updated: 2026-04-16*

---

## Table of Contents

- [Given-When-Then Pattern Flow](#given-when-then-pattern-flow)
- [Order Service Test Flow Diagram](#order-service-test-flow-diagram)
- [Test Scenario Complexity Levels](#test-scenario-complexity-levels)
- [Mocking Strategy Diagram](#mocking-strategy-diagram)
- [Test Execution Flow](#test-execution-flow)
- [AAA vs GWT Comparison](#aaa-vs-gwt-comparison)
- [Success Metrics Dashboard](#success-metrics-dashboard)
- [Workshop Timeline](#workshop-timeline)

---

## Given-When-Then Pattern Flow

```mermaid
graph LR
    A[GIVEN<br/>Setup<br/>What's the initial state?] --> B[WHEN<br/>Action<br/>What happens?]
    B --> C[THEN<br/>Assertion<br/>What should be the result?]
    
    style A fill:#e1f5ff,stroke:#01579b,stroke-width:2px,color:#000
    style B fill:#fff9c4,stroke:#f57f17,stroke-width:2px,color:#000
    style C fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px,color:#000
```

**Pattern Explanation:**
- **GIVEN**: Set up the test environment and initial conditions
- **WHEN**: Execute the action or behavior being tested
- **THEN**: Verify the expected outcomes and side effects

---

## Order Service Test Flow Diagram

```mermaid
flowchart TD
    Start[Testing OrderServiceImpl.createOrder]
    
    subgraph Given["GIVEN (Arrange/Setup)"]
        G1[Create Test Data<br/>OrderRequest with items]
        G2[Configure Mocks<br/>orderRepository.save<br/>kafkaTemplate.send]
        G1 --> G2
    end
    
    subgraph When["WHEN (Act/Execute)"]
        W1[OrderResponse response = orderService.createOrder request]
        W2[Internal Flow:<br/>1. Convert OrderItemRequest → OrderItem<br/>2. Calculate total: 2 × $1000 = $2000<br/>3. Create Order entity status: PENDING<br/>4. Save order to repository<br/>5. Create OrderCreatedEvent<br/>6. Update status to INVENTORY_CHECKING<br/>7. Save updated order<br/>8. Publish event to Kafka<br/>9. Map Order → OrderResponse]
        W1 --> W2
    end
    
    subgraph Then["THEN (Assert/Verify)"]
        T1[Verify Return Value<br/>✓ assertNotNull response<br/>✓ assertEquals $2000, totalAmount<br/>✓ assertEquals customerId<br/>✓ assertEquals 1, items.size]
        T2[Verify Repository Interactions<br/>✓ verify orderRepository, times 2.save<br/>- First: PENDING status<br/>- Second: INVENTORY_CHECKING status]
        T3[Verify Kafka Event Publishing<br/>✓ verify kafkaTemplate, times 1.send<br/>✓ Capture and verify event content]
        T1 --> T2
        T2 --> T3
    end
    
    Start --> Given
    Given --> When
    When --> Then
    
    style Start fill:#f3e5f5,stroke:#4a148c,stroke-width:2px,color:#000
    style Given fill:#e1f5ff,stroke:#01579b,stroke-width:2px,color:#000
    style When fill:#fff9c4,stroke:#f57f17,stroke-width:2px,color:#000
    style Then fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px,color:#000
    style G1 fill:#e1f5ff,stroke:#01579b,stroke-width:1px,color:#000
    style G2 fill:#e1f5ff,stroke:#01579b,stroke-width:1px,color:#000
    style W1 fill:#fff9c4,stroke:#f57f17,stroke-width:1px,color:#000
    style W2 fill:#fff9c4,stroke:#f57f17,stroke-width:1px,color:#000
    style T1 fill:#c8e6c9,stroke:#2e7d32,stroke-width:1px,color:#000
    style T2 fill:#c8e6c9,stroke:#2e7d32,stroke-width:1px,color:#000
    style T3 fill:#c8e6c9,stroke:#2e7d32,stroke-width:1px,color:#000
```

**Key Testing Points:**
- Mock external dependencies (repository, Kafka)
- Verify business logic (price calculation, status transitions)
- Confirm side effects (database saves, event publishing)

---

## Test Scenario Complexity Levels

```mermaid
graph TD
    subgraph Easy["EASY (10 min)"]
        E1[Scenario 1: Create Order with Single Item<br/>• Simple test data<br/>• Basic assertions<br/>• Single mock configuration]
    end
    
    subgraph Medium["MEDIUM (15 min)"]
        M1[Scenario 2: Create Order with Multiple Items<br/>• Complex test data 3 items<br/>• Total calculation verification<br/>• Multiple assertions]
        M2[Scenario 3: Get Order By ID - Not Found<br/>• Exception testing<br/>• assertThrows usage<br/>• Error message verification]
        M3[Scenario 4: Update Order Status<br/>• State change testing<br/>• Verify method calls<br/>• Mock configuration for existing data]
        M1 --> M2
        M2 --> M3
    end
    
    subgraph Advanced["ADVANCED (20 min)"]
        A1[Scenario 5: Get Orders By Customer ID<br/>• Collection testing<br/>• Multiple object mapping<br/>• Complex mock setup]
        A2[Scenario 6: Create Order with Zero Quantity<br/>• Boundary testing<br/>• Business rule validation<br/>• Edge case handling]
        A3[Scenario 7: Negative Price<br/>• Invalid input testing<br/>• Business logic verification<br/>• Refund/credit scenario]
        A1 --> A2
        A2 --> A3
    end
    
    Easy --> Medium
    Medium --> Advanced
    
    style Easy fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px,color:#000
    style Medium fill:#fff9c4,stroke:#f57f17,stroke-width:2px,color:#000
    style Advanced fill:#ffccbc,stroke:#d84315,stroke-width:2px,color:#000
    style E1 fill:#c8e6c9,stroke:#2e7d32,stroke-width:1px,color:#000
    style M1 fill:#fff9c4,stroke:#f57f17,stroke-width:1px,color:#000
    style M2 fill:#fff9c4,stroke:#f57f17,stroke-width:1px,color:#000
    style M3 fill:#fff9c4,stroke:#f57f17,stroke-width:1px,color:#000
    style A1 fill:#ffccbc,stroke:#d84315,stroke-width:1px,color:#000
    style A2 fill:#ffccbc,stroke:#d84315,stroke-width:1px,color:#000
    style A3 fill:#ffccbc,stroke:#d84315,stroke-width:1px,color:#000
```

**Progressive Learning Path:**
- Start with simple scenarios to build confidence
- Progress to medium complexity for real-world patterns
- Challenge with advanced scenarios for edge cases

---

## Mocking Strategy Diagram

```mermaid
graph TD
    SUT[OrderServiceImpl<br/>Class Under Test]
    
    subgraph Mocks["Mocked Dependencies"]
        Repo[OrderRepository<br/>MOCKED]
        Kafka[KafkaTemplate<br/>MOCKED]
    end
    
    subgraph RepoBehavior["OrderRepository Mock Behavior"]
        R1[save Order → returns saved order]
        R2[findById UUID → returns Optional]
    end
    
    subgraph KafkaBehavior["KafkaTemplate Mock Behavior"]
        K1[send topic, event → void]
        K2[Verify:<br/>- Called once<br/>- Correct topic<br/>- Correct event]
    end
    
    SUT --> Repo
    SUT --> Kafka
    Repo --> RepoBehavior
    Kafka --> KafkaBehavior
    
    Why[WHY MOCK?<br/>✓ Isolate unit under test<br/>✓ Control test data<br/>✓ Verify interactions<br/>✓ Fast execution no real DB/Kafka<br/>✓ Deterministic results]
    
    style SUT fill:#e1f5ff,stroke:#01579b,stroke-width:3px,color:#000
    style Mocks fill:#fff9c4,stroke:#f57f17,stroke-width:2px,color:#000
    style RepoBehavior fill:#f3e5f5,stroke:#4a148c,stroke-width:2px,color:#000
    style KafkaBehavior fill:#f3e5f5,stroke:#4a148c,stroke-width:2px,color:#000
    style Why fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px,color:#000
    style Repo fill:#fff9c4,stroke:#f57f17,stroke-width:1px,color:#000
    style Kafka fill:#fff9c4,stroke:#f57f17,stroke-width:1px,color:#000
    style R1 fill:#f3e5f5,stroke:#4a148c,stroke-width:1px,color:#000
    style R2 fill:#f3e5f5,stroke:#4a148c,stroke-width:1px,color:#000
    style K1 fill:#f3e5f5,stroke:#4a148c,stroke-width:1px,color:#000
    style K2 fill:#f3e5f5,stroke:#4a148c,stroke-width:1px,color:#000
```

**Mocking Best Practices:**
- Mock external dependencies only (databases, message queues, external APIs)
- Keep business logic in the real implementation
- Verify both return values and side effects
- Use argument captors for complex verification

---

## Test Execution Flow

```mermaid
flowchart TD
    Start([Workshop Start])
    
    Step1[Step 1: UNDERSTAND 5 min<br/>• Review GWT pattern<br/>• Study OrderServiceImpl<br/>• Identify business rules]
    
    Step2[Step 2: PREPARE PROMPT 2 min/scenario<br/>• Copy prompt template<br/>• Fill in scenario details<br/>• Add code context]
    
    Step3[Step 3: GENERATE TEST 1 min/scenario<br/>• Paste prompt to AI<br/>• Review generated code<br/>• Check for fake assertions]
    
    Step4[Step 4: IMPLEMENT 2 min/scenario<br/>• Copy test to IDE<br/>• Add necessary imports<br/>• Fix any compilation errors]
    
    Step5[Step 5: RUN & VERIFY 1 min/scenario<br/>• Execute test<br/>• Check results<br/>• Fix failures<br/>• Verify real logic tested]
    
    Step6[Step 6: REVIEW 5 min<br/>• Discuss findings<br/>• Share learnings<br/>• Identify improvements]
    
    End([Workshop Complete<br/>30-45 minutes])
    
    Start --> Step1
    Step1 --> Step2
    Step2 --> Step3
    Step3 --> Step4
    Step4 --> Step5
    Step5 --> Step6
    Step6 --> End
    
    style Start fill:#f3e5f5,stroke:#4a148c,stroke-width:2px,color:#000
    style Step1 fill:#e1f5ff,stroke:#01579b,stroke-width:2px,color:#000
    style Step2 fill:#fff9c4,stroke:#f57f17,stroke-width:2px,color:#000
    style Step3 fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px,color:#000
    style Step4 fill:#ffccbc,stroke:#d84315,stroke-width:2px,color:#000
    style Step5 fill:#f3e5f5,stroke:#4a148c,stroke-width:2px,color:#000
    style Step6 fill:#e1f5ff,stroke:#01579b,stroke-width:2px,color:#000
    style End fill:#c8e6c9,stroke:#2e7d32,stroke-width:3px,color:#000
```

**Workshop Workflow:**
1. **Understand** - Learn the concepts and codebase
2. **Prepare** - Craft effective AI prompts
3. **Generate** - Use AI to create test code
4. **Implement** - Integrate tests into project
5. **Verify** - Run and validate tests
6. **Review** - Reflect and improve

---

## AAA vs GWT Comparison

```mermaid
graph LR
    subgraph AAA["AAA (Technical Focus)"]
        A1[ARRANGE<br/>• Set up test data<br/>• Configure mocks<br/>• Prepare system]
        A2[ACT<br/>• Execute method<br/>• Call function<br/>• Trigger behavior]
        A3[ASSERT<br/>• Verify results<br/>• Check state<br/>• Validate behavior]
        A1 --> A2
        A2 --> A3
    end
    
    subgraph GWT["GWT (Business Focus)"]
        G1[GIVEN<br/>• Business context<br/>• Initial state<br/>• Preconditions]
        G2[WHEN<br/>• User action<br/>• System event<br/>• Business trigger]
        G3[THEN<br/>• Expected outcome<br/>• Business result<br/>• Observable effect]
        G1 --> G2
        G2 --> G3
    end
    
    A1 -.≈.-> G1
    A2 -.≈.-> G2
    A3 -.≈.-> G3
    
    Usage[WHEN TO USE:<br/>AAA: Internal team testing, technical docs<br/>GWT: Stakeholder communication, BDD, acceptance criteria<br/><br/>BOTH ARE VALID! Choose based on your audience.]
    
    style AAA fill:#e1f5ff,stroke:#01579b,stroke-width:2px,color:#000
    style GWT fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px,color:#000
    style Usage fill:#fff9c4,stroke:#f57f17,stroke-width:2px,color:#000
    style A1 fill:#e1f5ff,stroke:#01579b,stroke-width:1px,color:#000
    style A2 fill:#e1f5ff,stroke:#01579b,stroke-width:1px,color:#000
    style A3 fill:#e1f5ff,stroke:#01579b,stroke-width:1px,color:#000
    style G1 fill:#c8e6c9,stroke:#2e7d32,stroke-width:1px,color:#000
    style G2 fill:#c8e6c9,stroke:#2e7d32,stroke-width:1px,color:#000
    style G3 fill:#c8e6c9,stroke:#2e7d32,stroke-width:1px,color:#000
```

**Pattern Selection Guide:**
- **AAA (Arrange-Act-Assert)**: Technical teams, unit tests, implementation details
- **GWT (Given-When-Then)**: Business stakeholders, BDD, user stories, acceptance criteria
- Both patterns are equivalent in structure, differing mainly in terminology and audience

---

## Success Metrics Dashboard

```mermaid
graph TD
    subgraph Metrics["TEST QUALITY METRICS"]
        Coverage[Code Coverage<br/>Target: > 80%<br/>████████████████████████████████████░░░░░░░░░░ 80%]
        
        Scenarios[Test Scenarios Completed<br/>Target: 7/7<br/>✓ Scenario 1: Single Item<br/>✓ Scenario 2: Multiple Items<br/>✓ Scenario 3: Not Found Exception<br/>✓ Scenario 4: Status Update<br/>✓ Scenario 5: Customer Orders<br/>✓ Scenario 6: Zero Quantity<br/>✓ Scenario 7: Negative Price]
        
        Quality[Test Quality Checklist<br/>✓ Tests follow GWT pattern<br/>✓ No fake/trivial assertions<br/>✓ Proper mocking used<br/>✓ Multiple assertions per test<br/>✓ Descriptive test names<br/>✓ Edge cases covered<br/>✓ Verify calls included<br/>✓ All tests pass]
    end
    
    Coverage --> Scenarios
    Scenarios --> Quality
    
    style Metrics fill:#f3e5f5,stroke:#4a148c,stroke-width:2px,color:#000
    style Coverage fill:#e1f5ff,stroke:#01579b,stroke-width:2px,color:#000
    style Scenarios fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px,color:#000
    style Quality fill:#fff9c4,stroke:#f57f17,stroke-width:2px,color:#000
```

**Quality Indicators:**
- **Coverage**: Aim for >80% line coverage
- **Scenarios**: Complete all 7 test scenarios
- **Quality**: Follow best practices checklist

---

## Workshop Timeline

```mermaid
gantt
    title Workshop Timeline (30-45 minutes)
    dateFormat mm
    axisFormat %M min
    
    section Setup
    Understand Concepts           :00, 5m
    
    section Easy
    Scenario 1 Single Item        :05, 5m
    
    section Medium
    Scenario 2 Multiple Items     :10, 5m
    Scenario 3 Not Found          :15, 5m
    Scenario 4 Status Update      :20, 5m
    
    section Advanced
    Scenario 5 Customer Orders    :25, 7m
    Scenario 6 Zero Quantity      :32, 5m
    Scenario 7 Negative Price     :37, 5m
    
    section Wrap-up
    Review & Discussion           :42, 5m
```

**Time Management:**
- **Setup (5 min)**: Understand concepts and codebase
- **Easy (5 min)**: Build confidence with simple scenario
- **Medium (15 min)**: Practice with realistic scenarios
- **Advanced (17 min)**: Challenge with edge cases
- **Wrap-up (5 min)**: Review and discuss learnings

---

## Usage Notes

**For Workshop Facilitators:**
- Use these diagrams during presentation to explain concepts visually
- Reference specific diagrams when introducing each workshop phase
- Display the timeline to keep participants on track

**For Participants:**
- Refer to these diagrams when writing tests
- Use the GWT pattern flow as a mental model
- Check the success metrics to validate your work

**Technical Notes:**
- All diagrams use Mermaid syntax and render automatically on GitHub
- Text is styled in black for optimal readability
- Color coding indicates different phases and complexity levels

---

*Created for: PCC Workshop - Microservices Unit Testing*  
*Service Focus: Order Service (OrderServiceImpl)*  
*Last Updated: 2026-04-16 14:31:00 +0700*