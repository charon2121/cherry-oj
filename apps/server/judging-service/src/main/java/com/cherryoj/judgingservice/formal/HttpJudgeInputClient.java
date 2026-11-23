package com.cherryoj.judgingservice.formal;

import com.cherryoj.identity.token.CallTokenSigner;
import com.cherryoj.identity.token.ServiceNames;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpJudgeInputClient implements JudgeInputClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final FormalProperties properties;
    private final ObjectMapper json;
    private final CallTokenSigner calls;

    public HttpJudgeInputClient(FormalProperties properties, ObjectMapper json, CallTokenSigner calls) {
        this.properties = properties;
        this.json = json;
        this.calls = calls;
    }

    @Override
    public JudgeInput get(String submissionId, String traceParent) {
        UUID.fromString(submissionId);
        var builder = HttpRequest.newBuilder(
                        URI.create(properties.submissionUrl().replaceAll("/$", "") + "/internal/judging/judge-inputs/"
                                + submissionId))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", calls.authorization(ServiceNames.SUBMISSION, null))
                .GET();
        if (traceParent != null) builder.header("traceparent", traceParent);
        var future = http.sendAsync(
                builder.build(), ignored -> new com.cherryoj.judgingservice.http.LimitedHttpBody(2097152));
        try {
            var response = future.get(6, TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw new FormalFailure("JUDGE_INPUT_UNAVAILABLE");
            return json.readValue(response.body(), JudgeInput.class);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new FormalFailure("JUDGE_INPUT_INTERRUPTED");
        } catch (Exception error) {
            throw new FormalFailure("JUDGE_INPUT_UNAVAILABLE");
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }
}
