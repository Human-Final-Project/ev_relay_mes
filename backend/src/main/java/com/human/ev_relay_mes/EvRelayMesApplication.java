package com.human.ev_relay_mes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class EvRelayMesApplication {

	public static void main(String[] args) {
		SpringApplication.run(EvRelayMesApplication.class, args);
	}

}
