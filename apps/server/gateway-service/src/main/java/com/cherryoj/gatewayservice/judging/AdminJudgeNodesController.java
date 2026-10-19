package com.cherryoj.gatewayservice.judging;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiSuccess;
import com.cherryoj.gatewayservice.auth.AdminGatewayAccess;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 管理端总览读判题节点：是否在线、能判的语言、最近心跳。 */
@RestController
@RequestMapping("/api/admin/judging/judge-nodes")
public class AdminJudgeNodesController {
    private final AdminGatewayAccess adminAccess;
    private final JudgingServiceClient judging;

    AdminJudgeNodesController(AdminGatewayAccess adminAccess, JudgingServiceClient judging) {
        this.adminAccess = adminAccess;
        this.judging = judging;
    }

    @GetMapping
    Mono<ResponseEntity<ApiSuccess<JudgeNodeDtos.JudgeNodeListData>>> list(ServerWebExchange exchange) {
        String requestId = ApiRequestContext.requestId(exchange);
        return adminAccess
                .delegatedIdentity(exchange, requestId)
                .flatMap(judging::judgeNodes)
                .onErrorMap(error -> error instanceof ApiProblemException ? error : JudgingServiceClient.unavailable())
                .map(data ->
                        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiSuccess.of(data, requestId)));
    }
}
