package com.ticketing.billing;

import com.ticketing.billing.lock.InMemoryLockService;
import com.ticketing.billing.lock.LockService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationTests {

	@TestConfiguration
	static class TestConfig {
		@Bean
		@Primary
		public LockService inMemoryLockService() {
			return new InMemoryLockService();
		}
	}

	@Test
	void contextLoads() {
	}

}
