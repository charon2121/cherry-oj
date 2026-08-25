package com.cherryoj.gatewayservice.api;

public record ApiSuccess<T>(T data, ApiMeta meta) {

	public static <T> ApiSuccess<T> of(T data, String requestId) {
		return new ApiSuccess<>(data, new ApiMeta(requestId));
	}

	public static <T> ApiSuccess<T> of(T data, String requestId, ApiPagination pagination) {
		return new ApiSuccess<>(data, new ApiMeta(requestId, pagination));
	}

}
