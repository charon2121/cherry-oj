package com.cherryoj.gatewayservice.problem;

import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiSuccess;
import com.cherryoj.gatewayservice.auth.ActingUser;
import com.cherryoj.gatewayservice.auth.AdminGatewayAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.function.Function;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 管理端"语言"页：读取与保存各语言的 CORE 前缀，原样转发给 problem-service。 */
@RestController
@RequestMapping("/api/admin/languages")
@Validated
public class AdminLanguagesController {

    private final AdminGatewayAccess adminAccess;
    private final ProblemServiceClient problemService;

    AdminLanguagesController(AdminGatewayAccess adminAccess, ProblemServiceClient problemService) {
        this.adminAccess = adminAccess;
        this.problemService = problemService;
    }

    @GetMapping
    Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminLanguageSettingListData>>> list(ServerWebExchange exchange) {
        String requestId = ApiRequestContext.requestId(exchange);
        return admin(exchange, requestId, problemService::listLanguageSettings).map(result -> ok(result, requestId));
    }

    @PutMapping("/{languageId}")
    Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminLanguageSetting>>> update(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{0,31}$") String languageId,
            @Valid @RequestBody UpdateLanguageSettingRequest request,
            ServerWebExchange exchange) {
        String requestId = ApiRequestContext.requestId(exchange);
        return admin(
                        exchange,
                        requestId,
                        identity -> problemService.updateLanguageSetting(identity, languageId, request))
                .map(result -> ok(result, requestId));
    }

    private <T> Mono<T> admin(ServerWebExchange exchange, String requestId, Function<ActingUser, Mono<T>> action) {
        return adminAccess
                .actingUser(exchange, requestId)
                .flatMap(action)
                .onErrorMap(error -> ProblemApiErrors.map(error, false, requestId));
    }

    private static <T> ResponseEntity<ApiSuccess<T>> ok(T body, String requestId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiSuccess.of(body, requestId));
    }

    record UpdateLanguageSettingRequest(
            @NotNull @Size(min = 1, max = 65_536) String solutionPrefix,
            @NotNull @Size(min = 1, max = 64) String expectedUpdatedAt) {}
}
