package com.computerstore.computer_store_backend.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
