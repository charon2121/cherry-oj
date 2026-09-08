package com.cherryoj.judgingservice.operations;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 独立运维入口，不创建应用上下文，不启动服务、worker 或执行迁移。 */
public final class EnvironmentCommand {

	private EnvironmentCommand() {
	}

	public static void main(String[] args) {
		if (!(args.length == 1 && args[0].equals("list")) && !(args.length == 6 && args[0].equals("switch"))) {
			System.err.println(
					"Usage: judge-environment list | switch CURRENT_ID CURRENT_VERSION TARGET_ID TARGET_VERSION REASON");
			System.exit(2);
		}
		try {
			var environment = new StandardEnvironment();
			ConfigDataEnvironmentPostProcessor.applyTo(environment);
			var source = new DriverManagerDataSource(environment.getRequiredProperty("spring.datasource.url"),
					environment.getRequiredProperty("spring.datasource.username"),
					environment.getRequiredProperty("spring.datasource.password"));
			var operations = new EnvironmentOperations(source);
			if (args[0].equals("switch")) {
				operations.switchEnvironment(args[1], Long.parseLong(args[2]), args[3], Long.parseLong(args[4]),
						args[5]);
				System.out
					.println("Environment switched. Deploy and calibrate in the target environment before publishing.");
			}
			operations.list().forEach(System.out::println);
		}
		catch (IllegalStateException error) {
			// 配置异常可能包含 JDBC URL 或秘密；仅输出我们定义的稳定操作错误码。
			String message = error.getMessage();
			System.err.println(
					message != null && message.matches("[A-Z_]{1,80}") ? message : "CONFIGURATION_OR_SWITCH_FAILED");
			System.exit(1);
		}
		catch (Exception error) {
			System.err.println("ENVIRONMENT_OPERATION_FAILED (" + error.getClass().getSimpleName() + ")");
			System.exit(1);
		}
	}

}
