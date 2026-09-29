package com.cherryoj.judgingservice.application;

import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.api.JudgingDtos.*;
import com.cherryoj.judgingservice.judge.JudgeNodeClient;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import java.io.InputStream;
import java.time.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NodeDeploymentService {
    private final JudgeNodeRepository nodes;
    private final JudgeNodeClient client;
    private final Clock clock;
    private final TransactionTemplate transactions;
    public NodeDeploymentService(JudgeNodeRepository nodes, JudgeNodeClient client,
                                 Clock clock, PlatformTransactionManager manager) {
        this.nodes = nodes; this.client = client; this.clock = clock; transactions = new TransactionTemplate(manager);
    }
    public Deployment deploy(DeploymentMetadata metadata, InputStream archive, String traceId) {
        var online = nodes.online(now());
        if (online.isEmpty()) throw noOnline();
        var node = online.getFirst();
        if (nodes.hashConflict(node.nodeId(), metadata.testDataVersionId(), metadata.expectedSha256())) throw new JudgingApiException(HttpStatus.CONFLICT, "DEPLOYMENT_HASH_CONFLICT", "相同版本不能部署不同摘要。");
        // HTTP 明确在事务外，回执丢失可以重试。
        Long previousReceipt = nodes.receiptVersion(node, metadata.testDataVersionId());
        com.cherryoj.judgingservice.api.JudgeNodeDtos.Receipt receipt;
        try {
            receipt = client.install(node, metadata, archive, traceId);
        } catch (JudgingApiException error) {
            if ("JUDGE_NODE_DATA_REJECTED".equals(error.code()) || "JUDGE_NODE_RECEIPT_MISMATCH".equals(error.code())) {
                nodes.invalidateReceipt(node, metadata.testDataVersionId(), previousReceipt);
            }
            LoggerFactory.getLogger(getClass()).warn("judge.node.deployment.failed nodeId={} testDataVersionId={} code={}", node.nodeId(), metadata.testDataVersionId(), error.code());
            throw error;
        }
        return transactions.execute(status -> {
            nodes.lockRegistry();
            var current = nodes.find(node.nodeId());
            if (current == null || !current.sessionId().equals(node.sessionId()) || !current.leaseExpiresAt().isAfter(now())) throw JudgeNodeClient.unreachable();
            if (nodes.hashConflict(node.nodeId(), metadata.testDataVersionId(), metadata.expectedSha256())) throw JudgeNodeClient.mismatch();
            var now = now();
            nodes.recordReady(node, metadata.testDataVersionId(), receipt.sha256(), receipt.fileCount(), now);
            LoggerFactory.getLogger(getClass()).info("judge.node.deployment.ready nodeId={} testDataVersionId={}", node.nodeId(), metadata.testDataVersionId());
            return new Deployment(metadata.testDataVersionId(), node.nodeId(), metadata.expectedSha256(), "READY", receipt.sha256(), now, null, now, 0);
        });
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    public static JudgingApiException noOnline() { return new JudgingApiException(HttpStatus.SERVICE_UNAVAILABLE, "NO_ONLINE_JUDGE_NODE", "当前没有在线判题节点，请启动节点并等待注册后重试。"); }
}
