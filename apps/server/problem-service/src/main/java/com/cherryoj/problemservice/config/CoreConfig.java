package com.cherryoj.problemservice.config;

import com.cherryoj.problemservice.domain.UuidV7;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    UuidV7 uuidV7(Clock clock, SecureRandom secureRandom) {
        return new UuidV7(clock, secureRandom);
    }
}
