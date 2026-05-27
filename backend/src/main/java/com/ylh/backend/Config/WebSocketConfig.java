package com.ylh.backend.Config;

import com.ylh.backend.Socket.ScreenWebSocketHandler;
import com.ylh.backend.Socket.ScreenStreamManager;
import com.ylh.backend.Service.HostInfoService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ScreenStreamManager screenStreamManager;
    private final HostInfoService hostInfoService;

    public WebSocketConfig(ScreenStreamManager screenStreamManager, HostInfoService hostInfoService) {
        this.screenStreamManager = screenStreamManager;
        this.hostInfoService = hostInfoService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new ScreenWebSocketHandler(screenStreamManager, hostInfoService), "/ws/screen/{hostId}")
                .addInterceptors(new ScreenHandshakeInterceptor())
                .setAllowedOrigins("*");
    }

    private static class ScreenHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String uri = servletRequest.getServletRequest().getRequestURI();
                String hostIdStr = extractHostId(uri);
                if (hostIdStr != null) {
                    attributes.put("hostId", Long.parseLong(hostIdStr));
                }
                String token = servletRequest.getServletRequest().getParameter("token");
                if (token != null) {
                    attributes.put("token", token);
                }
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }

        private String extractHostId(String uri) {
            String prefix = "/ws/screen/";
            int idx = uri.indexOf(prefix);
            if (idx == -1) return null;
            String after = uri.substring(idx + prefix.length());
            int queryIdx = after.indexOf('?');
            if (queryIdx != -1) {
                after = after.substring(0, queryIdx);
            }
            return after.isEmpty() ? null : after;
        }
    }
}
