package com.cherryoj.judgingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JudgingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JudgingServiceApplication.class, args);
    }

}
