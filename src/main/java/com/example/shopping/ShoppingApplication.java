package com.example.shopping;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShoppingApplication {

	public static void main(String[] args) {
		configureBusinessTimeZone();
		SpringApplication.run(ShoppingApplication.class, args);
	}

	static void configureBusinessTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Seoul")));
	}

}
