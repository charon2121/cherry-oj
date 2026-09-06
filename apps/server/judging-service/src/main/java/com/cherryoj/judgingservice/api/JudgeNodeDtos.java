package com.cherryoj.judgingservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** 字段与共享 fixture 以 contracts/judge-node.schema.json 为准。 */
public final class JudgeNodeDtos {
    private JudgeNodeDtos() {}
    public static final String NODE_ID = "^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$";
    public record Language(@NotBlank @Pattern(regexp = JudgingDtos.LANGUAGE_PATTERN) String languageId,
                           @NotBlank @Size(max = 256) String toolchainVersion,
                           @NotBlank @Size(max = 128) String languageConfigDigest) {}
    public record Registration(
            @NotBlank @Pattern(regexp = NODE_ID) String nodeId,
            @NotBlank @Size(max = 256) String environmentFingerprint,
            @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String sessionId,
            @NotBlank @Size(max = 512) String endpoint,
            @NotBlank @Size(max = 32) String architecture,
            @NotBlank @Size(max = 256) String cpuModel,
            @NotBlank @Size(max = 128) String osVersion,
            @NotBlank @Size(max = 128) String kernelVersion,
            @NotBlank @Size(max = 128) String judgeVersion,
            @NotBlank @Size(max = 128) String sandboxVersion,
            @NotBlank @Size(max = 128) String configDigest,
            @NotNull @Size(min = 1, max = 32) List<@Valid Language> languages) {}
    public record Heartbeat(@NotBlank @Pattern(regexp = NODE_ID) String nodeId,
                            @NotBlank @Size(max = 256) String environmentFingerprint,
                            @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String sessionId) {}
    public record Lease(String nodeId, String environmentId, long leaseDurationNs) {}
    public record Install(String nodeId, String environmentFingerprint, String sessionId,
                          String testDataVersionId, String expectedSha256, JudgingDtos.Manifest manifest) {}
    public record Receipt(String nodeId, String environmentFingerprint, String sessionId,
                          String testDataVersionId, String sha256, int fileCount) {}
}
