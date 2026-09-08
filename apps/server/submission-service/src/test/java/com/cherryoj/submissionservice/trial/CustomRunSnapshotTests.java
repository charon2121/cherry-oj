package com.cherryoj.submissionservice.trial;

import com.cherryoj.submissionservice.api.*;
import com.cherryoj.submissionservice.integration.SubmissionPrerequisites;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomRunSnapshotTests {

	@Test
	void staleVersionIsRejectedBeforeAnyTrialCall() {
		var prerequisite = mock(SubmissionPrerequisites.class);
		var id = UUID.randomUUID();
		var version = UUID.randomUUID();
		when(prerequisite.snapshot(id.toString(), "cpp"))
			.thenReturn(new SubmissionDtos.Snapshot(id.toString(), UUID.randomUUID().toString(), 2, "title",
					UUID.randomUUID().toString(), "a".repeat(64), "cpp", "ACM", 1));
		var controller = new CustomRunController(prerequisite, new ObjectMapper(), "http://127.0.0.1:1", "unused");
		var jwt = Jwt.withTokenValue("test").header("alg", "none").subject(UUID.randomUUID().toString()).build();
		var request = new CustomRunController.Request(id, version, "cpp", "int main(){}", "");
		var error = assertThrows(SubmissionException.class,
				() -> controller.run(request, new JwtAuthenticationToken(jwt), null, null));
		assertEquals("PROBLEM_VERSION_CHANGED", error.code());
		verify(prerequisite, never()).profile(any());
	}

	@Test
	void forwardsFrozenInputWithoutAnyDownstreamFeatureFlag() throws Exception {
		var prerequisite = mock(SubmissionPrerequisites.class);
		var id = UUID.randomUUID();
		var version = UUID.randomUUID();
		var data = UUID.randomUUID();
		var json = new ObjectMapper();
		when(prerequisite.snapshot(id.toString(), "cpp")).thenReturn(new SubmissionDtos.Snapshot(id.toString(),
				version.toString(), 2, "title", data.toString(), "a".repeat(64), "cpp", "ACM", 1));
		var captured = new java.util.concurrent.atomic.AtomicReference<tools.jackson.databind.JsonNode>();
		var authorization = new java.util.concurrent.atomic.AtomicReference<String>();
		var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/internal/submission/trials", exchange -> {
			captured.set(json.readTree(exchange.getRequestBody().readAllBytes()));
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			byte[] response = "{\"status\":\"COMPLETED\",\"cpuNs\":0,\"memoryBytes\":0,\"stdout\":{\"text\":\"\",\"capturedBytes\":0,\"truncated\":false},\"stderr\":{\"text\":\"\",\"capturedBytes\":0,\"truncated\":false},\"effectiveLimits\":{\"cpuNs\":1,\"clockNs\":1,\"memoryBytes\":1}}"
				.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, response.length);
			try (var out = exchange.getResponseBody()) {
				out.write(response);
			}
		});
		server.start();
		try {
			var token = "work044-synthetic-service-token-0123456789";
			var controller = new CustomRunController(prerequisite, json,
					"http://127.0.0.1:" + server.getAddress().getPort(), token);
			var jwt = Jwt.withTokenValue("test").header("alg", "none").subject(UUID.randomUUID().toString()).build();
			var response = controller.run(new CustomRunController.Request(id, version, "cpp", "int main(){}", ""),
					new JwtAuthenticationToken(jwt), null, null);
			assertEquals("COMPLETED", response.getBody().path("status").asText());
			assertEquals(version.toString(), response.getBody().path("problemVersionId").asText());
			assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
			assertEquals("Bearer " + token, authorization.get());
			assertEquals(data.toString(), captured.get().path("testDataVersionId").asText());
			assertEquals("", captured.get().path("inputText").asText());
			verify(prerequisite, never()).profile(any());
		}
		finally {
			server.stop(0);
		}
	}

}
