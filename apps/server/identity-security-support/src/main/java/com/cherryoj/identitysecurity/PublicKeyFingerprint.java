package com.cherryoj.identitysecurity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public final class PublicKeyFingerprint {

	private PublicKeyFingerprint() {
	}

	public static String kid(RSAPublicKey publicKey) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
			return "rsa-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}
}
