package com.banka1.testing.config;

import com.banka1.testing.jwt.JwtTestUtils;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;

@TestConfiguration
@Profile("test")
public class JwtTestConfiguration {

    @Bean
    public KeyPair testKeyPair() {
        return JwtTestUtils.getKeyPair();
    }

    @Bean
    public RSAPublicKey testPublicKey() {
        return JwtTestUtils.getPublicKey();
    }

    @Bean
    public JwtEncoder testJwtEncoder() {
        return JwtTestUtils.getJwtEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey testPublicKey) {
        return NimbusJwtDecoder.withPublicKey(testPublicKey).build();
    }
}
