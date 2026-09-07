package com.cherryoj.submissionservice.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration(proxyBeanMethods=false) @EnableScheduling
class SubmissionScheduling {
    @org.springframework.context.annotation.Bean
    org.springframework.kafka.listener.CommonErrorHandler submissionConsumerErrorHandler() {
        // 数据库或 DLT 暂时不可用时持续重试，不能采用框架默认次数后丢弃记录。
        return new org.springframework.kafka.listener.DefaultErrorHandler(
                new org.springframework.util.backoff.FixedBackOff(1000L, org.springframework.util.backoff.FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
