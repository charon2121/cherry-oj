package com.cherryoj.judgingservice.trial;

import static com.cherryoj.judgingservice.trial.CustomRunErrors.*;

import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.application.ExecutionProfileService;
import com.cherryoj.judgingservice.formal.FormalProperties;
import com.cherryoj.judgingservice.http.LimitedHttpBody;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import com.cherryoj.judgingservice.judge.LanguageFiles;
import com.cherryoj.judgingservice.limits.ResolvedLanguageLimits;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.cherryoj.judgingservice.trial.CustomRunDtos.EffectiveLimits;
import com.cherryoj.judgingservice.trial.CustomRunDtos.Request;
import com.cherryoj.judgingservice.trial.CustomRunDtos.Result;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class CustomRunService {
    private final ExecutionProfileService profiles;
    private final JudgeNodeRepository nodes;
    private final FormalProperties budgets;
    private final ObjectMapper json;
    private final HttpClient http;
    private final LanguageCatalog languages;
    private final Semaphore slots = new Semaphore(1);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    CustomRunService(
            ExecutionProfileService profiles,
            JudgeNodeRepository nodes,
            FormalProperties budgets,
            ObjectMapper json,
            HttpClient http,
            LanguageCatalog languages) {
        this.profiles = profiles;
        this.nodes = nodes;
        this.budgets = budgets;
        this.json = json;
        this.http = http;
        this.languages = languages;
    }

    Result run(Request request, String trace) {
        if (request.source().getBytes(StandardCharsets.UTF_8).length > 1048576
                || (request.solutionSource() != null
                        && request.solutionSource().getBytes(StandardCharsets.UTF_8).length > 327680)
                || request.inputText().getBytes(StandardCharsets.UTF_8).length > 65536)
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "RUN_INPUT_TOO_LARGE", "代码或输入过大。");
        if ((request.solutionSource() == null) != (request.solutionPrefixLineCount() == null))
            throw error(HttpStatus.BAD_REQUEST, "INVALID_RUN_REQUEST", "用户文件与前缀行数必须同时给出。");
        long deadline = Math.min(
                request.deadlineEpochMs(), System.currentTimeMillis() + CustomRunProperties.DEADLINE.toMillis());
        remaining(deadline);
        if (!slots.tryAcquire()) throw error(HttpStatus.TOO_MANY_REQUESTS, "RUN_BUSY", "运行服务繁忙，请稍后重试。");
        // 0=未开始，1=工作线程持有槽位，2=启动前取消；取消与启动竞态只能释放一次。
        var started = new AtomicInteger();
        var released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) slots.release();
        };
        Future<Result> task;
        try {
            task = executor.submit(() -> {
                if (!started.compareAndSet(0, 1)) throw unavailable();
                try {
                    return execute(request, trace, deadline);
                } catch (ExecutionProfileService.Rejected rejected) {
                    throw JudgingApiException.from(rejected);
                } finally {
                    release.run();
                }
            });
        } catch (RejectedExecutionException stopped) {
            slots.release();
            throw unavailable();
        }
        try {
            return task.get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            task.cancel(true);
            if (started.compareAndSet(0, 2)) release.run();
            Thread.currentThread().interrupt();
            throw timeout();
        } catch (TimeoutException e) {
            task.cancel(true);
            if (started.compareAndSet(0, 2)) release.run();
            throw timeout();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof JudgingApiException known) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("custom.run.failed stage=execution code={}", known.code());
                throw known;
            }
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("custom.run.failed stage=execution code=UNEXPECTED_FAILURE");
            throw unavailable();
        }
    }

    private Result execute(Request r, String trace, long deadline) {
        ExecutionProfileService.ExecutionProfile profile = profiles.resolve(
                r.problemId().toString(), r.languageId(), ExecutionProfileService.Purpose.TRIAL, trace);
        ResolvedLanguageLimits limits = profile.limits();
        long clock =
                limits.clockNs() != null ? limits.clockNs() : Math.multiplyExact(limits.cpuNs(), budgets.wallRatio());
        var effective = new EffectiveLimits(limits.cpuNs(), limits.memoryBytes(), clock);
        if (profile.executionBudgetNs() > TimeUnit.MILLISECONDS.toNanos(remaining(deadline))) throw timeout();
        // 自测只用请求里的输入，不读测试数据：任何在线、声明了该语言的节点都行。
        var online = nodes.online(r.languageId(), LocalDateTime.now(ZoneOffset.UTC));
        if (online.isEmpty()) throw unavailable();
        var node = online.getFirst();
        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "submissionId",
                UUID.randomUUID().toString(),
                "problemId",
                r.problemId(),
                "languageId",
                r.languageId(),
                "source",
                r.source(),
                "limits",
                effective,
                "mode",
                "trial",
                "testcases",
                List.of(Map.of("input", r.inputText()))));
        if (r.solutionSource() != null) body.put("solutionSource", r.solutionSource());
        // CORE：报错里用户文件的行号按前缀行数换算；ACM 时两者为 null，只清理路径。
        String solutionFile = r.solutionSource() == null ? null : solutionFile(r.languageId());
        Integer prefixLines = r.solutionPrefixLineCount();
        Duration wait = Duration.ofNanos(
                Math.min(profile.executionBudgetNs(), TimeUnit.MILLISECONDS.toNanos(remaining(deadline))));
        var builder = HttpRequest.newBuilder(URI.create(node.endpoint().replaceAll("/$", "") + "/judge"))
                .timeout(wait)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
        if (trace != null && trace.matches("[a-f0-9]{2}-[a-f0-9]{32}-[a-f0-9]{16}-[a-f0-9]{2}"))
            builder.header("traceparent", trace);
        var response = http.sendAsync(builder.build(), ignored -> new LimitedHttpBody(2 * 1024 * 1024));
        try {
            var received = response.get(wait.toNanos(), TimeUnit.NANOSECONDS);
            if (received.statusCode() != 200) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("custom.run.failed stage=judge httpStatus={}", received.statusCode());
                throw unavailable();
            }
            return TrialResultMapper.fromJudge(json.readTree(received.body()), effective, solutionFile, prefixLines);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw timeout();
        } catch (TimeoutException e) {
            throw timeout();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof HttpTimeoutException) throw timeout();
            throw unavailable();
        } finally {
            if (!response.isDone()) response.cancel(true);
        }
    }

    private static long remaining(long deadline) {
        long ms = deadline - System.currentTimeMillis();
        if (ms <= 0) throw timeout();
        return ms;
    }

    /** CORE 自测要用该语言的用户文件名；不支持核心代码模式的语言没有这个名字，直接拒绝而不是送出一个编不过的程序。 */
    private String solutionFile(String languageId) {
        LanguageFiles files = languages.coreFiles(languageId);
        if (files == null) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_LANGUAGE", "该语言不支持核心代码模式。");
        return files.solutionFile();
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
