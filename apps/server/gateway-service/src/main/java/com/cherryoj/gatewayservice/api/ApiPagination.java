package com.cherryoj.gatewayservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public sealed interface ApiPagination permits CursorPagination, PagePagination {

	@JsonProperty("kind")
	String kind();

}
