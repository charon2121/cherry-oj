package com.cherryoj.identitysecurity;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

public final class IdentityFailureClassifier {

	private IdentityFailureClassifier() {
	}

	public static IdentityFailureReason classify(HttpServletRequest request, Throwable error) {
		String details = failureDetails(error);
		if (containsAny(details, "resourceaccessexception", "remotekeysourceexception",
				"jwtdecoderinitializationexception", "retrieve the remote jwk set",
				"couldn't retrieve remote jwk set", "connection refused", "connect timed out")) {
			return IdentityFailureReason.KEY_SERVICE_UNAVAILABLE;
		}
		String authorization = request == null ? null : request.getHeader("Authorization");
		if (request != null && (authorization == null || authorization.isBlank())) {
			return IdentityFailureReason.MISSING_BEARER;
		}
		if (containsAny(details, "expired", "expiresat")) {
			return IdentityFailureReason.EXPIRED_TOKEN;
		}
		if (containsAny(details, "no matching key", "unknown kid", "key id", "jwk set did not contain")) {
			return IdentityFailureReason.UNKNOWN_KEY;
		}
		if (containsAny(details, "invalid signature", "signature verification failed")) {
			return IdentityFailureReason.BAD_SIGNATURE;
		}
		if (containsAny(details, "malformed", "invalid serialized", "invalid jwt serialization",
				"invalid unsecured", "unexpected number of base64url")) {
			return IdentityFailureReason.MALFORMED_TOKEN;
		}
		if (containsAny(details, "invalid issuer", "invalid audience", "required identity claims",
				"issued-at", "timestamp")) {
			return IdentityFailureReason.INVALID_CLAIMS;
		}
		return IdentityFailureReason.UNKNOWN;
	}

	private static String failureDetails(Throwable error) {
		StringBuilder result = new StringBuilder();
		for (Throwable current = error; current != null; current = current.getCause()) {
			result.append(current.getClass().getName()).append(' ');
			if (current.getMessage() != null) {
				result.append(current.getMessage()).append(' ');
			}
		}
		return result.toString().toLowerCase(Locale.ROOT);
	}

	private static boolean containsAny(String value, String... needles) {
		for (String needle : needles) {
			if (value.contains(needle)) {
				return true;
			}
		}
		return false;
	}
}
