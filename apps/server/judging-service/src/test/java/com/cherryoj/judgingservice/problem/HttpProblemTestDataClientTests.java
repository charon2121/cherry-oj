package com.cherryoj.judgingservice.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.identity.token.CallToken;
import com.cherryoj.identity.token.CallTokenSigner;
import com.cherryoj.identity.token.PublicKeyDirectory;
import com.cherryoj.identity.token.ServiceKeys;
import com.cherryoj.identity.token.ServiceNames;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpProblemTestDataClientTests {
    private static final String PROBLEM = "019c8e42-7f70-7000-8000-000000000010";
    private static final String DIGEST = "6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4";

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private int status = 200;
    private String body =
            "{\"location\":\"/data/problems/p\",\"digest\":\"" + DIGEST + "\",\"testcaseCount\":2,\"totalBytes\":16}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes();
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpProblemTestDataClient client() {
        return new HttpProblemTestDataClient(
                new ProblemServiceProperties(
                        "http://127.0.0.1:" + server.getAddress().getPort()),
                JsonMapper.builder()
                        .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build(),
                new CallTokenSigner(
                        ServiceNames.JUDGING,
                        ServiceKeys.privateKey("classpath:test-service-identity/judging-service-private.pem"),
                        Clock.systemUTC()));
    }

    @Test
    void readsTheCurrentAddressWithACallTokenForProblemService() {
        var data = client().current(PROBLEM, null);
        assertThat(data.location()).isEqualTo("/data/problems/p");
        assertThat(data.digest()).isEqualTo(DIGEST);
        assertThat(data.testcaseCount()).isEqualTo(2);
        assertThat(path.get()).isEqualTo("/internal/judging/problems/" + PROBLEM + "/test-data");
        var call = CallToken.verify(
                authorization.get().substring("Bearer ".length()),
                PublicKeyDirectory.load("classpath:test-service-identity"),
                ServiceNames.PROBLEM,
                Instant.now());
        assertThat(call.caller()).isEqualTo(ServiceNames.JUDGING);
        assertThat(call.user()).isEmpty();
    }

    @Test
    void missingDataAndFailuresAreDistinguishedAndNeverReturnHalfAnAnswer() {
        status = 404;
        body = "{}";
        assertThatThrownBy(() -> client().current(PROBLEM, null)).hasMessage("TEST_DATA_MISSING");
        status = 503;
        assertThatThrownBy(() -> client().current(PROBLEM, null)).hasMessage("TEST_DATA_UNAVAILABLE");
        status = 200;
        body = "{\"location\":\"/x\",\"digest\":\"bad\",\"testcaseCount\":2,\"totalBytes\":1}";
        assertThatThrownBy(() -> client().current(PROBLEM, null)).hasMessage("TEST_DATA_INVALID");
        body = "{\"location\":\"/x\",\"digest\":\"" + DIGEST + "\",\"testcaseCount\":0,\"totalBytes\":1}";
        assertThatThrownBy(() -> client().current(PROBLEM, null)).hasMessage("TEST_DATA_INVALID");
        body = "not json";
        assertThatThrownBy(() -> client().current(PROBLEM, null)).hasMessage("TEST_DATA_UNAVAILABLE");
    }

    @Test
    void aRejectedCallIsReportedAsSuch() {
        body = "{}";
        for (int rejected : new int[] {401, 403}) {
            status = rejected;
            assertThatThrownBy(() -> client().current(PROBLEM, null)).hasMessage("CALL_REJECTED");
        }
    }
}
