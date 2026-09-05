package com.cherryoj.identitysecurity;

public enum IdentityFailureReason {
	MISSING_BEARER,
	MALFORMED_TOKEN,
	EXPIRED_TOKEN,
	UNKNOWN_KEY,
	BAD_SIGNATURE,
	INVALID_CLAIMS,
	KEY_SERVICE_UNAVAILABLE,
	UNKNOWN
}
