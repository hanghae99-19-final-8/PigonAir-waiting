package com.example.waitingflow.service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.waitingflow.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserQueueService {

	private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

	@Value("${scheduler.enabled}")
	private Boolean scheduling = false;

	private final String USER_QUEUE_WAIT_KEY = "users:queue:%s:wait";
	private final String USER_QUEUE_WAIT_KEY_FOR_SCAN = "users:queue:*:wait";
	private final String USER_QUEUE_PROCEED_KEY = "users:queue:%s:proceed";

	// 이전에 생성한 토큰을 저장할 메모리 캐시
	private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

	public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
		// 먼저 등록한 사람이 높은 랭크를 갖도록 redis의 sortedset<userId,unix timestamp> 사용.
		// 등록과 동시에 몇 번째 대기인지 리턴

		long unixTimestamp = Instant.now().getEpochSecond();

		return reactiveRedisTemplate.opsForZSet()
			.add(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString(), unixTimestamp)
			.filter(i -> i)
			.switchIfEmpty(Mono.error(ErrorCode.QUEUE_ALREADY_REGISTERED_USER.build()))
			.flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString()))
			.map(i -> i >= 0 ? i + 1 : i);
	}

	// 진입 가능 여부: wait큐 사용자 제거 - proceed큐 사용자 추가
	public Mono<Long> allowUser(final String queue, final Long count) {
		return reactiveRedisTemplate.opsForZSet().popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count) // value 값이 작은 것을 pop
			.flatMap(member -> reactiveRedisTemplate.opsForZSet()
				.add(USER_QUEUE_PROCEED_KEY.formatted(queue), member.getValue(), Instant.now().getEpochSecond()))
			.count();
	}

	public Mono<Boolean> isAllowed(final String queue, final Long userId) {
		return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
			.defaultIfEmpty(-1L)
			.map(rank -> rank >= 0);
	}

	public Mono<Boolean> isAllowedByToken(final String queue, final Long userId,final Long flightId, final String token) {
		return generateToken(queue, userId,flightId)
			.map(genToken -> genToken.equals(token));
	}

	public Mono<Long> getRank(final String queue, final Long userId) {
		return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
			.defaultIfEmpty(-1L)
			.map(rank -> rank >= 0 ? rank + 1 : rank);
	}


	public Mono<String> generateToken(final String queue, final Long userId,final Long flightId) {
		// 캐시에서 해당 토큰을 찾아 반환
		String cachedToken = tokenCache.get(generateCacheKey(queue, userId,flightId));
		if (cachedToken != null) {
			return Mono.just(cachedToken);
		}

		// 캐시에 없으면 새로운 토큰을 생성하고 캐시에 저장
		return generateTokenAndCache(queue, userId, flightId);
	}
	private Mono<String> generateTokenAndCache(final String queue, final Long userId,final Long flightId) {
		return Mono.fromCallable(() -> {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			String input = "user-queue-%s-%d-%d".formatted(queue, userId,flightId);
			byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

			StringBuilder hexString = new StringBuilder();
			for (byte aByte: encodedHash) {
				hexString.append(String.format("%02x", aByte));
			}

			// 생성한 토큰을 캐시에 저장
			tokenCache.put(generateCacheKey(queue, userId,flightId), hexString.toString());

			return hexString.toString();
		}).subscribeOn(Schedulers.boundedElastic());
	}
	private String generateCacheKey(String queue, Long userId,Long flightId) {
		return queue + "-" + userId + "-" +flightId;
	}

	@Scheduled(initialDelay = 5000, fixedDelay = 1000) // 서버가 시작되고 5초후 1초 주기로 실행
	public void scheduleAllowUser() {
		if(!scheduling) {
			log.info("passed scheduling");
			return;
		}

		log.info("called scheduling...");

		Long maxAllowUserCount = 60L;


		// 사용자 허용 코드
		reactiveRedisTemplate.scan(ScanOptions.scanOptions()
				.match(USER_QUEUE_WAIT_KEY_FOR_SCAN)
				.count(60)
				.build())
			.map(key -> key.split(":")[2])
			.flatMap(queue -> allowUser(queue, maxAllowUserCount)
				.map(allowed -> Tuples.of(queue, allowed)))
			.doOnNext(tuple -> log.info("Tried %d and allowed %d members of %s queues".formatted(maxAllowUserCount, tuple.getT2(), tuple.getT1())))
			.subscribe(key -> System.out.println("스캔된 키: " + key));
	}
}
