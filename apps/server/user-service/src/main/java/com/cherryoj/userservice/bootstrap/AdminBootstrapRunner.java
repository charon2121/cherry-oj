package com.cherryoj.userservice.bootstrap;

import com.cherryoj.userservice.application.UserAdministrationService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "bootstrap")
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserAdministrationService users;
    private final Environment environment;

    public AdminBootstrapRunner(UserAdministrationService users, Environment environment) {
        this.users = users;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) throws IOException {
        String username = environment.getProperty("cherry.auth.bootstrap-username");
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("--cherry.auth.bootstrap-username is required");
        }
        char[] password = readPassword();
        try {
            users.bootstrapAdmin(username, new String(password));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static char[] readPassword() throws IOException {
        if (System.console() != null) {
            char[] value = System.console().readPassword("Initial administrator password: ");
            if (value == null) {
                throw new IllegalStateException("password input was cancelled");
            }
            return value;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String value = reader.readLine();
        if (value == null) {
            throw new IllegalStateException("administrator password must be provided on standard input");
        }
        return value.toCharArray();
    }
}
