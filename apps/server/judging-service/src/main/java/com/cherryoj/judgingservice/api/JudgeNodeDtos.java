package com.cherryoj.judgingservice.api;

import jakarta.validation.constraints.*;
import java.util.List;

/** 字段与共享 fixture 以 contracts/judge-node.schema.json 为准。节点只带身份，没有环境指纹。 */
public final class JudgeNodeDtos {
    private JudgeNodeDtos() {}

    public static final String NODE_ID = "^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$";

    public record Registration(
            @NotBlank @Pattern(regexp = NODE_ID) String nodeId,

            @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN)
            String sessionId,

            @NotBlank @Size(max = 512) String endpoint,

            @NotNull @Size(min = 1, max = 32)
            List<@NotBlank @Pattern(regexp = JudgingDtos.LANGUAGE_PATTERN) String> languages) {}

    public record Heartbeat(
            @NotBlank @Pattern(regexp = NODE_ID) String nodeId,

            @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN)
            String sessionId) {}

    public record Lease(String nodeId, long leaseDurationNs) {}
    /** 管理端看到的节点状态：不含访问地址与会话号，它们是节点协议的内部事实。 */
    public record NodeStatus(String nodeId, List<String> languages, boolean online, java.time.Instant lastSeenAt) {}

    public record NodeStatusList(List<NodeStatus> items) {}
}
