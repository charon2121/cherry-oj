package com.cherryoj.judgingservice.formal;

import com.cherryoj.serviceidentity.ServiceIdentityException;
import com.cherryoj.serviceidentity.ServiceNames;
import com.cherryoj.serviceidentity.ServiceTokenClient;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpFormalInputClient implements FormalInputClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final FormalProperties properties;
    private final ObjectMapper json;
    private final ServiceTokenClient tokens;

    public HttpFormalInputClient(FormalProperties properties, ObjectMapper json, ServiceTokenClient tokens) {
        this.properties = properties;
        this.json = json;
        this.tokens = tokens;
    }

    public FormalInput get(String id, String trace) {
        UUID.fromString(id);
        String token;
        try {
            token = tokens.tokenFor(ServiceNames.SUBMISSION);
        } catch (ServiceIdentityException error) {
            throw new FormalFailure("JUDGE_INPUT_UNAVAILABLE");
        }
        var builder = HttpRequest.newBuilder(URI.create(
                        properties.submissionUrl().replaceAll("/$", "") + "/internal/judging/judge-inputs/" + id))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .GET();
        if (trace != null) builder.header("traceparent", trace);
        var future = http.sendAsync(
                builder.build(), ignored -> new com.cherryoj.judgingservice.http.LimitedHttpBody(2097152));
        try {
            var response = future.get(6, TimeUnit.SECONDS);
            if (response.statusCode() == 401) tokens.invalidate(ServiceNames.SUBMISSION);
            if (response.statusCode() != 200) throw new FormalFailure("JUDGE_INPUT_UNAVAILABLE");
            return json.readValue(response.body(), FormalInput.class);
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
