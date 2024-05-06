package com.example.waitingflow.controller;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.example.waitingflow.dto.AllowUserResponse;
import com.example.waitingflow.dto.AllowedUserResponse;
import com.example.waitingflow.dto.RankNumberResponse;
import com.example.waitingflow.dto.RegisterUserResponse;
import com.example.waitingflow.service.UserQueueService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class UserQueueController {

	private final UserQueueService userQueueService;

	// 대기 등록
	@PostMapping("")
	public Mono<RegisterUserResponse> registerUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
		@RequestParam(name = "user_id") Long userId) {
		return userQueueService.registerWaitQueue(queue, userId)
			.map(RegisterUserResponse::new);
	}

	// 진입 허용
	@PostMapping("/allow")
	public Mono<AllowUserResponse> allowUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
		@RequestParam(name = "count") Long count) {
		return userQueueService.allowUser(queue, count)
			.map(allowed -> new AllowUserResponse(count, allowed));
	}

	// 허용 가능한 사용자인지 확인
	@GetMapping("/allowed")
	public Mono<AllowedUserResponse> isAllowedUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
		@RequestParam(name = "user_id") Long userId,
		@RequestParam(name = "flight_id") Long flightId,
		@RequestParam(name = "token") String token) {
		return userQueueService.isAllowedByToken(queue, userId,flightId, token)
			.map(AllowedUserResponse::new);
	}

	@GetMapping("/rank")
	public Mono<RankNumberResponse> getRankUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
		@RequestParam(name = "user_id") Long userId) {
		return userQueueService.getRank(queue, userId)
			.map(RankNumberResponse::new);
	}

	@GetMapping("/touch")
	Mono<?> touch(@RequestParam(name = "queue", defaultValue = "default") String queue,
		@RequestParam(name = "user_id") Long userId,
		@RequestParam(name = "flight_id") Long flightId,
		ServerWebExchange exchange) {
		// SeverWebExcange -> HttpServletRequest와 HttpServletResponse를 대체하는 역할
		return Mono.defer(() -> userQueueService.generateToken(queue, userId,flightId))
			.map(token -> {
				exchange.getResponse().addCookie(
					ResponseCookie
						.from("user-queue-%s-token".formatted(queue), token)
						.maxAge(Duration.ofSeconds(300))
						.path("/")
						.build()
				);


				return token;
			});
	}

}

