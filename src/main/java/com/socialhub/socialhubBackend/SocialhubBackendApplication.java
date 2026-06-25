package com.socialhub.socialhubBackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point.
 *
 * <p>{@code @ConfigurationPropertiesScan} discovers {@code @ConfigurationProperties}
 * records (e.g. {@code config.AppProperties}) so externalized config binds automatically.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SocialhubBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocialhubBackendApplication.class, args);
	}

}
