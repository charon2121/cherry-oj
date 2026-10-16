package com.cherryoj.serviceidentity;

import java.util.regex.Pattern;

/**
 * 服务身份名。它出现在服务令牌的 sub（谁在调）与 aud（调谁）里，也是被调方允许名单里写的字面量，
 * 因此与各服务的 spring.application.name 保持一致。
 */
public final class ServiceNames {

	public static final String GATEWAY = "gateway-service";
	public static final String USER = "user-service";
	public static final String PROBLEM = "problem-service";
	public static final String SUBMISSION = "submission-service";
	public static final String JUDGING = "judging-service";

	/** 接收服务令牌的服务（可作为 aud）。gateway 只调别人，不接收服务调用。 */
	public static final java.util.Set<String> RECEIVERS = java.util.Set.of(USER, PROBLEM, SUBMISSION, JUDGING);

	private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9-]{1,62}$");

	private ServiceNames() {
	}

	public static boolean valid(String name) {
		return name != null && NAME.matcher(name).matches();
	}

	public static String require(String name, String field) {
		if (!valid(name)) {
			throw new IllegalArgumentException(field + " must be a service name like judging-service, got: " + name);
		}
		return name;
	}
}
