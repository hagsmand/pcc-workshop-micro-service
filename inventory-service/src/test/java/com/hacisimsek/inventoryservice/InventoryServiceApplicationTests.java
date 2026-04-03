package com.hacisimsek.inventoryservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = InventoryServiceApplicationTests.TestConfig.class)
@ActiveProfiles("test")
class InventoryServiceApplicationTests {

	@Test
	void contextLoads() {
		// This test verifies that the Spring application context loads successfully
		// with minimal configuration for testing purposes
	}

	@Configuration
	static class TestConfig {
		// Minimal test configuration - no beans needed for context load test
	}

}

// Made with Bob
