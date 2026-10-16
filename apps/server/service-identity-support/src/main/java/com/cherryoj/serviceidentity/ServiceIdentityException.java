package com.cherryoj.serviceidentity;

/** 服务身份校验或换取失败。reason 是稳定的原因码，进日志；不含令牌内容。 */
public final class ServiceIdentityException extends RuntimeException {

	private final String reason;

	public ServiceIdentityException(String reason, String message) {
		super(message);
		this.reason = reason;
	}

	public ServiceIdentityException(String reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public String reason() {
		return reason;
	}
}
