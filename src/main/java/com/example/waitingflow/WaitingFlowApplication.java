package com.example.waitingflow;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WaitingFlowApplication {

	public static void main(String[] args) {
		SpringApplication.run(WaitingFlowApplication.class, args);
	}

}
