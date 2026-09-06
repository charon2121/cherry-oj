package com.cherryoj.judgingservice.judge;

import com.cherryoj.judgingservice.api.JudgeNodeDtos.*;
import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.api.JudgingDtos.DeploymentMetadata;
import com.cherryoj.judgingservice.config.JudgeNodeProperties;
import com.cherryoj.judgingservice.config.JudgingProperties;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository.Node;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JudgeNodeClient {
    private final JudgeNodeProperties nodeProperties;
    private final JudgingProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;
    public JudgeNodeClient(JudgeNodeProperties nodeProperties, JudgingProperties properties, ObjectMapper json) {
        this.nodeProperties = nodeProperties; this.properties = properties; this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public Receipt install(Node node, DeploymentMetadata metadata, InputStream archive, String traceId) {
        String boundary = "cherry-node-" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] middle = ("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"archive\"; filename=\"asset.zip\"\r\nContent-Type: application/zip\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        var install = new Install(node.nodeId(), node.fingerprint(), node.sessionId(), metadata.testDataVersionId(), metadata.expectedSha256(), metadata.manifest());
        byte[] encoded = json.writeValueAsBytes(install);
        if (encoded.length > 1_048_576) throw rejected();
        var consumed = new java.util.concurrent.atomic.AtomicBoolean();
        var publisher = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),
                HttpRequest.BodyPublishers.ofByteArray(encoded), HttpRequest.BodyPublishers.ofByteArray(middle),
                HttpRequest.BodyPublishers.ofInputStream(() -> {
                    // 非可重放源禁止 HTTP 客户端隐式二次订阅。重试由调用者重新打开原始资产。
                    if (!consumed.compareAndSet(false, true)) throw new IllegalStateException("archive already consumed");
                    return new FilterInputStream(archive) {
                        long remaining = properties.maxArchiveBytes();
                        @Override public int read(byte[] b, int off, int len) throws IOException {
                            int count = super.read(b, off, (int)Math.min(len, remaining + 1));
                            if (count > remaining) throw new IOException("archive exceeds limit");
                            if (count > 0) remaining -= count; return count;
                        }
                        @Override public int read() throws IOException {
                            int value = super.read(); if (value >= 0 && --remaining < 0) throw new IOException("archive exceeds limit"); return value;
                        }
                    };
                }), HttpRequest.BodyPublishers.ofByteArray(suffix));
        var builder = HttpRequest.newBuilder(URI.create(node.endpoint().replaceAll("/$", "") + "/internal/judge-node/v1/install"))
                .timeout(properties.judgeTimeout()).header("Authorization", "Bearer " + nodeProperties.controlToken())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(publisher);
        if (traceId != null && traceId.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")) builder.header("traceparent", traceId);
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(builder.build(), ignored -> new LimitedBody());
        HttpResponse<byte[]> response;
        try {
            response = future.get(properties.judgeTimeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            future.cancel(true); Thread.currentThread().interrupt(); throw unreachable();
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true); throw unreachable();
        }
        if (response.statusCode() >= 400 && response.statusCode() < 500) throw rejected();
        if (response.statusCode() != 200) throw unreachable();
        try {
            var tree = json.readTree(response.body());
            if (!tree.isObject() || tree.size() != 6 || !tree.path("fileCount").isIntegralNumber()) throw mismatch();
            for (String field : List.of("nodeId", "environmentFingerprint", "sessionId", "testDataVersionId", "sha256")) {
                if (!"STRING".equals(tree.path(field).getNodeType().name())) throw mismatch();
            }
            Receipt receipt = json.treeToValue(tree, Receipt.class);
            if (!node.nodeId().equals(receipt.nodeId()) || !node.fingerprint().equals(receipt.environmentFingerprint())
                    || !node.sessionId().equals(receipt.sessionId()) || !metadata.testDataVersionId().equals(receipt.testDataVersionId())
                    || !metadata.expectedSha256().equals(receipt.sha256()) || metadata.manifest().files().size() != receipt.fileCount()) throw mismatch();
            return receipt;
        } catch (JudgingApiException e) { throw e; }
        catch (RuntimeException e) { throw mismatch(); }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long size;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) size += buffer.remaining();
            if (size > 16_384) { subscription.cancel(); delegate.onError(new IOException("node response exceeds limit")); }
            else delegate.onNext(buffers);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
    }
    public static JudgingApiException unreachable() { return problem("JUDGE_NODE_UNREACHABLE", "判题节点暂时无法连接，请等待节点恢复后重试。"); }
    public static JudgingApiException rejected() { return problem("JUDGE_NODE_DATA_REJECTED", "判题节点拒绝测试数据，请检查数据包后重新部署。"); }
    public static JudgingApiException mismatch() { return problem("JUDGE_NODE_RECEIPT_MISMATCH", "判题节点返回的数据回执不匹配，请重新部署或联系管理员。"); }
    private static JudgingApiException problem(String code, String detail) { return new JudgingApiException(HttpStatus.SERVICE_UNAVAILABLE, code, detail); }
}
