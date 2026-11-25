package com.cherryoj.problemservice.application;

import static com.cherryoj.problemservice.application.AuthoringInput.*;

import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.TestDataDtos.TestData;
import com.cherryoj.problemservice.config.AuthoringProperties;
import com.cherryoj.problemservice.domain.UuidV7;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AuthoringMapper;
import com.cherryoj.problemservice.persistence.AuthoringMapper.Job;
import com.cherryoj.problemservice.persistence.AuthoringMapper.Summary;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuthoringService {
    public record JobSummary(
            String id,
            String kind,
            String status,
            String stage,
            long rowVersion,
            int attempt,
            Instant createdAt,
            Instant updatedAt,
            Instant appliedAt) {
        static JobSummary from(Summary summary) {
            return new JobSummary(
                    summary.id(),
                    summary.kind(),
                    summary.status(),
                    summary.stage(),
                    summary.rowVersion(),
                    summary.attempt(),
                    utc(summary.createdAt()),
                    utc(summary.updatedAt()),
                    utc(summary.appliedAt()));
        }

        private static Instant utc(LocalDateTime date) {
            return date == null ? null : date.toInstant(ZoneOffset.UTC);
        }
    }

    public record JobList(List<JobSummary> items) {}

    public record JobDetail(
            JobSummary job, JsonNode buildResult, JsonNode proposal, String errorCode, boolean reportAvailable) {}

    record Outcome(String status, String stage, String data, String error, String report, byte[] archive) {
        static Outcome failure(String code) {
            return new Outcome("FAILED", null, null, code, null, null);
        }
    }

    private final AuthoringMapper jobs;
    private final AdminProblemMapper problems;
    private final AuthoringInput inputs;
    private final AuthoringProperties settings;
    private final ProblemAuditWriter audit;
    private final TestDataService testData;
    private final ObjectMapper json;
    private final UuidV7 ids;
    private final Clock clock;
    private final TransactionTemplate transactions;

    AuthoringService(
            AuthoringMapper jobs,
            AdminProblemMapper problems,
            AuthoringInput inputs,
            AuthoringProperties settings,
            ProblemAuditWriter audit,
            TestDataService testData,
            ObjectMapper json,
            UuidV7 ids,
            Clock clock,
            PlatformTransactionManager manager) {
        this.jobs = jobs;
        this.problems = problems;
        this.inputs = inputs;
        this.settings = settings;
        this.audit = audit;
        this.testData = testData;
        this.json = json;
        this.ids = ids;
        this.clock = clock;
        transactions = new TransactionTemplate(manager);
    }

    public JobList list(String problemId) {
        problem(problemId, false);
        return new JobList(jobs.list(problemId).stream().map(JobSummary::from).toList());
    }

    public JobDetail get(String problemId, String id) {
        return detail(require(jobs.get(problemId, id)));
    }

    public String report(String problemId, String id) {
        require(jobs.get(problemId, id));
        String report = jobs.report(problemId, id);
        if (report == null) throw unavailable("当前尝试的报告暂不可用。");
        return report;
    }

    public JobDetail create(String problemId, JsonNode body, String actor) {
        String kind = requiredText(body, "kind", 8);
        long rowVersion = integer(body, "rowVersion", 0, Long.MAX_VALUE);
        return transactions.execute(tx -> {
            jobs.lockQueue();
            var draft = problem(problemId, true);
            requireDraft(draft, rowVersion);
            var snapshot = inputs.snapshot(draft);
            var request = switch (kind) {
                case "BUILD" -> inputs.build(body, snapshot);
                case "PROPOSAL" -> inputs.proposal(body, snapshot, id -> previousProposal(problemId, id));
                default -> throw invalid("任务类型无效。");
            };
            String requestDigest = digest(request.kind() + "\n" + request.snapshotDigest() + "\n" + request.json());
            String existing = jobs.existing(problemId, requestDigest);
            if (existing != null) return get(problemId, existing);

            requireEnabled(kind);
            requireQueueCapacity();
            String id = ids.next().toString();
            jobs.insert(
                    id, problemId, requestDigest, request.snapshotDigest(), rowVersion, kind, request.json(), now());
            audit.append(problemId, actor, "AUTHORING_JOB_CREATED", Map.of("jobId", id, "kind", kind));
            return get(problemId, id);
        });
    }

    public JobDetail retry(String problemId, String id, String actor) {
        return transactions.execute(tx -> {
            jobs.lockQueue();
            var draft = problem(problemId, true);
            var job = require(jobs.lock(problemId, id));
            requireSnapshot(draft, job);
            if (job.status().equals("QUEUED") || job.status().equals("RUNNING")) return detail(job);
            requireEnabled(job.kind());
            if (job.attempt() == 5
                    || !(job.status().equals("FAILED") || job.status().equals("CANCELLED"))) {
                throw conflict("RESOURCE_STATE_CONFLICT", "当前任务不可重试。");
            }
            requireQueueCapacity();
            if (jobs.retry(id, now()) != 1) throw conflict("RESOURCE_STATE_CONFLICT", "任务状态已变化。");
            audit.append(problemId, actor, "AUTHORING_JOB_RETRIED", Map.of("jobId", id, "attempt", job.attempt() + 1));
            return get(problemId, id);
        });
    }

    public JobDetail cancel(String problemId, String id, String actor) {
        return transactions.execute(tx -> {
            // 与物理删除沿用同一锁序。
            problem(problemId, true);
            var job = require(jobs.lock(problemId, id));
            if (job.status().equals("CANCELLED")) return detail(job);
            if (jobs.cancel(id, now()) != 1) throw conflict("RESOURCE_STATE_CONFLICT", "当前任务不可取消。");
            audit.append(problemId, actor, "AUTHORING_JOB_CANCELLED", Map.of("jobId", id, "attempt", job.attempt()));
            return get(problemId, id);
        });
    }

    public TestData apply(String problemId, String id, JsonNode body, String actor) {
        fields(body, "rowVersion", "testDataDigest");
        long rowVersion = integer(body, "rowVersion", 0, Long.MAX_VALUE);
        String digest = requiredText(body, "testDataDigest", 64);
        if (!digest.matches("[a-f0-9]{64}")) throw invalid("产物指纹格式无效。");
        var job = require(jobs.get(problemId, id));
        checkArtifact(job, digest, rowVersion);
        if (job.appliedJson() != null) return json.readValue(job.appliedJson(), TestData.class);
        var artifact = jobs.archive(problemId, id);
        byte[] archive = artifact == null ? null : artifact.archive();
        if (archive == null) {
            var current = require(jobs.get(problemId, id));
            if (current.appliedJson() != null) return json.readValue(current.appliedJson(), TestData.class);
            throw unavailable("验题产物暂不可用。");
        }
        return testData.applyAuthoring(
                problemId,
                new ByteArrayInputStream(archive),
                actor,
                digest,
                draft -> {
                    var current = require(jobs.lock(problemId, id));
                    checkArtifact(current, digest, rowVersion);
                    if (current.appliedJson() != null) return json.readValue(current.appliedJson(), TestData.class);
                    requireSnapshot(draft, current);
                    return null;
                },
                result -> {
                    if (jobs.applied(id, json.writeValueAsString(result), now()) != 1) {
                        throw conflict("RESOURCE_STATE_CONFLICT", "任务应用状态已变化。");
                    }
                    audit.append(problemId, actor, "AUTHORING_ARTIFACT_APPLIED", Map.of("jobId", id, "digest", digest));
                });
    }

    Job claim() {
        return transactions.execute(tx -> {
            var lease = jobs.lockQueue();
            var now = now();
            if (lease.jobId() != null) {
                if (lease.leaseUntil().isAfter(now)) return null;
                jobs.finish(
                        lease.jobId(), lease.attempt(), "FAILED", null, null, "AUTHORING_INTERRUPTED", null, null, now);
                jobs.release(lease.jobId(), lease.attempt());
            }
            var job = jobs.next();
            if (job == null) return null;
            jobs.lease(job.id(), job.attempt(), now.plusMinutes(12));
            if (jobs.start(job.id(), job.attempt(), job.kind().equals("BUILD") ? "INPUT" : "MODEL", now) != 1) {
                throw new IllegalStateException("Authoring attempt could not start");
            }
            return job;
        });
    }

    boolean running(Job job) {
        return jobs.running(job.id(), job.attempt());
    }

    void progress(Job job, String stage) {
        jobs.progress(job.id(), job.attempt(), stage, now());
    }

    void finish(Job job, Outcome outcome) {
        transactions.executeWithoutResult(tx -> {
            jobs.lockQueue();
            jobs.finish(
                    job.id(),
                    job.attempt(),
                    outcome.status(),
                    outcome.stage(),
                    outcome.data(),
                    outcome.error(),
                    outcome.report(),
                    outcome.archive(),
                    now());
            jobs.release(job.id(), job.attempt());
        });
    }

    private void checkArtifact(Job job, String expectedDigest, long rowVersion) {
        if (!job.kind().equals("BUILD") || !job.status().equals("SUCCEEDED") || job.resultJson() == null) {
            throw conflict("RESOURCE_STATE_CONFLICT", "只有成功完成的构建可以应用。");
        }
        if (job.rowVersion() != rowVersion) throw conflict("ROW_VERSION_CONFLICT", "题目已更新，请重新验题。");
        if (!expectedDigest.equals(
                json.readTree(job.resultJson()).path("testDataDigest").asText())) {
            throw conflict("AUTHORING_ARTIFACT_CHANGED", "产物与审核指纹不一致。");
        }
    }

    private void requireSnapshot(ProblemRow draft, Job job) {
        requireDraft(draft, job.rowVersion());
        if (!inputs.snapshot(draft).digest().equals(job.snapshotDigest())) {
            throw conflict("AUTHORING_INPUT_CHANGED", "草稿的测试数据或语言配置已变化，请重新构建。");
        }
    }

    private static void requireDraft(ProblemRow draft, long rowVersion) {
        if (draft.status() != ProblemStatus.ACTIVE || draft.visibility() != Visibility.PRIVATE) {
            throw conflict("RESOURCE_STATE_CONFLICT", "只有在用的私有题目可以出题。");
        }
        if (draft.rowVersion() != rowVersion) throw conflict("ROW_VERSION_CONFLICT", "题目已更新，请刷新后重试。");
    }

    private JsonNode previousProposal(String problemId, String id) {
        var previous = require(jobs.get(problemId, id));
        if (!previous.kind().equals("PROPOSAL")
                || !previous.status().equals("SUCCEEDED")
                || previous.resultJson() == null) {
            throw conflict("RESOURCE_STATE_CONFLICT", "请选择同题已生成的 AI 提案。");
        }
        return json.readTree(previous.resultJson());
    }

    private void requireEnabled(String kind) {
        if (kind.equals("BUILD") ? !settings.buildEnabled() : !settings.modelEnabled()) {
            throw unavailable(kind.equals("BUILD") ? "当前部署尚未启用验题执行器。" : "当前部署尚未启用 AI 出题。");
        }
    }

    private void requireQueueCapacity() {
        if (jobs.queuedCount() >= 16) {
            throw new ProblemApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTHORING_QUEUE_FULL", "待执行任务已满，请稍后再试。");
        }
    }

    private ProblemRow problem(String id, boolean lock) {
        var row = lock ? problems.findProblemForUpdate(id) : problems.findProblem(id);
        if (row == null) throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在。");
        return row;
    }

    private static Job require(Job job) {
        if (job == null) throw new ProblemApiException(HttpStatus.NOT_FOUND, "AUTHORING_JOB_NOT_FOUND", "找不到这道题的出题任务。");
        return job;
    }

    private JobDetail detail(Job job) {
        JsonNode result = job.resultJson() == null ? null : json.readTree(job.resultJson());
        boolean build = job.kind().equals("BUILD");
        return new JobDetail(
                JobSummary.from(job.summary()),
                build ? result : null,
                build ? null : result,
                job.errorCode(),
                job.reportAvailable());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static ProblemApiException conflict(String code, String detail) {
        return new ProblemApiException(HttpStatus.CONFLICT, code, detail);
    }

    private static ProblemApiException unavailable(String detail) {
        return new ProblemApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTHORING_UNAVAILABLE", detail);
    }
}
