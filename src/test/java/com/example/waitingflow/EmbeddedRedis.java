package com.example.waitingflow;

import java.io.IOException;

import org.springframework.boot.test.context.TestConfiguration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import redis.embedded.RedisServer;

@TestConfiguration
public class EmbeddedRedis {

	private final RedisServer redisServer;

	public EmbeddedRedis() throws IOException {
		this.redisServer = new RedisServer(63790);
	}

	@PostConstruct
	public void start() throws IOException{
		this.redisServer.start();
	}

	@PreDestroy
	public void stop() throws IOException{
		this.redisServer.stop();
	}
}

