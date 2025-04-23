package com.banka1.idp.config;

import com.banka1.idp.user.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String loginUrl;

    public SecurityConfig(@Value("${frontend.login.url}") String loginUrl) {
        this.loginUrl = loginUrl;
    }

    private static KeyPair generateRsaKey() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return keyPair;
    }

    private static void getAccessTokenClaims(JwtEncodingContext context) {
        context.getClaims()
                .claims(
                        (claims) -> {
                            if ((context.getPrincipal().getPrincipal() instanceof User u)) {
                                record Claims(
                                        Set<String> permissions,
                                        Long id,
                                        String department,
                                        String position,
                                        boolean isAdmin,
                                        boolean isEmployed) {}
                                Set<String> permissions =
                                        AuthorityUtils.authorityListToSet(
                                                        context.getPrincipal().getAuthorities())
                                                .stream()
                                                .collect(
                                                        Collectors.collectingAndThen(
                                                                Collectors.toSet(),
                                                                Collections::unmodifiableSet));
                                var c =
                                        new Claims(
                                                permissions,
                                                u.getId(),
                                                u.getDepartment(),
                                                u.getPosition(),
                                                u.isAdmin(),
                                                "employee".equalsIgnoreCase(u.getUserType()));
                                claims.put("resource_access", c);
                            }
                        });
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http, RequestCache requestCache) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .requestCache(cache -> cache.requestCache(requestCache))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .with(
                        authorizationServerConfigurer,
                        // Enable OpenID Connect 1.0
                        (authorizationServer) ->
                                authorizationServer.oidc(
                                        oidc ->
                                                oidc.userInfoEndpoint(
                                                                userInfo ->
                                                                        userInfo.userInfoMapper(
                                                                                userInfoMapper()))
                                                        .clientRegistrationEndpoint(
                                                                Customizer.withDefaults())))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
                .exceptionHandling(
                        (exceptions) ->
                                exceptions.defaultAuthenticationEntryPointFor(
                                        new LoginUrlAuthenticationEntryPoint(loginUrl),
                                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http, RequestCache requestCache) throws Exception {

        http.authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(requestCache))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .formLogin(
                        fl -> {
                            fl.loginPage(loginUrl);
                            fl.loginProcessingUrl("/api/idp/login");
                            fl.usernameParameter("email");
                        });
        return http.build();
    }

    @Bean
    public RequestCache requestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName("continue");
        return requestCache;
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate template) {
        return new JdbcRegisteredClientRepository(template);
    }

