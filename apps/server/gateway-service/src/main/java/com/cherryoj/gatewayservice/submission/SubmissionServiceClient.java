package com.cherryoj.gatewayservice.submission;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.auth.DelegatedIdentity;
import com.cherryoj.gatewayservice.auth.InternalRequestFactory;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
final class SubmissionServiceClient {
    private final WebClient client;
    private final InternalRequestFactory requests;
    SubmissionServiceClient(WebClient.Builder builder,InternalRequestFactory requests,
            @Value("${cherry.gateway.submission-service-url:http://127.0.0.1:8083}") String url) {
        // A page can contain 100 diagnostics of 8 KiB, up to six JSON bytes per escaped character.
        this.client=builder.clone().baseUrl(url).codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024)).build();
        this.requests=requests;
    }
    Mono<ResponseEntity<SubmissionController.View>> create(DelegatedIdentity identity,UUID key,SubmissionController.Create body) {
        return requests.authenticated(client.post().uri("/api/submissions").header("Idempotency-Key",key.toString()),identity)
                .bodyValue(body).exchangeToMono(response -> {
                    if(response.statusCode().value()!=200 && response.statusCode().value()!=201) return failure(response);
                    return response.bodyToMono(SubmissionController.View.class).map(SubmissionServiceClient::validate)
                            .map(view -> ResponseEntity.status(response.statusCode()).body(view));
                }).switchIfEmpty(Mono.error(unavailable())).timeout(Duration.ofSeconds(20))
                .onErrorMap(SubmissionServiceClient::map);
    }
    Mono<SubmissionController.View> read(DelegatedIdentity identity,String path) {
        return requests.authenticated(client.get().uri(path),identity).exchangeToMono(response -> {
            if(response.statusCode().value()!=200) return failure(response);
            return response.bodyToMono(SubmissionController.View.class).map(SubmissionServiceClient::validate);
        }).switchIfEmpty(Mono.error(unavailable())).timeout(Duration.ofSeconds(10)).onErrorMap(SubmissionServiceClient::map);
    }
    Mono<SubmissionController.HistoryPage> history(DelegatedIdentity identity,UUID problemId,int page,int size,String verdict) {
        var request=client.get().uri(builder -> {
            builder.path("/api/submissions").queryParam("problemId",problemId).queryParam("page",page).queryParam("size",size);
            if(verdict!=null) builder.queryParam("verdict",verdict);
            return builder.build();
        });
        return requests.authenticated(request,identity).exchangeToMono(response -> {
            if(response.statusCode().value()!=200) return failure(response);
            return response.bodyToMono(SubmissionController.HistoryPage.class).map(result -> {
                if(result.items()==null || result.items().size()>size || result.page()!=page || result.size()!=size
                        || result.totalElements()<0 || result.totalPages()<0) throw unavailable();
                result.items().forEach(view -> {
                    validate(view);
                    if(!problemId.equals(view.problemId())) throw unavailable();
                });
                return result;
            });
        }).switchIfEmpty(Mono.error(unavailable())).timeout(Duration.ofSeconds(10)).onErrorMap(SubmissionServiceClient::map);
    }
    Mono<SubmissionController.Source> source(DelegatedIdentity identity,UUID id) {
        return requests.authenticated(client.get().uri("/api/submissions/{id}/source",id),identity).exchangeToMono(response -> {
            if(response.statusCode().value()!=200) return failure(response);
            return response.bodyToMono(SubmissionController.Source.class).map(source -> {
                if(!id.equals(source.submissionId()) || source.problemId()==null || source.problemVersionId()==null
                        || !"cpp".equals(source.languageId()) || source.source()==null
                        || source.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>262144) throw unavailable();
                return source;
            });
        }).switchIfEmpty(Mono.error(unavailable())).timeout(Duration.ofSeconds(10)).onErrorMap(SubmissionServiceClient::map);
    }
    private static SubmissionController.View validate(SubmissionController.View view) {
        if(view.id()==null || view.problemId()==null || view.problemVersionId()==null || view.problemTitle()==null
                || view.createdAt()==null || !"cpp".equals(view.languageId()) || view.status()==null
                || !Set.of("PENDING","JUDGING","DONE").contains(view.status())
                || ("DONE".equals(view.status()) && (view.verdict()==null
                    || !Set.of("AC","WA","PE","TLE","MLE","OLE","RE","CE","SE").contains(view.verdict())))) throw unavailable();
        return view;
    }
    private static <T> Mono<T> failure(ClientResponse response) {
        int status=response.statusCode().value();
        return response.bodyToMono(ErrorBody.class).defaultIfEmpty(new ErrorBody("SUBMISSION_UNAVAILABLE"))
                .flatMap(error -> {
                    String code=error.code();
                    String detail=switch(code==null?"":code) {
                        case "PROBLEM_NOT_AVAILABLE" -> "题目当前不可提交，请返回题库确认公开状态。";
                        case "PROBLEM_VERSION_CHANGED" -> "题目版本已更新，请打开新版本后重新提交。";
                        case "IDEMPOTENCY_CONFLICT" -> "此请求对应的代码不同，请先恢复原提交结果。";
                        case "SOURCE_TOO_LARGE" -> "源码超过 256 KiB，请缩减后提交。";
                        case "SUBMISSIONS_PAUSED" -> "暂未开放新提交，请稍后重试；已有结果仍可查询。";
                        default -> status==404 ? "提交不存在或不属于当前账号。" : status==422
                                ? "题目尚未就绪或请求不符合提交要求，请检查后重试。" : "提交服务暂不可用，请先确认原请求结果。";
                    };
                    HttpStatus publicStatus=Set.of(400,403,404,409,413,422,429).contains(status)?HttpStatus.valueOf(status):HttpStatus.SERVICE_UNAVAILABLE;
                    String safeCode=code!=null && code.matches("[A-Z][A-Z0-9_]{0,63}")?code:"SUBMISSION_UNAVAILABLE";
                    return Mono.error(new ApiProblemException(publicStatus,safeCode,"提交请求未完成",detail));
                });
    }
    private record ErrorBody(String code) {}
    private static Throwable map(Throwable error) { return error instanceof ApiProblemException?error:unavailable(); }
    private static ApiProblemException unavailable() {
        return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE,"SUBMISSION_UNAVAILABLE","提交服务暂不可用",
                "暂时无法确认结果，请先查询原请求，避免重复提交。");
    }
}
