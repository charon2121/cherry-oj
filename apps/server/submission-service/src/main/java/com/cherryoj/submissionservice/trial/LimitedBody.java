package com.cherryoj.submissionservice.trial;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

/** 在订阅阶段限流，不能先聚合不受限正文再检查长度。 */
public final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {

	private final int limit;

	private final CompletableFuture<byte[]> result = new CompletableFuture<>();

	private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

	private Flow.Subscription subscription;

	public LimitedBody(int limit) {
		if (limit < 1)
			throw new IllegalArgumentException("positive body limit required");
		this.limit = limit;
	}

	public CompletionStage<byte[]> getBody() {
		return result;
	}

	public void onSubscribe(Flow.Subscription value) {
		subscription = value;
		value.request(1);
	}

	public void onNext(List<ByteBuffer> buffers) {
		for (var buffer : buffers) {
			if (buffer.remaining() > limit - bytes.size()) {
				subscription.cancel();
				result.completeExceptionally(new IllegalStateException("response too large"));
				return;
			}
			byte[] chunk = new byte[buffer.remaining()];
			buffer.get(chunk);
			bytes.writeBytes(chunk);
		}
		subscription.request(1);
	}

	public void onError(Throwable error) {
		result.completeExceptionally(error);
	}

	public void onComplete() {
		result.complete(bytes.toByteArray());
	}

}
