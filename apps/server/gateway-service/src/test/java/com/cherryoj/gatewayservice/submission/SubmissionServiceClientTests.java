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
