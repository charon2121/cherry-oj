package com.cherryoj.gatewayservice.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 异常正文可能含凭据；只保留有界的类型与应用调用位置。 */
record UnexpectedErrorFacts(String errorType, List<String> exceptionTypes, List<String> applicationFrames) {

	private static final int MAX_CAUSES = 8;
	private static final int MAX_FRAMES = 12;
	private static final int MAX_INSPECTED_FRAMES = 256;
	private static final Pattern CLASS_NAME = Pattern.compile(
			"[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
	private static final Pattern METHOD_NAME = Pattern.compile("[A-Za-z0-9_$<>]{1,80}");

	static UnexpectedErrorFacts from(Throwable error) {
		List<String> types = new ArrayList<>();
		List<String> frames = new ArrayList<>();
		Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		Throwable current = error;
		while (current != null && visited.size() < MAX_CAUSES && visited.add(current)) {
			String type = safeClassName(current.getClass().getName());
			if (type != null) {
				types.add(type);
			}
			if (frames.size() < MAX_FRAMES) {
				StackTraceElement[] stack = current.getStackTrace();
				for (int i = 0; i < Math.min(stack.length, MAX_INSPECTED_FRAMES) && frames.size() < MAX_FRAMES; i++) {
					String owner = safeClassName(stack[i].getClassName());
					String method = stack[i].getMethodName();
					if (owner != null && owner.startsWith("com.cherryoj.") && METHOD_NAME.matcher(method).matches()) {
						String frame = owner + "." + method;
						if (!frames.contains(frame)) {
							frames.add(frame);
						}
					}
				}
			}
			current = current.getCause();
		}
		return new UnexpectedErrorFacts(safeClassName(error.getClass().getName()),
				List.copyOf(types), List.copyOf(frames));
	}

	private static String safeClassName(String value) {
		return value.length() <= 200 && CLASS_NAME.matcher(value).matches() ? value : null;
	}
}
