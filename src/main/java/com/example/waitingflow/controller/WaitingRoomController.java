package com.example.waitingflow.controller;

import org.springframework.http.HttpCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;

import com.example.waitingflow.service.UserQueueService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Controller
public class WaitingRoomController {

	private final UserQueueService userQueueService;

	@GetMapping("/waiting-room")
	public Mono<Rendering> waitingRoomPage(@RequestParam(name = "queue", defaultValue = "default") String queue,
		@RequestParam(name = "user_id") Long userId,
		@RequestParam(name = "redirect_url") String redirectUrl,
		ServerWebExchange exchange) {
		String key = "user-queue-%s-token".formatted(queue);
		HttpCookie cookieValue = exchange.getRequest().getCookies().getFirst(key);
		String token = cookieValue == null ? "" : cookieValue.getValue();

		// 1. 입장이 허용되어 page redirect(이동)이 가능한지?
		return userQueueService.isAllowedByToken(queue, userId, token)
			.filter(allowed -> allowed)
			.flatMap(allowed -> Mono.just(Rendering.redirectTo(redirectUrl).build()))
			.switchIfEmpty(
				// 대기 등록. 이미 있으면 웹 페이지에 필요한 데이터 전달
				userQueueService.registerWaitQueue(queue, userId)
					.onErrorResume(ex ->userQueueService.getRank(queue, userId))
					.map(rank -> Rendering.view("waiting-room.html")
						.modelAttribute("number", rank)
						.modelAttribute("userId", userId)
						.modelAttribute("queue", queue)
						.build())
			);
	}
}

