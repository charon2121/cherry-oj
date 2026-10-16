package com.cherryoj.gatewayservice.judging;

import java.time.Instant;
import java.util.List;

/** 字段以 contracts/web-api.openapi.json 的 JudgeNode / JudgeNodeListData 为准。 */
public final class JudgeNodeDtos {
    private JudgeNodeDtos() {}

    public record JudgeNode(String nodeId, List<String> languages, boolean online, Instant lastSeenAt) {}

    public record JudgeNodeListData(List<JudgeNode> items) {}
}