//    @Bean
//    OAuth2AuthorizationService oAuth2AuthorizationService(
//            JdbcTemplate template,
//            RegisteredClientRepository regClientRepository,
//            ObjectMapper om) {
//        SecurityJackson2Modules.enableDefaultTyping(om);
//        JdbcOAuth2AuthorizationService jdbcOAuth2AuthorizationService =
//                new JdbcOAuth2AuthorizationService(template, regClientRepository);
//        return jdbcOAuth2AuthorizationService;
//    }

    @Bean
    public ApplicationRunner registeredClientCreator(
            TokenSettings tokenSettings, RegisteredClientRepository repository) {
        RegisteredClient oidcClient =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("oidc-client")
                        .clientSecret("{noop}secret")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("http://127.0.0.1:3001/login/oauth2/code/oidc-client")
                        .redirectUri("https://oauth.pstmn.io/v1/callback")
                        .postLogoutRedirectUri("http://127.0.0.1:3001/")
                        .scope(OidcScopes.OPENID)
                        .scope(OidcScopes.PROFILE)
                        .scope(OidcScopes.EMAIL)
                        .tokenSettings(tokenSettings)
                        .clientSettings(
                                ClientSettings.builder()
                                        .requireAuthorizationConsent(false)
                                        .requireProofKey(true)
                                        .build())
                        .build();
        RegisteredClient publicClient =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("public-client")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("http://127.0.1:3001/login/oauth2/code/public-client")
                        .postLogoutRedirectUri("http://127.0.0.1:3001/")
                        .scope(OidcScopes.OPENID)
                        .scope(OidcScopes.PROFILE)
                        .scope(OidcScopes.EMAIL)
                        .tokenSettings(tokenSettings)
                        .clientSettings(
                                ClientSettings.builder()
                                        .requireAuthorizationConsent(false)
                                        .requireProofKey(true)
                                        .build())
                        .build();
        RegisteredClient gatewayClient =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("gateway-client")
                        .clientSecret("{noop}secrets")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("https://localhost/api/login/oauth2/code/idp")
                        .redirectUri("https://oauth.pstmn.io/v1/callback")
                        .postLogoutRedirectUri("https://localhost")
                        .scope("openid")
                        .scope("email")
                        .scope("profile")
                        .tokenSettings(tokenSettings)
                        .build();
        RegisteredClient tradingServiceClient =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("trading-service-client")
                        .clientSecret("{noop}verysecuresecretmuchwow")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scope("openid")
                        .scope("email")
                        .scope("profile")
                        .scope("trading-service")
                        .tokenSettings(
                                TokenSettings.builder()
                                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                                        .build())
                        .build();
        return args -> {
            if (repository.findByClientId(oidcClient.getClientId()) == null) {
                repository.save(oidcClient);
            }
            if (repository.findByClientId(publicClient.getClientId()) == null) {
                repository.save(publicClient);
            }
            if (repository.findByClientId(gatewayClient.getClientId()) == null) {
                repository.save(gatewayClient);
            }
            if (repository.findByClientId(tradingServiceClient.getClientId()) == null) {
                repository.save(tradingServiceClient);
            }
        };
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return (context) -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                getAccessTokenClaims(context);
            } else if (new OAuth2TokenType("id_token").equals(context.getTokenType())) {
                context.getClaims()
                        .claims(
                                (claims) -> {
                                    if ((context.getPrincipal().getPrincipal() instanceof User u)) {
                                        claims.put("sub", u.getEmail());
                                        claims.put("email", u.getEmail());
                                        claims.put(
                                                "name", u.getFirstName() + " " + u.getLastName());
                                        claims.put("family_name", u.getLastName());
                                        claims.put("given_name", u.getFirstName());
                                    }
                                });
            }
        };
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey =
                new RSAKey.Builder(publicKey)
                        .privateKey(privateKey)
                        .keyID(UUID.randomUUID().toString())
                        .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper() {
        return (context) -> {
            OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
            var token = (JwtAuthenticationToken) authentication.getPrincipal();
            return new OidcUserInfo(token.getToken().getClaims());
        };
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);

        config.setAllowedHeaders(
                Arrays.asList("Origin", "Content-Type", "Accept", "Authorization"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Dynamic Access-Control-Allow-Origin: Set it to the Origin from the request
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    /**
     * Configures the token settings to set the refresh token time to live to 1 day and to create a
     * new refresh token on use. Will be used in production. However, React Strict Mode runs every
     * request to run twice which, when the token needs to be refreshed, will lead to errors. So, in
     * development, refresh token rotation will be disabled.
     */
    @Bean
    TokenSettings tokenSettings() {
        return TokenSettings.builder()
                .refreshTokenTimeToLive(Duration.ofDays(1))
                .reuseRefreshTokens(false)
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build();
    }

    /**
     * Configures and provides token settings specifically for the development profile. Sets the
     * refresh token time-to-live to 1 day and enables the reuse of refresh tokens. Additionally,
     * configures the access token time-to-live to 30 seconds; this causes the Gateway to refresh
     * tokens on every request. See above for the rationale.
     */
    @Bean
    @Profile("dev")
    @Primary
    TokenSettings devTokenSettings() {
        return TokenSettings.builder()
                .refreshTokenTimeToLive(Duration.ofDays(1))
                .reuseRefreshTokens(true)
                .accessTokenTimeToLive(Duration.ofSeconds(30))
                .build();
    }
}
