package com.cherryoj.gatewayservice.api;

public record PagePagination(
		int page, int size, long totalElements, int totalPages) implements ApiPagination {

	public PagePagination {
		if (page < 1 || size < 1 || totalElements < 0 || totalPages < 0) {
			throw new IllegalArgumentException("page pagination values are outside the public contract");
		}
	}

	@Override
	public String kind() {
		return "page";
	}

}
