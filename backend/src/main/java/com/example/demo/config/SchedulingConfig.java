package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the scheduled order-timeout job (see OrderTimeoutJob). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
