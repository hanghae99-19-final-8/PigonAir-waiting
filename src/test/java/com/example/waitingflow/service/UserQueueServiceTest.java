package com.example.waitingflow.service;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;

import com.example.waitingflow.EmbeddedRedis;
import com.example.waitingflow.exception.ApplicationException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@Import(EmbeddedRedis.class)
@ActiveProfiles("test")
class UserQueueServiceTest {

	@Autowired
	private UserQueueService userQueueService;

	@Autowired
	private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
	@BeforeEach
	public void beforeEach() {
		ReactiveRedisConnection redisConnection = reactiveRedisTemplate.getConnectionFactory().getReactiveConnection();
		redisConnection.serverCommands().flushAll().subscribe();
	}

	@Test
	@Rollback(value = false)
	void registerWaitQueue() {
		StepVerifier.create(userQueueService.registerWaitQueue("default", 100L))
			.expectNext(1L)
			.verifyComplete();
		// StepVerifier.create(Mono.delay(Duration.ofSeconds(1)))
		// 	.consumeNextWith(delay -> System.out.println("1초 딜레이 발생"))
		// 	.verifyComplete();
		StepVerifier.create(userQueueService.registerWaitQueue("default", 101L))
			.expectNext(2L)
			.verifyComplete();
		// StepVerifier.create(Mono.delay(Duration.ofSeconds(1)))
		// 	.consumeNextWith(delay -> System.out.println("1초 딜레이 발생"))
		// 	.verifyComplete();
		StepVerifier.create(userQueueService.registerWaitQueue("default", 102L))
			.expectNext(3L)
			.verifyComplete();


	}

	@Test
	void alreadyRegisterWaitQueue() {
		StepVerifier.create(userQueueService.registerWaitQueue("default", 100L))
			.expectNext(1L)
			.verifyComplete();

		StepVerifier.create(userQueueService.registerWaitQueue("default", 100L))
			.expectError(ApplicationException.class)
			.verify();
	}

	@Test
	void emptyAllowUser() {
		StepVerifier.create(userQueueService.allowUser("default", 3L))
			.expectNext(0L)
			.verifyComplete();
	}

	@Test
	void allowUser() {
		StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
				.then(userQueueService.registerWaitQueue("default", 101L))
				.then(userQueueService.registerWaitQueue("default", 102L))
				.then(userQueueService.allowUser("default", 2L)))
			.expectNext(2L)
			.verifyComplete();
	}

	@Test
	void allowUser2() {
		StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
				.then(userQueueService.registerWaitQueue("default", 101L))
				.then(userQueueService.registerWaitQueue("default", 102L))
				.then(userQueueService.allowUser("default", 5L)))
			.expectNext(3L)
			.verifyComplete();
	}

	@Test
	void allowUserAfterRegisterWaitQueu() {
		StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
				.then(userQueueService.registerWaitQueue("default", 101L))
				.then(userQueueService.registerWaitQueue("default", 102L))
				.then(userQueueService.allowUser("default", 3L))
				.then(userQueueService.registerWaitQueue("default", 200L)))
			.expectNext(1L)
			.verifyComplete();
	}

	@Test
	void isNotAllowed() {
		StepVerifier.create(userQueueService.isAllowed("default", 100L))
			.expectNext(false)
			.verifyComplete();
	}

	@Test
	void isNotAllowed2() {
		StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
				.then(userQueueService.allowUser("default", 3L))
				.then(userQueueService.isAllowed("default", 101L)))
			.expectNext(false)
			.verifyComplete();
	}

	@Test
	void isAllowed() {
		StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
				.then(userQueueService.allowUser("default", 3L))
				.then(userQueueService.isAllowed("default", 100L)))
			.expectNext(true)
			.verifyComplete();
	}

	@Test
	void getRank() {
		StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
				.then(userQueueService.getRank("default", 100L)))
			.expectNext(1L)
			.verifyComplete();

		StepVerifier.create(userQueueService.registerWaitQueue("default", 101L)
				.then(userQueueService.getRank("default", 101L)))
			.expectNext(2L)
			.verifyComplete();
	}

	@Test
	void emptyRank() {
		StepVerifier.create(userQueueService.getRank("default", 100L))
			.expectNext(-1L)
			.verifyComplete();
	}

	@Test
	void isNotAllowedByToken() {
		StepVerifier.create(userQueueService.isAllowedByToken("default", 100L,1L, ""))
			.expectNext(false)
			.verifyComplete();
	}

	// @Test
	// void isAllowedByToken() {
	// 	StepVerifier.create(userQueueService.isAllowedByToken("default", 100L,1L, "f76ab5578c18752395695a2f536090edf23ad6fa3dc8441d202edc04b0cda18f"))
	// 		.expectNext(true)
	// 		.verifyComplete();
	// }
	//
	// @Test
	// void generateToken() {
	// 	StepVerifier.create(userQueueService.generateToken("default", 100L,1L))
	// 		.expectNext("f76ab5578c18752395695a2f536090edf23ad6fa3dc8441d202edc04b0cda18f")
	// 		.verifyComplete();
	// }
}
