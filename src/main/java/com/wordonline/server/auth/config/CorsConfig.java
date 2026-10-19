package com.wordonline.server.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://word-online.yunseong.dev",
                "https://apptive-game-team.github.io",
                "https://word-online.vercel.app",
                "https://word-online.apps.tossmini.com",
                "https://word-online.private-apps.tossmini.com",
                "https://team6515.itch.io",
                "https://html-classic.itch.zone",
                "https://assets-production.game-ping.kr",
                "https://www.game-ping.kr",
                "https://arcanecasters.theevilent.com"));
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}