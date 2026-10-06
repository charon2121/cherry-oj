package com.cherryoj.problemservice.integration.judging;

import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.config.JudgingClientProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class HttpJudgingClient implements JudgingClient {
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final String TRACEPARENT_PATTERN = "^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$";

    private final JudgingClientProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    public HttpJudgingClient(JudgingClientProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    @Override
    public JudgingDtos.Calibration calibrate(
            JudgingDtos.CalibrationRequest value,
            String delegatedJwt,
            String traceparent) {
        HttpRequest request = request("/internal/admin/calibrations", delegatedJwt, traceparent)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(writeJson(value)))
                .build();
        return send(request, JudgingDtos.Calibration.class);
    }

    @Override
    public JudgingDtos.Readiness readiness(
            String problemId,
            String languageId,
            String testDataDigest,
            String delegatedJwt,
            String traceparent) {
        String query = "?problemId=" + encode(problemId)
                + "&languageId=" + encode(languageId)
                + "&testDataDigest=" + encode(testDataDigest);
        return send(request("/internal/admin/readiness" + query, delegatedJwt, traceparent).GET().build(),
                JudgingDtos.Readiness.class);
    }

    private HttpRequest.Builder request(String path, String delegatedJwt, String traceparent) {
        if (delegatedJwt == null || delegatedJwt.isBlank()) {
            throw new ProblemApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "需要管理员身份。");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(resolve(path))
                .timeout(properties.requestTimeout())
                .header("Authorization", "Bearer " + delegatedJwt)
                .header("Accept", "application/json");
        if (traceparent != null && traceparent.matches(TRACEPARENT_PATTERN)) request.header("traceparent", traceparent);
        return request;
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) throw downstream("JUDGING_INVALID_RESPONSE", "判题服务响应超限。");
                if (response.statusCode() < 200 || response.statusCode() >= 300) throw status(response.statusCode(), bytes);
                try {
                    return json.readValue(bytes, responseType);
                }
                catch (Exception error) {
                    throw downstream("JUDGING_INVALID_RESPONSE", "判题服务响应格式无效。");
                }
            }
        }
        catch (HttpTimeoutException error) {
            throw new ProblemApiException(HttpStatus.GATEWAY_TIMEOUT, "JUDGING_TIMEOUT", "判题服务请求超时，请读取状态后重试。");
        }
        catch (ConnectException error) {
            throw new ProblemApiException(HttpStatus.SERVICE_UNAVAILABLE, "JUDGING_UNAVAILABLE", "判题服务暂时不可用。");
        }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProblemApiException(HttpStatus.SERVICE_UNAVAILABLE, "JUDGING_INTERRUPTED", "判题服务请求已中断。");
        }
        catch (IOException error) {
            throw downstream("JUDGING_IO_FAILURE", "判题服务通信失败。");
        }
    }

    private byte[] writeJson(Object value) {
        try {
            return json.writeValueAsBytes(value);
        }
        catch (Exception error) {
            throw new IllegalStateException("Could not encode judging request", error);
        }
    }

    private URI resolve(String path) {
        String base = properties.baseUrl().toString();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private ProblemApiException status(int status, byte[] body) {
        if (status == 503) {
            try {
                String code = json.readTree(body).path("code").asText();
                String detail = switch (code) {
                    case "NO_ONLINE_JUDGE_NODE" -> "当前没有在线判题节点，请启动节点并等待注册后重试。";
                    case "JUDGE_NODE_UNREACHABLE" -> "判题节点暂时无法连接，请等待节点恢复后重试。";
                    default -> null;
                };
                if (detail != null) return new ProblemApiException(HttpStatus.SERVICE_UNAVAILABLE, code, detail);
            } catch (RuntimeException ignored) { /* 未知或非法正文始终收敛。 */ }
        }
        if (status == 409) return new ProblemApiException(HttpStatus.CONFLICT, "JUDGING_STATE_CONFLICT", "判题资源状态冲突。");
        if (status == 422 || status == 400) return new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY, "JUDGING_VALIDATION_FAILED", "判题服务拒绝了请求数据。");
        if (status >= 500) return new ProblemApiException(HttpStatus.SERVICE_UNAVAILABLE, "JUDGING_UNAVAILABLE", "判题服务暂时不可用。");
        return downstream("JUDGING_REJECTED", "判题服务拒绝了委托请求。");
    }

    private static ProblemApiException downstream(String code, String message) {
        return new ProblemApiException(HttpStatus.BAD_GATEWAY, code, message);
    }
}
