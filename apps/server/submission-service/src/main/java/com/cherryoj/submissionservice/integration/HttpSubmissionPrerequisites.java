package com.cherryoj.submissionservice.integration;

import com.cherryoj.identitysecurity.service.ServiceCredentials;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpSubmissionPrerequisites implements SubmissionPrerequisites {
    private final String problemUrl, judgingUrl, problemToken, judgingToken;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public HttpSubmissionPrerequisites(ObjectMapper json,
            @Value("${cherry.submission.problem-url}") String problemUrl,
            @Value("${cherry.submission.judging-url}") String judgingUrl,
            @Value("${cherry.submission.problem-token:}") String problemToken,
            @Value("${cherry.submission.judging-token:}") String judgingToken) {
        this.json=json; this.problemUrl=problemUrl; this.judgingUrl=judgingUrl;
        this.problemToken=problemToken; this.judgingToken=judgingToken;
    }
    public Snapshot snapshot(String id, String language) {
        return post(problemUrl, "/internal/submission/problem-snapshot", problemToken,
                Map.of("problemId",id,"languageId",language), Snapshot.class);
    }
    public Profile profile(Snapshot s) {
        return post(judgingUrl, "/internal/submission/execution-profile", judgingToken,
                Map.of("problemVersionId",s.problemVersionId(),"testDataVersionId",s.testDataVersionId(),
                        "testDataContentSha256",s.testDataContentSha256(),"languageId",s.languageId(),"totalCount",s.totalCount()), Profile.class);
    }
    private <T> T post(String base, String path, String token, Object body, Class<T> type) {
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            ServiceCredentials.validate(token);
            var request = HttpRequest.newBuilder(URI.create(base.replaceAll("/$", "") + path))
                    .timeout(Duration.ofSeconds(5)).header("Authorization","Bearer " + token)
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            future=http.sendAsync(request, ignored -> new LimitedBody());
            var response=future.get(6, TimeUnit.SECONDS);
            if (response.statusCode()==404) throw new SubmissionException(HttpStatus.NOT_FOUND,"PROBLEM_NOT_AVAILABLE","题目当前不可提交。");
            if (response.statusCode()==422) throw new SubmissionException(HttpStatus.UNPROCESSABLE_ENTITY,"UNSUPPORTED_CODE_MODE","当前只支持 C++ ACM 正式提交。");
            if (response.statusCode()!=200) throw unavailable();
            return json.readValue(response.body(),type);
        } catch (SubmissionException error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception error) { throw unavailable(); }
        finally { if (future!=null && !future.isDone()) future.cancel(true); }
    }
    private static SubmissionException unavailable() {
        return new SubmissionException(HttpStatus.SERVICE_UNAVAILABLE,"JUDGING_NOT_READY","当前题目暂时无法判题。");
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription=value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer:buffers) {
                if (buffer.remaining()>65536-bytes.size()) { subscription.cancel(); result.completeExceptionally(new IllegalStateException("snapshot too large")); return; }
                byte[] chunk=new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
