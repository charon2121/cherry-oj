package com.cherryoj.problemservice.application;

import static com.cherryoj.problemservice.application.ProblemReadinessChecks.*;

import com.cherryoj.identity.token.CallUser;
import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.Problem;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheck;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheckCode;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheckItem;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.application.ProblemReadinessChecks.PublicationSnapshot;
import com.cherryoj.problemservice.integration.judging.JudgingClient;
import com.cherryoj.problemservice.integration.judging.JudgingDtos;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 公开检查和题目可见性。公开前需要本地条件与判题服务就绪，提交时重新确认版本与测试数据指纹。
 */
@Service
public class ProblemPublicationService {
    private final AdminProblemMapper problems;
    private final ProblemReadinessChecks readiness;
    private final AdminProblemService adminProblems;
    private final JudgingClient judging;
    private final ProblemAuditWriter audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ProblemPublicationService(
            AdminProblemMapper problems,
            ProblemReadinessChecks readiness,
            AdminProblemService adminProblems,
            JudgingClient judging,
            ProblemAuditWriter audit,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.problems = problems;
        this.readiness = readiness;
        this.adminProblems = adminProblems;
        this.judging = judging;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public PublishCheck publishCheck(String problemId, CallUser admin, String traceparent) {
        PublicationSnapshot local = transactions.execute(status -> readiness.load(problemId, false));
        List<PublishCheckItem> checks = localChecks(local);
        var languages = new ArrayList<AdminProblemDtos.PublishLanguageCheck>();
        for (LanguageRow language : local.languages()) {
            String problem = languageProblem(local, language);
            var languageChecks = new ArrayList<PublishCheckItem>();
            JudgingDtos.Readiness remote = local.info() == null
                    ? null
                    : judging.readiness(
                            problemId,
                            language.languageId(),
                            local.info().digest(),
                            local.info().maxTestcaseIoBytes(),
                            admin,
                            traceparent);
            var support = remote == null
                    ? item(PublishCheckCode.LANGUAGE, false, "测试数据就绪后才能检查语言。")
                    : remoteCheck(remote, "LANGUAGE", PublishCheckCode.LANGUAGE, "没有在线节点支持该语言。");
            languageChecks.add(problem == null ? support : item(PublishCheckCode.LANGUAGE, false, problem));
            languageChecks.add(
                    remote == null
                            ? item(PublishCheckCode.ONLINE_JUDGE_NODE, false, "测试数据就绪后才能检查节点。")
                            : remoteCheck(
                                    remote, "ONLINE_JUDGE_NODE", PublishCheckCode.ONLINE_JUDGE_NODE, "当前没有在线判题节点。"));
            var calibration = remote == null
                    ? item(PublishCheckCode.CALIBRATION, false, "测试数据就绪后才能检查标定。")
                    : remoteCheck(remote, "CALIBRATION", PublishCheckCode.CALIBRATION, "没有有效标定。");
            if (remote != null && calibration.passed()) {
                var model = remoteCheck(remote, "LIMIT_MODEL", PublishCheckCode.CALIBRATION, "缺少可用的语言限制参数。");
                if (!model.passed()) calibration = model;
                String hint =
                        calibration.passed() ? recalibrationHint(local, language, remote.executionProfile()) : null;
                if (hint != null) calibration = item(PublishCheckCode.CALIBRATION, true, calibration.message() + hint);
            }
            languageChecks.add(calibration);
            languages.add(new AdminProblemDtos.PublishLanguageCheck(
                    language.languageId(),
                    languageChecks.stream().allMatch(PublishCheckItem::passed),
                    List.copyOf(languageChecks)));
        }
        if (languages.isEmpty()) {
            checks.add(item(PublishCheckCode.ONLINE_JUDGE_NODE, false, "请先配置语言。"));
            checks.add(item(PublishCheckCode.CALIBRATION, false, "请先配置语言。"));
        } else {
            var candidate = languages.stream()
                    .filter(AdminProblemDtos.PublishLanguageCheck::ready)
                    .findFirst()
                    .orElse(languages.getFirst());
            checks.set(2, candidate.checks().get(0));
            checks.add(candidate.checks().get(1));
            checks.add(candidate.checks().get(2));
        }
        return new PublishCheck(
                checks.stream().allMatch(PublishCheckItem::passed), List.copyOf(checks), List.copyOf(languages));
    }

    /**
     * 修改驱动代码或前缀不会让标定自动失效（2026-10-17 决定先只做提示）：两者晚于这条标定修改时，在 CALIBRATION 项后附一句提示。
     * 只比较时间，不比较内容；judging-service 没给出标定时间时不提示。
     */
    private static String recalibrationHint(
            PublicationSnapshot local, LanguageRow language, JudgingDtos.ResolvedLanguageLimits profile) {
        if (local.problem().codeMode() != AdminProblemDtos.CodeMode.CORE
                || profile == null
                || profile.calibratedAt() == null) {
            return null;
        }
        LocalDateTime calibratedAt = profile.calibratedAt();
        LocalDateTime templateAt = language.judgeTemplateUpdatedAt();
        if (templateAt != null && templateAt.isAfter(calibratedAt)) {
            return "驱动代码在最近一次标定后修改过，建议重新标定。";
        }
        if (local.prefixes().get(language.languageId()) != null
                && local.prefixes().get(language.languageId()).updatedAt().isAfter(calibratedAt)) {
            return "该语言的前缀代码在最近一次标定后修改过，建议重新标定。";
        }
        return null;
    }

    public Problem publish(String problemId, long rowVersion, CallUser admin, String traceparent, String actorUserId) {
        PublicationSnapshot before = transactions.execute(status -> readiness.load(problemId, false));
        requireActive(before.problem());
        if (before.problem().visibility() == Visibility.PUBLIC) {
            return adminProblems.getProblem(problemId);
        }
        if (before.problem().rowVersion() != rowVersion) throw conflict();
        if (!publishCheck(problemId, admin, traceparent).ready()) {
            throw state("公开检查未通过，请先处理所有缺项。");
        }

        transactions.executeWithoutResult(status -> {
            PublicationSnapshot locked = readiness.load(problemId, true);
            requireActive(locked.problem());
            if (locked.problem().rowVersion() != rowVersion) throw conflict();
            if (localChecks(locked).stream().anyMatch(value -> !value.passed())) {
                throw state("本地公开条件已改变，请重新检查。");
            }
            // 远程检查之后数据又被替换：检查的是旧数据，不能据此公开。
            if (!locked.info().digest().equals(before.info().digest())) {
                throw state("测试数据已改变，请重新检查。");
            }
            if (problems.publishProblem(problemId, now(), rowVersion) != 1) throw conflict();
            audit.append(
                    problemId,
                    actorUserId,
                    "PROBLEM_PUBLISHED",
                    Map.of("testDataDigest", locked.info().digest()));
        });
        return adminProblems.getProblem(problemId);
    }

    public Problem unpublish(String problemId, long rowVersion, String actorUserId) {
        transactions.executeWithoutResult(status -> {
            PublicationSnapshot locked = readiness.load(problemId, true);
            requireActive(locked.problem());
            if (locked.problem().visibility() == Visibility.PRIVATE) return;
            if (locked.problem().rowVersion() != rowVersion) throw conflict();
            if (problems.unpublishProblem(problemId, now(), rowVersion) != 1) throw conflict();
            audit.append(problemId, actorUserId, "PROBLEM_UNPUBLISHED", Map.of());
        });
        return adminProblems.getProblem(problemId);
    }

    private static PublishCheckItem remoteCheck(
            JudgingDtos.Readiness readiness, String remoteCode, PublishCheckCode publicCode, String fallback) {
        if (readiness.checks() != null) {
            for (JudgingDtos.ReadinessCheck check : readiness.checks()) {
                if (remoteCode.equals(check.code()))
                    return item(publicCode, check.passed(), safeMessage(check.message(), fallback));
            }
        }
        return item(publicCode, false, fallback);
    }

    private static String safeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) return fallback;
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    private static PublishCheckItem item(PublishCheckCode code, boolean passed, String message) {
        return new PublishCheckItem(code, passed, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static ProblemApiException conflict() {
        return new ProblemApiException(HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT", "资源已被其他窗口修改，请重新加载。");
    }
}
