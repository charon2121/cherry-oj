package com.cherryoj.submissionservice.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.filter.OncePerRequestFilter;

final class IdentityKeyFailureFilter extends OncePerRequestFilter {
	private final SecurityProblemWriter problems;
	IdentityKeyFailureFilter(SecurityProblemWriter problems) { this.problems = problems; }

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		try { chain.doFilter(request, response); }
		catch (AuthenticationServiceException error) { problems.commence(request, response, error); }
	}
}
