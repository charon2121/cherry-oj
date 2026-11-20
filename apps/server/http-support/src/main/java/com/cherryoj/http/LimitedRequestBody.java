package com.cherryoj.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;

public final class LimitedRequestBody {
    private LimitedRequestBody() {}

    /** 读取实际字节，Content-Length 缺失时也在 JSON 解码前执行同样的上限。 */
    public static HttpInputMessage read(
            HttpInputMessage input, int maxBytes, Supplier<? extends RuntimeException> tooLarge) throws IOException {
        byte[] bytes = input.getBody().readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw tooLarge.get();
        }
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public HttpHeaders getHeaders() {
                return input.getHeaders();
            }
        };
    }
}
