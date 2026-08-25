package com.cherryoj.gatewayservice.api.status;

import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiSuccess;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/api/status")
public class SystemStatusController {

	private static final SystemStatusData READY = new SystemStatusData("gateway-service", "ready");

	@GetMapping
	public ApiSuccess<SystemStatusData> getStatus(ServerWebExchange exchange) {
		return ApiSuccess.of(READY, ApiRequestContext.requestId(exchange));
	}

}
