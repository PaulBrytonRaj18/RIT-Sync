package com.ritsync.config;

import com.ritsync.security.AccessGuard;
import com.ritsync.security.CustomUserDetailsService;
import com.ritsync.security.JwtTokenProvider;
import com.ritsync.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP over WebSocket for live heatmaps.
 * <ul>
 *   <li>The HTTP handshake is public (browsers can't send headers on it); authentication happens on
 *       the STOMP CONNECT frame, which must carry {@code Authorization: Bearer <access token>}.</li>
 *   <li>Clients may only SUBSCRIBE to {@code /topic/session/{id}/heatmap}, and only for sessions they
 *       manage (the course instructor or an admin). Clients cannot SEND.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final Pattern HEATMAP_TOPIC = Pattern.compile("^/topic/session/(\\d+)/heatmap$");

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AccessGuard guard;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOrigins.toArray(String[]::new);
        // SockJS endpoint; it also serves a plain WebSocket at /ws-heatmap/websocket (used by the web client)
        registry.addEndpoint("/ws-heatmap").setAllowedOrigins(origins).withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) return message;

                switch (accessor.getCommand()) {
                    case CONNECT -> accessor.setUser(authenticate(accessor));
                    case SUBSCRIBE -> authorizeSubscription(accessor.getUser(), accessor.getDestination());
                    case SEND -> throw new MessageDeliveryException("Sending messages is not allowed");
                    default -> { }
                }
                return message;
            }
        });
    }

    private Principal authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        String email = token == null ? null : tokenProvider.validateAccessToken(token).orElse(null);
        if (email == null) {
            throw new MessageDeliveryException("Authentication required");
        }
        var user = userDetailsService.loadUserByUsername(email);
        if (!user.isEnabled()) {
            throw new MessageDeliveryException("Account disabled");
        }
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void authorizeSubscription(Principal principal, String destination) {
        if (!(principal instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getPrincipal() instanceof UserPrincipal user)) {
            throw new MessageDeliveryException("Authentication required");
        }
        Matcher m = destination == null ? null : HEATMAP_TOPIC.matcher(destination);
        if (m == null || !m.matches()) {
            throw new MessageDeliveryException("Unknown destination");
        }
        long sessionId = Long.parseLong(m.group(1));
        if (!guard.canManageSession(sessionId, user)) {
            log.warn("User {} denied heatmap subscription to session {}", user.getId(), sessionId);
            throw new MessageDeliveryException("Access denied");
        }
    }
}
