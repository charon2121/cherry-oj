package com.cherryoj.judgingservice.application;

import com.cherryoj.judgingservice.api.JudgeNodeDtos.*;
import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.config.JudgeNodeProperties;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class JudgeNodeRegistry {
    private final JudgeNodeRepository nodes;
    private final JudgeNodeProperties properties;
    private final Clock clock;
    private final ObjectMapper json;
    public JudgeNodeRegistry(JudgeNodeRepository nodes, JudgeNodeProperties properties, Clock clock, ObjectMapper json) {
        this.nodes = nodes; this.properties = properties; this.clock = clock; this.json = json;
    }
    /** 同一 nodeId 的新进程带着新会话注册即接替旧进程；已被接替的旧会话不能再夺回 nodeId。 */
    @Transactional
    public Lease register(Registration request) {
        validateEndpoint(request.endpoint());
        if (request.languages().stream().distinct().count() != request.languages().size()) {
            throw problem("NODE_METADATA_INVALID");
        }
        nodes.lockRegistry();
        var existing = nodes.find(request.nodeId());
        if (existing != null && !existing.sessionId().equals(request.sessionId())
                && nodes.knownSession(request.nodeId(), request.sessionId())) throw problem("NODE_IDENTITY_CONFLICT");
        var now = now();
        nodes.register(request, json.writeValueAsString(request.languages()), now, now.plus(properties.leaseDuration()));
        LoggerFactory.getLogger(getClass()).info("judge.node.registered nodeId={} sessionId={}", request.nodeId(), request.sessionId());
        return new Lease(request.nodeId(), properties.leaseDuration().toNanos());
    }
    @Transactional
    public Lease heartbeat(Heartbeat request) {
        nodes.lockRegistry();
        var node = nodes.find(request.nodeId());
        if (node == null) throw new JudgingApiException(HttpStatus.NOT_FOUND, "NODE_NOT_REGISTERED", "Node registration required");
        if (!node.sessionId().equals(request.sessionId())) throw problem("NODE_IDENTITY_CONFLICT");
        var now = now();
        if (!node.leaseExpiresAt().isAfter(now)) LoggerFactory.getLogger(getClass()).info("judge.node.recovered nodeId={}", node.nodeId());
        nodes.heartbeat(node.nodeId(), now, now.plus(properties.leaseDuration()));
        return new Lease(node.nodeId(), properties.leaseDuration().toNanos());
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    public static void validateEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/")) || uri.getPort() == 0) throw problem("NODE_ENDPOINT_INVALID");
        } catch (IllegalArgumentException error) { throw problem("NODE_ENDPOINT_INVALID"); }
    }
    private static JudgingApiException problem(String code) { return new JudgingApiException(HttpStatus.CONFLICT, code, "Node registration conflicts with registered identity"); }
}
