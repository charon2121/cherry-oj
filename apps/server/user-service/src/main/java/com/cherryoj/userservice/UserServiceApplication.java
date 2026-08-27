package com.cherryoj.userservice;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UserServiceApplication {

    public static void main(String[] args) {
        boolean bootstrap = Arrays.asList(args).contains("--cherry.auth.mode=bootstrap");
        SpringApplication application = new SpringApplication(UserServiceApplication.class);
        if (bootstrap) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        ConfigurableApplicationContext context = application.run(args);
        if (bootstrap) {
            System.exit(SpringApplication.exit(context));
        }
    }

}
