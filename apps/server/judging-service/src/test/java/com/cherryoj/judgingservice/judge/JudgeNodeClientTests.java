package com.cherryoj.judgingservice.judge;

import static org.assertj.core.api.Assertions.*;
import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.api.JudgingDtos.*;
import com.cherryoj.judgingservice.config.*;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository.Node;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JudgeNodeClientTests {
    @Test void boundedRemoteResponsesAreValidatedAndBodiesNeverLeak() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var status=new java.util.concurrent.atomic.AtomicInteger(200);
        var body=new java.util.concurrent.atomic.AtomicReference<>("{}");
        server.createContext("/internal/judge-node/v1/install",exchange->{
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer secret");
            exchange.getRequestBody().transferTo(java.io.OutputStream.nullOutputStream());
            byte[] bytes=body.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status.get(),bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=new JudgeNodeClient(new JudgeNodeProperties("secret",Duration.ofSeconds(35),"node-remote"),
                new JudgingProperties(null,1000,1000,1000,20,100,Duration.ofSeconds(2),Duration.ofHours(1),false,null),JsonMapper.builder().build());
            var node=new Node("node","environment","session","http://127.0.0.1:"+server.getAddress().getPort(),"fingerprint",LocalDateTime.now().plusSeconds(35));
            var m=new DeploymentMetadata("version","a".repeat(64),new Manifest(1,2,List.of(new ManifestFile("1.in",1,"b".repeat(64)),new ManifestFile("1.out",1,"c".repeat(64)))));
            String valid="{\"nodeId\":\"node\",\"environmentFingerprint\":\"fingerprint\",\"sessionId\":\"session\",\"testDataVersionId\":\"version\",\"sha256\":\""+"a".repeat(64)+"\",\"fileCount\":2}";
            body.set(valid);assertThat(client.install(node,m,new ByteArrayInputStream(new byte[]{1}),null).fileCount()).isEqualTo(2);
            for(String invalid:List.of("not json private",valid.replace("Count\":2","Count\":2.5"),valid.replace("Count\":2","Count\":\"2\""),valid.replace("fingerprint","wrong"),valid.replace("\"node\"","\"wrong\""),valid.replace("\"session\"","\"wrong\""),valid.replace("version","wrong"),valid.replace("a".repeat(64),"b".repeat(64)),valid.replace("Count\":2","Count\":3"))){
                body.set(invalid);assertThatThrownBy(()->client.install(node,m,new ByteArrayInputStream(new byte[]{1}),null)).isInstanceOfSatisfying(JudgingApiException.class,e->assertThat(e.code()).isEqualTo("JUDGE_NODE_RECEIPT_MISMATCH"));
            }
            body.set("private "+"x".repeat(20000));assertThatThrownBy(()->client.install(node,m,new ByteArrayInputStream(new byte[]{1}),null)).hasMessageNotContaining("private");
            body.set("private");status.set(422);assertThatThrownBy(()->client.install(node,m,new ByteArrayInputStream(new byte[]{1}),null)).isInstanceOfSatisfying(JudgingApiException.class,e->assertThat(e.code()).isEqualTo("JUDGE_NODE_DATA_REJECTED"));
            status.set(503);assertThatThrownBy(()->client.install(node,m,new ByteArrayInputStream(new byte[]{1}),null)).isInstanceOfSatisfying(JudgingApiException.class,e->assertThat(e.code()).isEqualTo("JUDGE_NODE_UNREACHABLE"));
        }finally{server.stop(0);}
    }
    @Test void responseHeadersDoNotBypassTheWholeExchangeTimeout() throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        var headers = new java.util.concurrent.CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/judge-node/v1/install", exchange -> {
            exchange.getRequestBody().transferTo(java.io.OutputStream.nullOutputStream());
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            headers.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new JudgeNodeClient(new JudgeNodeProperties("secret", Duration.ofSeconds(35), "node-remote"),
                    new JudgingProperties(null, 1000, 1000, 1000, 20, 100, Duration.ofMillis(500), Duration.ofHours(1), false, null), JsonMapper.builder().build());
            var node = new Node("node", "environment", "session", "http://127.0.0.1:" + server.getAddress().getPort(), "fingerprint", LocalDateTime.now().plusSeconds(35));
            var metadata = new DeploymentMetadata("version", "a".repeat(64), new Manifest(1, 2, List.of(new ManifestFile("1.in", 1, "b".repeat(64)), new ManifestFile("1.out", 1, "c".repeat(64)))));
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThatThrownBy(() -> client.install(node, metadata, new ByteArrayInputStream(new byte[]{1}), null))
                            .isInstanceOfSatisfying(JudgingApiException.class, e -> assertThat(e.code()).isEqualTo("JUDGE_NODE_UNREACHABLE")));
            assertThat(headers.getCount()).isZero();
        } finally { release.countDown(); server.stop(0); }
    }
}
