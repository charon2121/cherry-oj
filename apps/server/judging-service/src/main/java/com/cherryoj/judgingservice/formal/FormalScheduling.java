package com.cherryoj.judgingservice.formal;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration(proxyBeanMethods=false) @EnableScheduling
class FormalScheduling {
    @Bean org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler formalTaskScheduler() {
        var scheduler=new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); scheduler.setThreadNamePrefix("formal-scheduler-"); return scheduler;
    }
    @Bean org.springframework.kafka.listener.CommonErrorHandler formalConsumerErrorHandler() {
        return new org.springframework.kafka.listener.DefaultErrorHandler(
                new org.springframework.util.backoff.FixedBackOff(1000L,org.springframework.util.backoff.FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
