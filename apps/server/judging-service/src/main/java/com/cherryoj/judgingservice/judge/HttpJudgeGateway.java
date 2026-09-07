package com.cherryoj.judgingservice.judge;

import com.cherryoj.judgingservice.config.JudgingProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class HttpJudgeGateway implements JudgeGateway {
    // Up to 1000 cases include 4 KiB output + 8 KiB stderr + diff. JSON escaping can
    // multiply those bytes by six. These private fields are discarded by JudgeResult.
    private static final int MAX_RESULT_BYTES = 96 * 1024 * 1024;
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
        return judge(endpointRef,request,traceId,properties.judgeTimeout());
    }

    @Override
    public JudgeResult judge(String endpointRef, JudgeRequest request, String traceId, java.time.Duration budget) throws JudgeCallException {
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> future=null;
        try {
            URI base = URI.create(endpointRef);
            if (!"http".equals(base.getScheme()) && !"https".equals(base.getScheme())) {
                throw new JudgeCallException("JUDGE_ENDPOINT_INVALID");
            }
            URI endpoint = base.resolve(base.getPath().endsWith("/") ? "judge" : base.getPath() + "/judge");
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(budget)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request), StandardCharsets.UTF_8));
            if (traceId != null && !traceId.isBlank()) builder.header("traceparent", traceId);
            future=client.sendAsync(builder.build(), ignored -> new com.cherryoj.judgingservice.http.LimitedHttpBody(MAX_RESULT_BYTES));
            var response=future.get(budget.toNanos(),java.util.concurrent.TimeUnit.NANOSECONDS);
            if(response.statusCode()!=200) throw new JudgeCallException("JUDGE_HTTP_"+response.statusCode());
            JudgeResult result=json.readValue(response.body(),JudgeResult.class);
            if(result.verdict()==null || result.environmentFingerprint()==null) throw new JudgeCallException("JUDGE_RESPONSE_INVALID");
            return result;
        }
        catch (JudgeCallException error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new JudgeCallException("JUDGE_CALL_INTERRUPTED", error);
        }
        catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | RuntimeException error) {
            throw new JudgeCallException("JUDGE_CALL_FAILED", error);
        } finally { if(future!=null && !future.isDone()) future.cancel(true); }
    }
}
