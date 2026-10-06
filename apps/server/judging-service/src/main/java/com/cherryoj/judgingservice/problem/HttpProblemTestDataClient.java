package com.cherryoj.judgingservice.problem;

import com.cherryoj.judgingservice.http.LimitedHttpBody;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpProblemTestDataClient implements ProblemTestDataClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ProblemServiceProperties properties;
    private final ObjectMapper json;

    public HttpProblemTestDataClient(ProblemServiceProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    public ProblemTestData current(String problemId, String traceParent) {
        UUID.fromString(problemId);
        if (properties.token().isBlank()) throw new ProblemTestDataException("PROBLEM_SERVICE_NOT_CONFIGURED");
        var builder = HttpRequest.newBuilder(URI.create(properties.url().replaceAll("/$", "")
                        + "/internal/judging/problems/" + problemId + "/test-data"))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + properties.token()).GET();
        if (traceParent != null && traceParent.matches("[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) {
            builder.header("traceparent", traceParent);
        }
        var future = http.sendAsync(builder.build(), ignored -> new LimitedHttpBody(65536));
        try {
            var response = future.get(6, TimeUnit.SECONDS);
            if (response.statusCode() == 404) throw new ProblemTestDataException("TEST_DATA_MISSING");
            if (response.statusCode() != 200) throw new ProblemTestDataException("TEST_DATA_UNAVAILABLE");
            var data = json.readValue(response.body(), ProblemTestData.class);
            if (data.location() == null || data.location().isBlank() || data.digest() == null
                    || !data.digest().matches("[a-f0-9]{64}") || data.caseCount() < 1 || data.caseCount() > 1000) {
                throw new ProblemTestDataException("TEST_DATA_INVALID");
            }
            return data;
        } catch (ProblemTestDataException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProblemTestDataException("TEST_DATA_UNAVAILABLE");
        } catch (Exception error) {
            throw new ProblemTestDataException("TEST_DATA_UNAVAILABLE");
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }
}
