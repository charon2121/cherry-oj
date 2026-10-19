package com.cherryoj.submissionservice.integration;

import com.cherryoj.serviceidentity.ServiceIdentityException;
import com.cherryoj.serviceidentity.ServiceNames;
import com.cherryoj.serviceidentity.ServiceTokenClient;
import com.cherryoj.submissionservice.api.SubmissionDtos.*;
import com.cherryoj.submissionservice.api.SubmissionException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpSubmissionPrerequisites implements SubmissionPrerequisites {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpSubmissionPrerequisites.class);
    private final String problemUrl, judgingUrl;
    private final ServiceTokenClient tokens;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public HttpSubmissionPrerequisites(
            ObjectMapper json,
            @Value("${cherry.submission.problem-url}") String problemUrl,
            @Value("${cherry.submission.judging-url}") String judgingUrl,
            ServiceTokenClient tokens) {
        this.json = json;
        this.problemUrl = problemUrl;
        this.judgingUrl = judgingUrl;
        this.tokens = tokens;
    }

    public Snapshot snapshot(String id, String language) {
        return post(
                problemUrl,
                "/internal/submission/problem-snapshot",
                ServiceNames.PROBLEM,
                Map.of("problemId", id, "languageId", language),
                Snapshot.class);
    }

    public Profile profile(Snapshot s) {
        return post(
                judgingUrl,
                "/internal/submission/execution-profile",
                ServiceNames.JUDGING,
                Map.of("problemId", s.problemId(), "languageId", s.languageId(), "purpose", "formal"),
                Profile.class);
    }

    private <T> T post(String base, String path, String audience, Object body, Class<T> type) {
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            String token = tokens.tokenFor(audience);
            var request = HttpRequest.newBuilder(URI.create(base.replaceAll("/$", "") + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
                    .build();
            future = http.sendAsync(request, ignored -> new LimitedBody());
            var response = future.get(6, TimeUnit.SECONDS);
            if (response.statusCode() == 404)
                throw new SubmissionException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_AVAILABLE", "题目当前不可提交。");
            if (response.statusCode() == 422)
                throw new SubmissionException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_LANGUAGE", "当前题目不支持这门语言。");
            if (response.statusCode() == 401) tokens.invalidate(audience);
            if (response.statusCode() != 200) throw unavailable(path, "status " + response.statusCode());
            return json.readValue(response.body(), type);
        } catch (SubmissionException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable(path, "interrupted");
        } catch (ServiceIdentityException error) {
            throw unavailable(path, "service identity " + error.reason());
        } catch (Exception error) {
            throw unavailable(path, error.getClass().getSimpleName());
        } finally {
            if (future != null && !future.isDone()) future.cancel(true);
        }
    }
    // 对用户统一说"暂时无法判题"，但运维要能看出是哪个依赖、因为什么失败；不记令牌与请求体。
    private static SubmissionException unavailable(String path, String reason) {
        LOGGER.atWarn()
                .addKeyValue("event", "submission.prerequisite.unavailable")
                .addKeyValue("path", path)
                .addKeyValue("reason", reason)
                .log("submission.prerequisite.unavailable");
        return new SubmissionException(HttpStatus.SERVICE_UNAVAILABLE, "JUDGING_NOT_READY", "当前题目暂时无法判题。");
    }
    /**
     * CORE 的快照带着驱动代码（最多 1 Mi 个字符）与前缀，JSON 转义后可能是字符数的数倍；
     * 4 MiB 足够装下合法快照，仍然挡得住失控的响应。
     */
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        public CompletionStage<byte[]> getBody() {
            return result;
        }

        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalStateException("snapshot too large"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
