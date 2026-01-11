package com.kafkachat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketHandlerConfig implements WebSocketConfigurer {
    
    private final RawWebSocketConfig rawWebSocketHandler;
    
    public WebSocketHandlerConfig(RawWebSocketConfig rawWebSocketHandler) {
        this.rawWebSocketHandler = rawWebSocketHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rawWebSocketHandler, "/ws")
                .setAllowedOrigins("*")
                .setAllowedOriginPatterns("*"); // Support ngrok and other dynamic origins
    }
}

