package com.cherryoj.judgingservice.judge;

import com.cherryoj.judgingservice.config.JudgingProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class HttpJudgeGateway implements JudgeGateway {
    private static final int MAX_RESULT_BYTES = 1_048_576;
    private final HttpClient client;
    private final ObjectMapper json;
    private final JudgingProperties properties;

    public HttpJudgeGateway(HttpClient client, ObjectMapper json, JudgingProperties properties) {
        this.client = client;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public JudgeResult judge(String endpointRef, JudgeRequest request, String traceId) throws JudgeCallException {
        try {
            URI base = URI.create(endpointRef);
            if (!"http".equals(base.getScheme()) && !"https".equals(base.getScheme())) {
                throw new JudgeCallException("JUDGE_ENDPOINT_INVALID");
            }
            URI endpoint = base.resolve(base.getPath().endsWith("/") ? "judge" : base.getPath() + "/judge");
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(properties.judgeTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request), StandardCharsets.UTF_8));
            if (traceId != null && !traceId.isBlank()) builder.header("traceparent", traceId);
            HttpResponse<java.io.InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESULT_BYTES + 1);
                if (bytes.length > MAX_RESULT_BYTES) throw new JudgeCallException("JUDGE_RESPONSE_TOO_LARGE");
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new JudgeCallException("JUDGE_HTTP_" + response.statusCode());
                }
                JudgeResult result = json.readValue(bytes, JudgeResult.class);
                if (result.verdict() == null || result.environmentFingerprint() == null) {
                    throw new JudgeCallException("JUDGE_RESPONSE_INVALID");
                }
                return result;
            }
        }
        catch (JudgeCallException error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new JudgeCallException("JUDGE_CALL_INTERRUPTED", error);
        }
        catch (IOException | RuntimeException error) {
            throw new JudgeCallException("JUDGE_CALL_FAILED", error);
        }
    }
}
