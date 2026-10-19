package com.cherryoj.gatewayservice.judging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.auth.AdminGatewayAccess;
import com.cherryoj.gatewayservice.auth.DelegatedIdentity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class AdminJudgeNodesControllerTests {

    private static final DelegatedIdentity IDENTITY = new DelegatedIdentity(
            "delegated-access-token", Instant.parse("2026-10-16T06:00:00Z"), "req_0123456789abcdef0123456789abcdef");

    @Test
    void passesTheAdminIdentityToJudgingAndReturnsNoStoreNodes() {
        AdminGatewayAccess access = mock(AdminGatewayAccess.class);
        JudgingServiceClient judging = mock(JudgingServiceClient.class);
        when(access.delegatedIdentity(any(), anyString())).thenReturn(Mono.just(IDENTITY));
        var nodes = new JudgeNodeDtos.JudgeNodeListData(List.of(new JudgeNodeDtos.JudgeNode(
                "judge-local-1", List.of("cpp"), true, Instant.parse("2026-10-16T04:00:00Z"))));
        when(judging.judgeNodes(IDENTITY)).thenReturn(Mono.just(nodes));

        var response =
                new AdminJudgeNodesController(access, judging).list(exchange()).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().data()).isEqualTo(nodes);
        verify(judging).judgeNodes(IDENTITY);
    }

    @Test
    void forbiddenAdminAccessIsNotMaskedAsAnOutage() {
        AdminGatewayAccess access = mock(AdminGatewayAccess.class);
        JudgingServiceClient judging = mock(JudgingServiceClient.class);
        when(access.delegatedIdentity(any(), anyString()))
                .thenReturn(
                        Mono.error(new ApiProblemException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问", "当前身份无权执行此操作。")));

        assertThatThrownBy(() -> new AdminJudgeNodesController(access, judging)
                        .list(exchange())
                        .block())
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void unexpectedDownstreamFailureBecomesJudgingUnavailable() {
        AdminGatewayAccess access = mock(AdminGatewayAccess.class);
        JudgingServiceClient judging = mock(JudgingServiceClient.class);
        when(access.delegatedIdentity(any(), anyString())).thenReturn(Mono.just(IDENTITY));
        when(judging.judgeNodes(IDENTITY)).thenReturn(Mono.error(new IllegalStateException("connection refused")));

        assertThatThrownBy(() -> new AdminJudgeNodesController(access, judging)
                        .list(exchange())
                        .block())
                .isInstanceOfSatisfying(ApiProblemException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.code()).isEqualTo("JUDGING_UNAVAILABLE");
                });
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/judging/judge-nodes").build());
    }
}
