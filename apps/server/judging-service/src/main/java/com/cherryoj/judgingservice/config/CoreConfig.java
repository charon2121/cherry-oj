package com.cherryoj.judgingservice.config;

import com.cherryoj.judgingservice.domain.UuidV7;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean SecureRandom secureRandom() { return new SecureRandom(); }
    @Bean UuidV7 uuidV7(Clock clock, SecureRandom random) { return new UuidV7(clock, random); }
    @Bean HttpClient httpClient() { return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(); }
}
