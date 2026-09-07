package com.cherryoj.judgingservice.judge;

import com.cherryoj.judgingservice.config.JudgingProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class HttpJudgeGatewaySizeTests {
    @Test void largeLegalWrongAnswerResponseKeepsItsVerdictAndDiscardsPrivateOutput() throws Exception {
        String cases=IntStream.rangeClosed(1,300).mapToObj(i -> "{\"idx\":"+i
                +",\"verdict\":\"WA\",\"output\":{\"excerpt\":\""+"x".repeat(4096)+"\"}}")
                .collect(Collectors.joining(","));
        byte[] body=("{\"verdict\":\"WA\",\"environmentFingerprint\":\"f\",\"caseResults\":["+cases+"]}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.length>1_048_576);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/judge",exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200,body.length);
            try(var output=exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var properties=new JudgingProperties(Path.of("."),1,1,1,1,1,Duration.ofSeconds(10),Duration.ofHours(1),false,null);
            var gateway=new HttpJudgeGateway(HttpClient.newHttpClient(),JsonMapper.builder()
                    .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(),properties);
            var result=gateway.judge("http://127.0.0.1:"+server.getAddress().getPort(),
                    new JudgeGateway.JudgeRequest("s","p","v","d","cpp","source",new JudgeGateway.Limits(1,1,null),"submit"),null);
            assertEquals("WA",result.verdict());
            assertEquals(300,result.caseResults().size());
            assertFalse(new tools.jackson.databind.ObjectMapper().writeValueAsString(result).contains("excerpt"));
        } finally { server.stop(0); }
    }
}
