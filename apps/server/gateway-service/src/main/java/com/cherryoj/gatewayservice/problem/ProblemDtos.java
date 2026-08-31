package com.cherryoj.gatewayservice.problem;

import java.util.List;

final class ProblemDtos {

	private ProblemDtos() {
	}

	record LanguageSummary(String id, String displayName) {
	}

	record ProblemLanguage(String id, String displayName, String starterCode) {
	}

	record ProblemSample(int ordinal, String input, String output, String explanationMarkdown) {
	}

	record ProblemSummary(
			String problemId, String slug, String currentVersionId, int versionNo, String title,
			String difficulty, List<String> tags, String codeMode,
			List<LanguageSummary> allowedLanguages) {
	}

	record ProblemList(List<ProblemSummary> items, String nextCursor, boolean hasMore) {
	}

	record ProblemListData(List<ProblemSummary> items) {
	}

	record ProblemDetail(
			String problemId, String problemVersionId, int versionNo, String slug, String codeMode,
			String title, String difficulty, List<String> tags, String statementMarkdown,
			String inputDescriptionMarkdown, String outputDescriptionMarkdown,
			String constraintsMarkdown, String hintMarkdown, List<ProblemSample> samples,
			List<ProblemLanguage> allowedLanguages) {
	}

	record VersionSummary(
			String id, int versionNo, String status, String title, String updatedAt,
			String publishedAt, long rowVersion) {
	}

	record AdminProblem(
			String id, String slug, String visibility, String status, String currentPublishedVersionId,
			List<VersionSummary> versions, String createdAt, String updatedAt, long rowVersion) {
	}

	record AdminProblemPage(
			List<AdminProblem> items, int page, int size, long totalElements, int totalPages) {
	}

	record AdminProblemListData(List<AdminProblem> items) {
	}

	record AdminLanguage(String id, String displayName, String starterCode) {
	}

	record ManifestFile(String name, long sizeBytes, String sha256) {
	}

	record Manifest(int caseCount, long totalBytes, List<ManifestFile> files) {
	}

	record TestDataVersion(
			String id, String problemId, String status, String sourceType, String contentSha256,
			Integer caseCount, Long totalBytes, Manifest manifest, String createdAt, String readyAt,
			String errorMessage) {
	}

	record TestDataVersionListData(List<TestDataVersion> items) {
	}

	record AdminProblemVersion(
			String id, String problemId, int versionNo, String status, String codeMode, String title,
			String statementMarkdown, String inputDescriptionMarkdown, String outputDescriptionMarkdown,
			String constraintsMarkdown, String hintMarkdown, String difficulty, List<String> tags,
			List<ProblemSample> samples, List<AdminLanguage> allowedLanguages,
			TestDataVersion testDataVersion, String changeSummary, String createdAt, String updatedAt,
			String publishedAt, long rowVersion) {
	}

	record TestDataDeployment(
			String testDataVersionId, String environmentId, String environmentName,
			String expectedSha256, String status, String deployedSha256, String deployedAt,
			String errorMessage, String updatedAt, long rowVersion) {
	}

	record BenchmarkSummary(
			String sourceSha256, String verdict, Long maxCpuNs, Long maxMemoryBytes, Long maxClockNs) {
	}

	record LanguageCalibration(
			String id, String problemVersionId, String languageId, String environmentId, String status,
			Long cpuNs, Long memoryBytes, Long clockNs, BenchmarkSummary benchmarkSummary,
			String errorMessage, String createdAt, String updatedAt, long rowVersion) {
	}

	record PublishCheckItem(String code, boolean passed, String message) {
	}

	record PublishCheck(boolean ready, String environmentId, List<PublishCheckItem> checks) {
	}
}
