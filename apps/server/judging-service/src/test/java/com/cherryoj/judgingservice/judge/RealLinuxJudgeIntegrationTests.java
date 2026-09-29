package com.cherryoj.judgingservice.judge;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.judgingservice.config.JudgingProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "CHERRY_REAL_JUDGE_URL", matches = "https?://.+")
class RealLinuxJudgeIntegrationTests {

    @Test
    void aPlusBReturnsAcWaAndCeThroughTheProductionHttpClient() throws Exception {
        String endpoint = System.getenv("CHERRY_REAL_JUDGE_URL");
        var properties = new JudgingProperties(1, Duration.ofSeconds(60));
        var client = new HttpJudgeGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);

        var ac = client.judge(endpoint, request("""
                #include <iostream>
                int main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}
                """), null);
        var wa = client.judge(endpoint, request("""
                #include <iostream>
                int main(){long long a,b;std::cin>>a>>b;std::cout<<a-b<<"\\n";}
                """), null);
        var ce = client.judge(endpoint, request("int main( {"), null);

        assertThat(ac.verdict()).isEqualTo("AC");
        assertThat(wa.verdict()).isEqualTo("WA");
        assertThat(ce.verdict()).isEqualTo("CE");
        assertThat(ac.cpuNs()).isNotNegative();
        assertThat(ac.memoryBytes()).isNotNegative();
    }

    private static JudgeGateway.JudgeRequest request(String source) {
        return new JudgeGateway.JudgeRequest(UUID.randomUUID().toString(), "a-plus-b",
                "a-plus-b-v1", System.getenv().getOrDefault("CHERRY_REAL_JUDGE_TEST_DATA_VERSION_ID", "a-plus-b"), "cpp", source,
                new JudgeGateway.Limits(1_000_000_000L, 268_435_456L, null), "submit");
    }
}
