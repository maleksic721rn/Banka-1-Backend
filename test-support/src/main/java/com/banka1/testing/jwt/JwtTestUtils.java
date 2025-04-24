package com.banka1.testing.jwt;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.security.oauth2.jwt.*;

import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;

/**
 * Utility class for generating JWT tokens for testing.
 * Creates tokens compatible with ResourceAwareJwtAuthenticationToken.
 */
public class JwtTestUtils {

    private static final KeyPair keyPair;
    private static final JwtEncoder jwtEncoder;

    static {
        keyPair = generateRsaKey();
        jwtEncoder = createJwtEncoder(keyPair);
    }

    /**
     * Generates a token for an admin user.
     * 
     * @return JWT token string
     */
    public static String generateAdminToken() {
        Map<String, Object> resourceAccess = new HashMap<>();
        resourceAccess.put("id", 1L);
        resourceAccess.put("isAdmin", true);
        resourceAccess.put("isEmployed", true);
        resourceAccess.put("department", "HR");
        resourceAccess.put("position", "DIRECTOR");
        
        List<String> permissions = Arrays.asList(
            "CREATE_CUSTOMER", "DELETE_CUSTOMER", "LIST_CUSTOMER", "EDIT_CUSTOMER", "READ_CUSTOMER",
            "SET_CUSTOMER_PERMISSION", "SET_EMPLOYEE_PERMISSION", "DELETE_EMPLOYEE", "EDIT_EMPLOYEE",
            "LIST_EMPLOYEE", "READ_EMPLOYEE", "CREATE_EMPLOYEE"
        );
        resourceAccess.put("permissions", permissions);
        
        return generateTokenWithResourceAccess("admin@admin.com", resourceAccess);
    }

    /**
     * Generates a token for an employee user.
     * 
     * @param email The employee email
     * @param userId The employee ID
     * @param department The department
     * @param position The position
     * @param permissions List of permissions
     * @return JWT token string
     */
    public static String generateEmployeeToken(String email, Long userId, String department, 
                                              String position, List<String> permissions) {
        Map<String, Object> resourceAccess = new HashMap<>();
        resourceAccess.put("id", userId);
        resourceAccess.put("isAdmin", false);
        resourceAccess.put("isEmployed", true);
        resourceAccess.put("department", department);
        resourceAccess.put("position", position);
        resourceAccess.put("permissions", permissions);
        
        return generateTokenWithResourceAccess(email, resourceAccess);
    }

    /**
     * Generates a token for a customer user.
     * 
     * @param email The customer email
     * @param userId The customer ID
     * @param permissions List of permissions
     * @return JWT token string
     */
    public static String generateCustomerToken(String email, Long userId, List<String> permissions) {
        Map<String, Object> resourceAccess = new HashMap<>();
        resourceAccess.put("id", userId);
        resourceAccess.put("isAdmin", false);
        resourceAccess.put("isEmployed", false);
        resourceAccess.put("permissions", permissions);
        
        return generateTokenWithResourceAccess(email, resourceAccess);
    }

    /**
     * Generates a JWT token with resource_access claim required by ResourceAwareJwtAuthenticationToken.
     * 
     * @param subject The subject (email)
     * @param resourceAccess The resource_access map
     * @return JWT token string
     */
    public static String generateTokenWithResourceAccess(String subject, Map<String, Object> resourceAccess) {
        Instant now = Instant.now();
        
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://idp.localhost")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("email", subject)
                .claim("resource_access", resourceAccess)
                .build();
        
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Get the RSA public key used for token signing.
     */
    public static RSAPublicKey getPublicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    /**
     * Get the RSA key pair used for token signing/verification.
     */
    public static KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Get the JWT encoder configured with the test keys.
     */
    public static JwtEncoder getJwtEncoder() {
        return jwtEncoder;
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

    private static JwtEncoder createJwtEncoder(KeyPair keyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        
        JWKSource<SecurityContext> jwkSource = (jwkSelector, securityContext) -> 
                Collections.singletonList(rsaKey);
        
        return new NimbusJwtEncoder(jwkSource);
    }
}