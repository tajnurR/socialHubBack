package com.socialhub.socialhubBackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} jobs (the due-post publisher). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
