package com.cherryoj.judgingservice.trial;

import com.cherryoj.judgingservice.api.*;
import com.cherryoj.judgingservice.formal.FormalProperties;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrialControllerTests {

	@Test
	void trialUsesOnlySuppliedInputAndKeepsIndependentStreams() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var payload = new AtomicReference<>(
				"{\"verdict\":\"RAN\",\"environmentFingerprint\":\"fingerprint\",\"caseResults\":[{\"idx\":1,\"verdict\":\"RAN\",\"cpuNs\":0,\"memoryBytes\":0,\"output\":{\"excerpt\":\"\",\"bytes\":0},\"stderr\":{\"excerpt\":\"debug\",\"bytes\":5}}]}");
		var json = new ObjectMapper();
		server.createContext("/judge", exchange -> {
			var request = json.readTree(exchange.getRequestBody().readAllBytes());
			assertEquals("trial", request.path("mode").asText());
			assertEquals(1, request.path("cases").size());
			assertFalse(request.path("cases").get(0).has("expected"));
			assertEquals("", request.path("cases").get(0).path("input").asText());
			byte[] bytes = payload.get().getBytes();
			exchange.sendResponseHeaders(200, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		var profiles = mock(SubmissionExecutionProfileController.class);
		var nodes = mock(JudgeNodeRepository.class);
		var problem = UUID.randomUUID();
		var version = UUID.randomUUID();
		var data = UUID.randomUUID();
		var environment = UUID.randomUUID().toString();
		when(profiles.resolve(any())).thenReturn(new SubmissionExecutionProfileController.Profile(version.toString(),
				data.toString(), "cpp", environment, "fingerprint", "calibration",
				new SubmissionExecutionProfileController.Limits(1000000000, 67108864, 2000000000L), 3000000000L));
		var node = mock(JudgeNodeRepository.Node.class);
		when(node.fingerprint()).thenReturn("fingerprint");
		when(node.endpoint()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
		when(nodes.ready(any(), any(), any(), any())).thenReturn(node);
		var controller = new TrialController(profiles, nodes, mock(FormalProperties.class), json,
				HttpClient.newHttpClient());
		var request = new TrialController.Request(problem, version, data, "a".repeat(64), "cpp", "int main(){}", "",
				System.currentTimeMillis() + 45000);
		try {
			var result = controller.run(request, null).getBody();
			assertEquals("COMPLETED", result.status());
			assertEquals("debug", result.stderr().text());
			assertEquals("", result.stdout().text());
			payload.set("{\"verdict\":\"RAN\",\"environmentFingerprint\":\"wrong\"}");
			assertThrows(JudgingApiException.class, () -> controller.run(request, null));
			payload.set(
					"{\"verdict\":\"CE\",\"environmentFingerprint\":\"fingerprint\",\"message\":\"/private/build/main.cpp: broken\"}");
			var ce = controller.run(request, null).getBody();
			assertEquals("COMPILE_ERROR", ce.status());
			assertNull(ce.cpuNs());
			assertFalse(ce.compileDiagnostic().contains("/private"));
			var expired = new TrialController.Request(problem, version, data, "a".repeat(64), "cpp", "int main(){}", "",
					1);
			assertThrows(JudgingApiException.class, () -> controller.run(expired, null));
		}
		finally {
			controller.close();
			server.stop(0);
		}
	}

	@Test
	void utf8ClippingNeverSplitsCharacters() {
		assertEquals("中", TrialController.clip("中文", 4));
	}

}
