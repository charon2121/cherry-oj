package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.application.AuthoringService.Outcome;
import com.cherryoj.problemservice.config.AuthoringProperties;
import com.cherryoj.problemservice.persistence.AuthoringMapper.Job;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
final class AuthoringWorker {
    private static final Logger LOG = LoggerFactory.getLogger(AuthoringWorker.class);
    private static final Set<String> BUILD_STAGES =
            Set.of("INPUT", "GENERATOR", "VALIDATOR", "ANSWERS", "ORACLE", "LANGUAGES", "EXPORT");
    private static final int RESULT_BYTES = 64 << 10;
    private static final int REPORT_BYTES = 1 << 20;
    private static final int ARCHIVE_BYTES = 9 << 20;

    private final AuthoringService jobs;
    private final AuthoringProperties settings;
    private final OpenAiProblemProposal model;
    private final ObjectMapper json;

    AuthoringWorker(
            AuthoringService jobs, AuthoringProperties settings, OpenAiProblemProposal model, ObjectMapper json) {
        this.jobs = jobs;
        this.settings = settings;
        this.model = model;
        this.json = json;
    }

    @Scheduled(fixedDelay = 500)
    void work() {
        if (!settings.buildEnabled() && !settings.modelEnabled()) return;
        var job = jobs.claim();
        if (job == null) return;
        Outcome outcome;
        try {
            outcome = job.kind().equals("PROPOSAL")
                    ? model.generate(job.inputJson(), () -> jobs.running(job))
                    : build(job);
        } catch (InterruptedException interrupted) {
            outcome = Outcome.failure("AUTHORING_INTERRUPTED");
        } catch (IOException | RuntimeException error) {
            String code =
                    job.kind().equals("PROPOSAL") ? "AUTHORING_MODEL_UNAVAILABLE" : "AUTHORING_RUNNER_UNAVAILABLE";
            LOG.warn("authoring.worker.failed jobId={} attempt={} code={}", job.id(), job.attempt(), code);
            outcome = Outcome.failure(code);
        }
        jobs.finish(job, outcome);
    }

    private Outcome build(Job job) throws IOException, InterruptedException {
        if (!settings.buildEnabled() || !Files.isExecutable(settings.binary()) || !jobs.running(job)) {
            return Outcome.failure("AUTHORING_RUNNER_UNAVAILABLE");
        }
        var privateDirectory = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        Files.createDirectories(settings.workRoot(), privateDirectory);
        Path workspace = Files.createTempDirectory(settings.workRoot(), "authoring-", privateDirectory);
        Process process = null;
        try {
            Path input = workspace.resolve("input.json");
            Path output = workspace.resolve("output");
            Files.writeString(input, job.inputJson(), StandardCharsets.UTF_8);
            var command = new ProcessBuilder(
                            settings.binary().toString(),
                            "-job",
                            input.toString(),
                            "-output",
                            output.toString(),
                            "-judge-url",
                            settings.judgeUrl().toString(),
                            "-toolchain-root",
                            settings.toolchainRoot().toString())
                    .directory(workspace.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            command.environment().clear();
            process = command.start();
            long deadline =
                    System.nanoTime() + Duration.ofMinutes(10).plusSeconds(5).toNanos();
            while (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                if (!jobs.running(job)) return Outcome.failure("AUTHORING_INTERRUPTED");
                if (System.nanoTime() >= deadline) return Outcome.failure("AUTHORING_TIMEOUT");
                readProgress(output, stage -> jobs.progress(job, stage));
            }
            return result(job, output, process.exitValue());
        } finally {
            try {
                if (process != null && process.isAlive()) {
                    process.destroy();
                    if (!process.waitFor(2, TimeUnit.SECONDS))
                        process.destroyForcibly().waitFor();
                }
            } finally {
                cleanup(workspace);
            }
        }
    }

    private void readProgress(Path output, Consumer<String> report) throws IOException {
        byte[] data = read(output.resolve("progress.json"), 1024);
        if (data == null) return;
        var progress = json.readTree(data);
        String stage = progress.path("stage").asText();
        if (progress.path("schemaVersion").asInt() == 1 && BUILD_STAGES.contains(stage)) report.accept(stage);
    }

    private Outcome result(Job job, Path output, int exit) throws IOException {
        byte[] metadata = read(output.resolve("result.json"), RESULT_BYTES);
        if (metadata == null) return Outcome.failure("AUTHORING_RUNNER_UNAVAILABLE");
        var result = json.readTree(metadata);
        var report = read(output.resolve("report.txt"), REPORT_BYTES);
        if (result.path("schemaVersion").asInt() != 1
                || !BUILD_STAGES.contains(result.path("stage").asText())
                || !result.path("languages").isArray()
                || result.path("languages").size() > 4
                || report == null) {
            return Outcome.failure("AUTHORING_RUNNER_UNAVAILABLE");
        }
        String details = new String(report, StandardCharsets.UTF_8);
        String data = json.writeValueAsString(result);
        if (exit != 0 && "FAILED".equals(result.path("status").asText())) {
            return new Outcome("FAILED", result.path("stage").asText(), data, "AUTHORING_BUILD_FAILED", details, null);
        }
        var digest = result.path("testDataDigest").asText();
        var frozen = json.readTree(job.inputJson());
        if (exit != 0
                || !"SUCCEEDED".equals(result.path("status").asText())
                || !"EXPORT".equals(result.path("stage").asText())
                || !digest.matches("[a-f0-9]{64}")
                || result.path("testcaseCount").asInt() != frozen.path("cases").size()
                || !languageResultsMatch(frozen.path("languages"), result.path("languages"))) {
            return Outcome.failure("AUTHORING_RUNNER_UNAVAILABLE");
        }
        byte[] archive = read(output.resolve("testdata.zip"), ARCHIVE_BYTES);
        if (archive == null || archive.length == 0) return Outcome.failure("AUTHORING_RUNNER_UNAVAILABLE");
        return new Outcome("SUCCEEDED", "COMPLETE", data, null, details, archive);
    }

    private static boolean languageResultsMatch(JsonNode frozen, JsonNode results) {
        var expected = new HashSet<String>();
        frozen.forEach(item -> expected.add(item.path("languageId").asText()));
        for (var language : results) {
            if (!expected.remove(language.path("languageId").asText())
                    || !"AC".equals(language.path("verdict").asText())
                    || !"PASSED".equals(language.path("status").asText())) return false;
        }
        return expected.isEmpty();
    }

    private static byte[] read(Path path, int limit) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null;
        try (var input = Files.newInputStream(path)) {
            byte[] data = input.readNBytes(limit + 1);
            if (data.length > limit) throw new IOException("Authoring artifact exceeds its limit");
            return data;
        }
    }

    private static void cleanup(Path directory) {
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException failure) {
            LOG.warn("authoring.worker.cleanup_failed");
        }
    }
}
