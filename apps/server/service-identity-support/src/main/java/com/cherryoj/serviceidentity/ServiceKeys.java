package com.cherryoj.serviceidentity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 读取 PEM 格式的服务密钥：私钥 PKCS#8（BEGIN PRIVATE KEY），公钥 X.509（BEGIN PUBLIC KEY），
 * 即 openssl genpkey / openssl pkey -pubout 的输出。位置写作 file:/绝对路径、classpath:路径或普通路径。
 */
public final class ServiceKeys {

	private static final int MIN_MODULUS_BITS = 2048;

	private ServiceKeys() {
	}

	public static RSAPrivateKey privateKey(String location) {
		byte[] der = der(read(location), "PRIVATE KEY", location);
		try {
			var key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
			requireStrength(key.getModulus().bitLength(), location);
			return key;
		}
		catch (GeneralSecurityException | ClassCastException error) {
			throw new IllegalStateException("service private key is not a PKCS#8 RSA key: " + location, error);
		}
	}

	public static RSAPublicKey publicKey(String location) {
		byte[] der = der(read(location), "PUBLIC KEY", location);
		try {
			var key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
			requireStrength(key.getModulus().bitLength(), location);
			return key;
		}
		catch (GeneralSecurityException | ClassCastException error) {
			throw new IllegalStateException("service public key is not an X.509 RSA key: " + location, error);
		}
	}

	private static void requireStrength(int bits, String location) {
		if (bits < MIN_MODULUS_BITS) {
			throw new IllegalStateException("service key must be at least " + MIN_MODULUS_BITS + " bits: " + location);
		}
	}

	private static byte[] der(String pem, String type, String location) {
		String begin = "-----BEGIN " + type + "-----";
		String end = "-----END " + type + "-----";
		int start = pem.indexOf(begin);
		int stop = pem.indexOf(end);
		if (start < 0 || stop < start) {
			throw new IllegalStateException("expected a PEM block '" + begin + "' in " + location);
		}
		String body = pem.substring(start + begin.length(), stop).replaceAll("\\s", "");
		return Base64.getDecoder().decode(body);
	}

	private static String read(String location) {
		if (location == null || location.isBlank()) {
			throw new IllegalStateException("service key location is not configured");
		}
		try {
			if (location.startsWith("classpath:")) {
				String resource = location.substring("classpath:".length()).replaceFirst("^/", "");
				try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
					if (in == null) throw new IllegalStateException("service key not found on classpath: " + location);
					return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
				}
			}
			String path = location.startsWith("file:") ? location.substring("file:".length()) : location;
			return Files.readString(Path.of(path), StandardCharsets.US_ASCII);
		}
		catch (IOException error) {
			throw new IllegalStateException("cannot read service key: " + location, error);
		}
	}
}
