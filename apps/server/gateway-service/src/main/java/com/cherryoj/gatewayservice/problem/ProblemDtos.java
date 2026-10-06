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
			String problemId, String slug, String title, String difficulty, List<String> tags,
			String codeMode, List<LanguageSummary> allowedLanguages) {
	}

	record ProblemList(List<ProblemSummary> items, String nextCursor, boolean hasMore) {
	}

	record ProblemListData(List<ProblemSummary> items) {
	}

	record ProblemDetail(
			String problemId, String slug, String codeMode, String title, String difficulty,
			List<String> tags, String statementMarkdown, String inputDescriptionMarkdown,
			String outputDescriptionMarkdown, String constraintsMarkdown, String hintMarkdown,
			List<ProblemSample> samples, List<ProblemLanguage> allowedLanguages) {
	}

	record AdminProblemSummary(
			String id, String slug, String title, String visibility, String status, String difficulty,
			boolean hasTestData, String updatedAt, String publishedAt, long rowVersion) {
	}

	record AdminProblemPage(
			List<AdminProblemSummary> items, int page, int size, long totalElements, int totalPages) {
	}

	record AdminProblemListData(List<AdminProblemSummary> items) {
	}

	record AdminLanguage(String id, String displayName, String starterCode) {
	}

	record ManifestFile(String name, long sizeBytes, String sha256) {
	}

	record Manifest(int caseCount, long totalBytes, List<ManifestFile> files) {
	}

	record TestData(String digest, int caseCount, long totalBytes, String updatedAt, Manifest manifest) {
	}

	/** 题目只有一份内容，管理端模型就是题目本身。 */
	record AdminProblem(
			String id, String slug, String visibility, String status, String codeMode, String title,
			String statementMarkdown, String inputDescriptionMarkdown, String outputDescriptionMarkdown,
			String constraintsMarkdown, String hintMarkdown, String difficulty, List<String> tags,
			List<ProblemSample> samples, List<AdminLanguage> allowedLanguages, TestData testData,
			String createdAt, String updatedAt, String publishedAt, long rowVersion) {
	}

	record BenchmarkSummary(
			String sourceSha256, String verdict, Long maxCpuNs, Long maxMemoryBytes, Long maxClockNs) {
	}

	record LanguageCalibration(
			String id, String problemId, String languageId, String status,
			Long cpuNs, Long memoryBytes, Long clockNs, String testDataDigest,
			BenchmarkSummary benchmarkSummary, String errorMessage, String createdAt, String updatedAt,
			long rowVersion) {
	}

	record PublishCheckItem(String code, boolean passed, String message) {
	}

	record PublishCheck(boolean ready, List<PublishCheckItem> checks) {
	}
}
