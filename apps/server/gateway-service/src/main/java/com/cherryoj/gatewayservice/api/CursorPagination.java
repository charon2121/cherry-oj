package com.cherryoj.gatewayservice.api;

public record CursorPagination(String nextCursor, boolean hasMore) implements ApiPagination {

	public CursorPagination {
		if (nextCursor != null && (nextCursor.isBlank() || nextCursor.length() > 2048)) {
			throw new IllegalArgumentException("nextCursor must contain 1 to 2048 characters");
		}
	}

	@Override
	public String kind() {
		return "cursor";
	}

}
