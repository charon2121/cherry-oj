package com.cherryoj.judgingservice.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpProblemTestDataClientTests {
    private static final String PROBLEM = "019c8e42-7f70-7000-8000-000000000010";
    private static final String TOKEN = "t".repeat(40);
    private static final String DIGEST = "6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4";

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private int status = 200;
    private String body = "{\"location\":\"/data/problems/p\",\"digest\":\"" + DIGEST + "\",\"caseCount\":2,\"totalBytes\":16}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes();
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private HttpProblemTestDataClient client(String token) {
        return new HttpProblemTestDataClient(new ProblemServiceProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), token), JsonMapper.builder()
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
    }

    @Test
    void readsTheCurrentAddressWithTheServiceToken() {
        var data = client(TOKEN).current(PROBLEM, null);
        assertThat(data.location()).isEqualTo("/data/problems/p");
        assertThat(data.digest()).isEqualTo(DIGEST);
        assertThat(data.caseCount()).isEqualTo(2);
        assertThat(path.get()).isEqualTo("/internal/judging/problems/" + PROBLEM + "/test-data");
        assertThat(authorization.get()).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void missingDataAndFailuresAreDistinguishedAndNeverReturnHalfAnAnswer() {
        status = 404; body = "{}";
        assertThatThrownBy(() -> client(TOKEN).current(PROBLEM, null)).hasMessage("TEST_DATA_MISSING");
        status = 503;
        assertThatThrownBy(() -> client(TOKEN).current(PROBLEM, null)).hasMessage("TEST_DATA_UNAVAILABLE");
        status = 200; body = "{\"location\":\"/x\",\"digest\":\"bad\",\"caseCount\":2,\"totalBytes\":1}";
        assertThatThrownBy(() -> client(TOKEN).current(PROBLEM, null)).hasMessage("TEST_DATA_INVALID");
        body = "{\"location\":\"/x\",\"digest\":\"" + DIGEST + "\",\"caseCount\":0,\"totalBytes\":1}";
        assertThatThrownBy(() -> client(TOKEN).current(PROBLEM, null)).hasMessage("TEST_DATA_INVALID");
        body = "not json";
        assertThatThrownBy(() -> client(TOKEN).current(PROBLEM, null)).hasMessage("TEST_DATA_UNAVAILABLE");
    }

    @Test
    void withoutATokenNothingIsSent() {
        assertThatThrownBy(() -> client("").current(PROBLEM, null)).hasMessage("PROBLEM_SERVICE_NOT_CONFIGURED");
        assertThat(path.get()).isNull();
    }
}
