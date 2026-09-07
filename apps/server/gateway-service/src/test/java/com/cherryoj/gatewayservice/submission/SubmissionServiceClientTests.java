package com.cherryoj.gatewayservice.submission;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.auth.DelegatedIdentity;
import com.cherryoj.gatewayservice.auth.InternalRequestFactory;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionServiceClientTests {
    @Test void readsLargeOriginalSourceAndRejectsMismatchedUpstreamIdentity() throws Exception {
        var id=UUID.randomUUID(); var problem=UUID.randomUUID(); var version=UUID.randomUUID();
        String source="\n\t\"".repeat(80_000); // Below 256 KiB raw, much larger after JSON escaping.
        var json=new tools.jackson.databind.ObjectMapper();
        var wrongId=new java.util.concurrent.atomic.AtomicBoolean();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/submissions",exchange -> {
            assertEquals("Bearer delegated",exchange.getRequestHeaders().getFirst("Authorization"));
            assertNull(exchange.getRequestHeaders().getFirst("Cookie"));
            Object result;
            if(exchange.getRequestURI().getPath().endsWith("/source")) {
                result=new SubmissionController.Source(wrongId.get()?UUID.randomUUID():id,problem,version,"cpp",source);
            } else {
                assertTrue(exchange.getRequestURI().getQuery().contains("problemId="+problem));
                assertTrue(exchange.getRequestURI().getQuery().contains("verdict=AC"));
                result=new SubmissionController.HistoryPage(java.util.List.of(),1,20,0,0);
            }
            byte[] bytes=json.writeValueAsBytes(result);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);
            try(var out=exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var client=new SubmissionServiceClient(WebClient.builder(),new InternalRequestFactory(),
                    "http://127.0.0.1:"+server.getAddress().getPort());
            var identity=new DelegatedIdentity("delegated",Instant.now().plusSeconds(60),"req_"+"a".repeat(32));
            assertEquals(source,client.source(identity,id).block().source());
            assertEquals(0,client.history(identity,problem,1,20,"AC").block().totalElements());
            wrongId.set(true);
            assertThrows(ApiProblemException.class,() -> client.source(identity,id).block());
        } finally { server.stop(0); }
    }
    @Test void readsMaximumPageWithEscapedDiagnostics() throws Exception {
        var problem=UUID.randomUUID();
        var row=new SubmissionController.View(UUID.randomUUID(),problem,UUID.randomUUID(),1,
                "题目","cpp","DONE",Instant.now(),"CE",null,null,null,null,null,"\u0001".repeat(8192),Instant.now());
        var body=new tools.jackson.databind.ObjectMapper().writeValueAsBytes(
                new SubmissionController.HistoryPage(java.util.Collections.nCopies(100,row),1,100,100,1));
        assertTrue(body.length>2*1024*1024);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/submissions",exchange -> {
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            try(var out=exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var client=new SubmissionServiceClient(WebClient.builder(),new InternalRequestFactory(),
                    "http://127.0.0.1:"+server.getAddress().getPort());
            var identity=new DelegatedIdentity("delegated",Instant.now().plusSeconds(60),"req_"+"a".repeat(32));
            assertEquals(100,client.history(identity,problem,1,100,null).block().items().size());
        } finally { server.stop(0); }
    }
    @Test void forwardsOnlyTheDelegatedIdentityAndOriginalKeyWithoutRetryingARejectedWrite() throws Exception {
        var calls=new AtomicInteger();
        var key=UUID.randomUUID();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/submissions",exchange -> {
            calls.incrementAndGet();
            assertEquals("Bearer test-delegated-token",exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals(key.toString(),exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            assertNull(exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.getRequestBody().readAllBytes();
            byte[] body="{\"code\":\"INVALID_ACCESS_TOKEN\",\"title\":\"private upstream diagnostic\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type","application/problem+json");
            exchange.sendResponseHeaders(401,body.length);
            try(var output=exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client=new SubmissionServiceClient(WebClient.builder(),new InternalRequestFactory(),
                    "http://127.0.0.1:"+server.getAddress().getPort());
            var identity=new DelegatedIdentity("test-delegated-token",Instant.now().plusSeconds(60),"req_"+"a".repeat(32));
            var error=assertThrows(ApiProblemException.class,() -> client.create(identity,key,
                    new SubmissionController.Create(UUID.randomUUID(),UUID.randomUUID(),"cpp","int main(){}") ).block());
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.status());
            assertFalse(error.getMessage().contains("private upstream"));
            assertEquals(1,calls.get());
        } finally { server.stop(0); }
    }
}
