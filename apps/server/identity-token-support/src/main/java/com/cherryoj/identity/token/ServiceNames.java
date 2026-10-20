package com.cherryoj.identity.token;

import java.util.Set;
import java.util.regex.Pattern;

/** 服务名：调用令牌的 iss/sub/aud 与头部 kid，也是公钥目录里的文件名前缀；等于各服务的 spring.application.name。 */
public final class ServiceNames {
    public static final String GATEWAY = "gateway-service";
    public static final String USER = "user-service";
    public static final String PROBLEM = "problem-service";
    public static final String SUBMISSION = "submission-service";
    public static final String JUDGING = "judging-service";

    /** 公钥目录必须包含的全部服务。 */
    public static final Set<String> ALL = Set.of(GATEWAY, USER, PROBLEM, SUBMISSION, JUDGING);

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9-]{1,62}$");

    private ServiceNames() {}

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
