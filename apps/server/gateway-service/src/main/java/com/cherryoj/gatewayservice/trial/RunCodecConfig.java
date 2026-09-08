package com.cherryoj.gatewayservice.trial;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Only the bounded custom-run DTO needs to accept a maximally escaped source/input body.
 */
@Configuration(proxyBeanMethods = false)
public class RunCodecConfig implements WebFluxConfigurer {

	public void configureHttpMessageCodecs(ServerCodecConfigurer codecs) {
		var decoder = new JacksonJsonDecoder() {
			@Override
			public boolean canDecode(ResolvableType type, MimeType mime) {
				return type.toClass() == CustomRunController.Request.class && super.canDecode(type, mime);
			}
		};
		decoder.setMaxInMemorySize(2 * 1024 * 1024);
		codecs.customCodecs().register(decoder);
	}

}
