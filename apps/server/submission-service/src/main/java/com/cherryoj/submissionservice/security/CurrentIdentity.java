package com.cherryoj.submissionservice.security;

import java.util.Set;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record CurrentIdentity(String userId, Set<String> roles) {
	public static CurrentIdentity from(JwtAuthenticationToken authentication) {
		return new CurrentIdentity(authentication.getToken().getSubject(),
				authentication.getAuthorities().stream()
						.map(authority -> authority.getAuthority())
						.filter(authority -> authority.startsWith("ROLE_"))
						.map(authority -> authority.substring(5))
						.collect(java.util.stream.Collectors.toUnmodifiableSet()));
	}
}
