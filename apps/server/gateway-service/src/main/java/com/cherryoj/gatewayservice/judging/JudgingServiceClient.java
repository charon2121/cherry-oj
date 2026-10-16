package com.cherryoj.gatewayservice.judging;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.auth.DelegatedIdentity;
import com.cherryoj.gatewayservice.auth.InternalRequestFactory;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** 读 judging-service 的管理端节点状态；带管理员委托身份，由 judging-service 再校验 ADMIN。 */
@Component
class JudgingServiceClient {
    private static final int MAX_NODES = 1000;
    private final WebClient client;
    private final InternalRequestFactory requests;

    JudgingServiceClient(WebClient.Builder builder, InternalRequestFactory requests,
            @Value("${cherry.gateway.judging-service-url:http://127.0.0.1:8084}") String url) {
        this.client = builder.clone().baseUrl(url).build();
        this.requests = requests;
    }

    Mono<JudgeNodeDtos.JudgeNodeListData> judgeNodes(DelegatedIdentity identity) {
        return requests.authenticated(client.get().uri("/internal/admin/judge-nodes"), identity)
                .exchangeToMono(response -> response.statusCode().value() == 200
                        ? response.bodyToMono(JudgeNodeDtos.JudgeNodeListData.class)
                        : Mono.error(unavailable()))
                .map(JudgingServiceClient::validate)
                .switchIfEmpty(Mono.error(unavailable()))
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(error -> error instanceof ApiProblemException ? error : unavailable());
    }

    private static JudgeNodeDtos.JudgeNodeListData validate(JudgeNodeDtos.JudgeNodeListData data) {
        if (data.items() == null || data.items().size() > MAX_NODES) throw unavailable();
        for (var node : data.items()) {
            if (node.nodeId() == null || !node.nodeId().matches("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$")
                    || node.languages() == null || node.lastSeenAt() == null) throw unavailable();
        }
        return data;
    }

    static ApiProblemException unavailable() {
        return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "JUDGING_UNAVAILABLE",
                "判题服务暂不可用", "暂时读不到判题节点状态，请稍后重试。");
    }
}
