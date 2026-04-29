# Lab 2: Security Testing and Improvement

## Overview
This lab focuses on identifying and fixing security vulnerabilities in the microservices architecture, as well as testing for race condition issues in concurrent API requests.

---

## Lab 2.1: Security Vulnerability Detection and Quick Fix

### Objective
Learn to identify security vulnerabilities using Bob's AI-powered code analysis and apply automated fixes.

### Prerequisites
- Bob AI assistant installed and configured
- Git repository cloned
- Access to Bob Findings panel

### Steps

#### 1. Clone Branch A
```bash
git checkout branch-a
```
> **Note**: Replace `branch-a` with the actual branch name containing security vulnerabilities.

#### 2. Review Bob Findings
1. Open the **Bob Findings** panel in your IDE
2. Navigate to the security findings section
3. Look for identified vulnerabilities such as:
   - Hardcoded API keys
   - Exposed credentials
   - Insecure configurations
   - Sensitive data in logs

**Expected Findings:**
- API key exposure in configuration files
- Hardcoded credentials
- Security misconfigurations

#### 3. Apply Quick Fix
1. Select a security finding from the Bob Findings panel
2. Click on the **Quick Fix** option
3. Review Bob's suggested fix
4. Accept the fix to automatically remediate the vulnerability

**What Bob Will Fix:**
- Move hardcoded credentials to environment variables
- Replace exposed API keys with secure configuration
- Update security configurations to follow best practices

#### 4. Verify the Fix
1. Review the changes made by Bob
2. Ensure credentials are now externalized
3. Verify that sensitive data is no longer hardcoded

---

## Lab 2.2: Race Condition Testing

### Objective
Create and execute test cases to identify race condition vulnerabilities in concurrent API requests.

### Prerequisites
- Podman or Docker installed
- Maven installed
- Test environment configured

### Part 1: Create Race Condition Test Case

#### 1. Generate Test Case Using Bob
1. Open Bob AI assistant
2. Request test case generation for race condition testing
3. Follow the format specified in `CREATE_TEST_CASE.md` (to be created)

**Example Prompt for Bob:**
```
Create a race condition test case for the inventory service that:
- Simulates multiple concurrent requests to reserve inventory
- Tests if the system properly handles concurrent stock updates
- Validates that inventory quantities remain consistent
```

#### 2. Review Generated Test
The test case should be generated at:
```
inventory-service/src/test/java/com/hacisimsek/inventoryservice/service/InventoryRaceConditionTest.java
```

**Expected Test Structure:**
- Multiple threads making concurrent API calls
- Assertions to verify data consistency
- Validation of inventory quantity after concurrent operations

### Part 2: Execute Race Condition Tests

#### 1. Run the Test Suite
Execute the race condition tests using the provided Podman Compose configuration:

```bash
podman-compose -f podman-compose.race-condition-test.yml up race-condition-tests
```

**What This Does:**
- Starts required services (MongoDB, Redis, Kafka, Zookeeper)
- Builds the common-library and inventory-service
- Executes the `InventoryRaceConditionTest` test suite
- Tests concurrent inventory operations

#### 2. Monitor Test Execution
Watch the console output for:
- Test initialization
- Concurrent request execution
- Test results (PASS/FAIL)
- Any race condition failures

**Expected Result (Spoiler):**
The current version of the API **cannot** handle race conditions properly. You should see test failures indicating:
- Inventory quantity inconsistencies
- Lost updates
- Concurrent modification issues

#### 3. Capture Test Results
Generate a detailed log file of the test execution:

```bash
podman-compose -f podman-compose.race-condition-test.yml logs race-condition-tests > race-condition-test-logs.log
```

#### 4. Analyze Test Results
Open `race-condition-test-logs.log` and review:
- Number of tests executed
- Failed test cases
- Error messages and stack traces
- Race condition indicators

**Key Indicators of Race Conditions:**
- Inventory quantity doesn't match expected value
- Multiple reservations succeed when only one should
- Inconsistent state after concurrent operations

### Part 3: Understanding the Results

#### Why the Tests Fail
The current implementation lacks proper concurrency control mechanisms:
- No pessimistic locking on inventory updates
- No optimistic locking with versioning
- No distributed locking mechanism
- Race conditions in read-modify-write operations

#### Potential Solutions (For Discussion)
1. **Pessimistic Locking**: Use database-level locks
2. **Optimistic Locking**: Implement version-based concurrency control
3. **Distributed Locking**: Use Redis or similar for distributed locks
4. **Atomic Operations**: Use database atomic operations

---

## Cleanup

After completing the lab, clean up the test environment:

```bash
# Stop and remove test containers
podman-compose -f podman-compose.race-condition-test.yml down

# Optional: Clean up test volumes
podman-compose -f podman-compose.race-condition-test.yml down -v
```

Or use the cleanup script:
```bash
./scripts/cleanup-race-test-containers.sh
```

---

## Learning Outcomes

After completing this lab, you should be able to:
- ✅ Identify security vulnerabilities using AI-powered code analysis
- ✅ Apply automated security fixes using Bob AI assistant
- ✅ Create test cases for race condition scenarios
- ✅ Execute concurrent API tests using containerized environments
- ✅ Analyze test results to identify concurrency issues
- ✅ Understand the importance of proper concurrency control

---

## Additional Resources

- [Bob AI Documentation](https://docs.bob.ai)
- [OWASP Security Guidelines](https://owasp.org)
- [Java Concurrency Best Practices](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [Spring Boot Security](https://spring.io/guides/gs/securing-web/)

---

## Troubleshooting

### Common Issues

**Issue**: Podman compose fails to start services
- **Solution**: Ensure Podman is running and you have sufficient resources

**Issue**: Tests timeout or hang
- **Solution**: Check if all dependent services (MongoDB, Kafka, Redis) are healthy

**Issue**: Bob Findings panel is empty
- **Solution**: Ensure you're on the correct branch and Bob has analyzed the codebase

**Issue**: Test logs are not generated
- **Solution**: Ensure the test container has completed execution before running the logs command

---

## Next Steps

After completing this lab:
1. Implement fixes for the identified race conditions
2. Re-run tests to verify fixes
3. Apply security best practices across all microservices
4. Document security improvements and concurrency solutions

---

**Lab Duration**: Approximately 45-60 minutes

**Difficulty Level**: Intermediate

**Last Updated**: 2026-04-29
