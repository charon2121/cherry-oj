package com.cherryoj.problemservice.api;

import java.time.LocalDateTime;
import java.util.List;

public final class TestDataDtos {

    private TestDataDtos() {
    }

    public record ManifestFile(String name, long sizeBytes, String sha256) {
    }

    public record Manifest(int testcaseCount, long totalBytes, List<ManifestFile> files) {
    }

    /**
     * 题目当前的测试数据。digest、测试点数和文件清单都来自地址下的 testdata.json，不对外暴露地址本身
     * （那是服务器上的路径）。
     */
    public record TestData(
            String digest,
            int testcaseCount,
            long totalBytes,
            LocalDateTime updatedAt,
            Manifest manifest) {
    }

    /** problem-service 到 judging-service 的内部读取模型，契约见 contracts/problem-test-data.schema.json。 */
    public record ProblemTestData(String location, String digest, int testcaseCount, long totalBytes) {
    }
}
