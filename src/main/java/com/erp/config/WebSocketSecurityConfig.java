package com.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

	@Bean
	AuthorizationManager<Message<?>> messageAuthorizationManager(
			MessageMatcherDelegatingAuthorizationManager.Builder messages) {
		return messages
				.simpSubscribeDestMatchers("/user/queue/messages", "/user/queue/read", "/user/queue/errors", "/user/queue/sync")
					.authenticated()
				.simpTypeMatchers(SimpMessageType.SUBSCRIBE).denyAll()
				.simpDestMatchers("/app/chat.send", "/app/chat.sync").authenticated()
				.simpTypeMatchers(SimpMessageType.MESSAGE).denyAll()
				.nullDestMatcher().authenticated()
				.anyMessage().denyAll()
				.build();
	}
}
