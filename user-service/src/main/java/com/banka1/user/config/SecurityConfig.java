package com.banka1.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

@Configuration
public class SecurityConfig {

    @Bean
    Customizer<
            AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry>
    authorizeHttpRequests() {
        return authorize -> authorize
                .requestMatchers("/api/set-password", "/api/reset-password").permitAll()
                .anyRequest().authenticated();
    }

}
