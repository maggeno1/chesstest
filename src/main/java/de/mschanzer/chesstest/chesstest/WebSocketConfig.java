package de.mschanzer.chesstest.chesstest;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // Aktiviert WebSocket-Nachrichtenbroker-Funktionalität
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Defines the prefix for destinations for messages from clients to the server.
        // E.g., clients send messages to /app/hello
        config.setApplicationDestinationPrefixes("/app");
        // Defines the prefix for destinations to which the server sends messages.
        // E.g., server sends messages to /topic/greetings or /queue/messages
        config.enableSimpleBroker("/topic"); // Für einfache Pub-Sub-Nachrichten
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Registers the STOMP endpoint. Clients connect to this URL.
        // .withSockJS() adds SockJS fallback options for browsers that don't support WebSockets.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
        // .setAllowedOriginPatterns("*") ist für die Entwicklung; in Produktion spezifische Origins angeben
    }
}