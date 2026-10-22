package com.erp.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private static final String CONNECTED = WebSocketConfig.class.getName() + ".connected";

	private TaskScheduler messageBrokerTaskScheduler;
	private MessageChannel clientOutboundChannel;

	@Autowired
	public void setMessageBrokerInfrastructure(@Lazy TaskScheduler taskScheduler,
			@Lazy @Qualifier("clientOutboundChannel") MessageChannel outboundChannel) {
		this.messageBrokerTaskScheduler = taskScheduler;
		this.clientOutboundChannel = outboundChannel;
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
				Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(message.getHeaders());
				boolean rejected = attributes != null && Boolean.FALSE.equals(attributes.get(CONNECTED));
				boolean requiresConnect = type == SimpMessageType.MESSAGE || type == SimpMessageType.SUBSCRIBE;
				if ((type == SimpMessageType.CONNECT && rejected)
						|| (requiresConnect && (attributes == null || !Boolean.TRUE.equals(attributes.get(CONNECTED))))) {
					reject(message);
					throw new AccessDeniedException("STOMP CONNECT must complete before sending or subscribing.");
				}
				return message;
			}

			@Override
			public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent,
					Exception exception) {
				if (exception != null) reject(message);
				else if (sent && SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.CONNECT) {
					Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(message.getHeaders());
					if (attributes != null) attributes.putIfAbsent(CONNECTED, Boolean.TRUE);
				}
			}
		});
	}

	private void reject(Message<?> message) {
		Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(message.getHeaders());
		if (attributes != null && Boolean.FALSE.equals(attributes.put(CONNECTED, Boolean.FALSE))) return;
		String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
		if (sessionId == null) return;
		StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
		error.setSessionId(sessionId);
		error.setMessage("메신저 요청을 처리할 수 없습니다.");
		// Ordered inbound processing swallows preSend failures; the native outbound ERROR path closes the socket.
		clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], error.getMessageHeaders()));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/queue")
				.setTaskScheduler(messageBrokerTaskScheduler)
				.setHeartbeatValue(new long[] {10000, 10000});
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
		registry.setPreservePublishOrder(true);
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.setPreserveReceiveOrder(true);
		registry.addEndpoint("/ws");
	}
}
