package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.judge.JudgeGateway;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class FormalResultTests {
    @Test void runtimeOutputIsDiscardedBeforeLifecycleProjection() {
        var json=JsonMapper.builder().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        var result=json.readValue("""
                {"verdict":"WA","message":"hidden input printed here",
                 "testcaseResults":[{"idx":1,"verdict":"WA","name":"secret name","output":{"stdout":"hidden input"},"diff":{"expected":"secret answer"}}]}
                """,JudgeGateway.JudgeResult.class);
        var safe=FormalWorker.safe(result,3);
        assertEquals(1,safe.get("executedTestcaseCount")); assertEquals(0,safe.get("passedTestcaseCount")); assertEquals(3,safe.get("testcaseCount"));
        String serialized=json.writeValueAsString(safe);
        assertFalse(serialized.contains("hidden")); assertFalse(serialized.contains("secret")); assertFalse(safe.containsKey("message"));
    }
    @Test void presentationErrorRemainsAProgramResult() {
        var safe=FormalWorker.safe(new JudgeGateway.JudgeResult("PE",1L,1L,null,null,
                List.of(new JudgeGateway.TestcaseResult(1,"PE",1L,1L))),1);
        assertEquals("PE",safe.get("verdict"));
        assertEquals(0,safe.get("passedTestcaseCount"));
    }
    @Test void compilationDiagnosticsAreBoundedAndUnknownVerdictCannotBecomeAc() {
        var ce=new JudgeGateway.JudgeResult("CE",null,null,null,"/private/compiler/Main.cpp:3: error\u001b[31m\u0000\n"+"x".repeat(9000),null);
        String message=(String)FormalWorker.safe(ce,1).get("message");
        assertTrue(message.length()<=8192); assertFalse(message.contains("/private")); assertFalse(message.contains("\u001b"));
        assertThrows(FormalFailure.class,() -> FormalWorker.safe(new JudgeGateway.JudgeResult("UNKNOWN",null,null,null),1));
        assertThrows(FormalFailure.class,() -> FormalWorker.safe(new JudgeGateway.JudgeResult("AC",null,null,null,null,
                List.of(new JudgeGateway.TestcaseResult(2,"AC",null,null))),1));
    }
}
