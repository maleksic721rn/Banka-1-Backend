package com.banka1.notification.service;

import com.banka1.common.model.Permission;
import com.banka1.common.model.Position;
import com.banka1.notification.aspect.AuthAspect;
import com.banka1.notification.aspect.Authorization;
import com.banka1.notification.service.implementation.AuthService;
import io.jsonwebtoken.Claims;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthAspectTest {

    private AuthAspect authAspect;

    @Mock
    private AuthService authService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private Method method;

    @Mock
    private Claims claims;

    private List<String> permissions;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        authAspect = new AuthAspect(authService);
        permissions = List.of(
                "user.employee.create", "user.employee.edit", "user.employee.delete",
                "user.employee.list", "user.employee.view", "user.employee.permission",
                "user.customer.create", "user.customer.edit", "user.customer.delete",
                "user.customer.list", "user.customer.view", "user.customer.permission"
        );
        jwtToken = "eyJhbGciOiJIUzI1NiJ9." +
                "eyJpZCI6MSwicG9zaXRpb24iOiJDVVNUT01FUiIsInBlcm1pc3Npb25zIjpbInVzZXIuY3VzdG9tZXIudmlldyJdLCJpc0FkbWluIjpmYWxzZSwiaWF0IjoxNzQwNzEwMTQyLCJleHAiOjE3NDA3MTE5NDJ9." +
                "someSignature";
    }

    @Test
    void testAuthorize_WithValidTokenAndPermissions_ShouldProceed() throws Throwable {

        String authHeader = "Bearer " + jwtToken;

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"authorization"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{authHeader});


        when(authService.getToken(authHeader)).thenReturn(jwtToken);
        when(authService.parseToken(jwtToken)).thenReturn(claims);


        Authorization authorization = mock(Authorization.class);
        when(method.getAnnotation(Authorization.class)).thenReturn(authorization);

        when(authorization.permissions()).thenReturn(new Permission[]{Permission.READ_EMPLOYEE});
        when(authorization.positions()).thenReturn(new Position[]{});


        when(claims.get("permissions")).thenReturn(permissions);


        ResponseEntity<String> expectedResponse = ResponseEntity.ok("Success");
        when(joinPoint.proceed()).thenReturn(expectedResponse);


        Object result = authAspect.authorize(joinPoint);

        assertEquals(expectedResponse, result);
        verify(joinPoint).proceed();

    }

    @Test
    void testAuthorize_InvalidToken_ShouldReturnUnauthorized() throws Throwable {

        String invalidJwtToken = "eyJhbGciOiJIUzI1NiJ9.invalidToken";
        String authHeader = "Bearer " + invalidJwtToken;

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"authorization"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{authHeader});
        when(authService.getToken(authHeader)).thenReturn(invalidJwtToken);
        when(authService.parseToken(invalidJwtToken)).thenReturn(null); // Invalid token


        Authorization authorization = mock(Authorization.class);



        Object result = authAspect.authorize(joinPoint);


        verify(joinPoint, never()).proceed();
        ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.UNAUTHORIZED, responseEntity.getStatusCode());
    }

    @Test
    void testAuthorize_ForbiddenAccess_ShouldReturnForbidden() throws Throwable {

        String authHeader = "Bearer " + jwtToken;

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"authorization"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{authHeader});
        when(authService.getToken(authHeader)).thenReturn(jwtToken);
        when(authService.parseToken(jwtToken)).thenReturn(claims);

        Authorization authorization = mock(Authorization.class);
        when(method.getAnnotation(Authorization.class)).thenReturn(authorization);
        when(authorization.permissions()).thenReturn(new Permission[]{Permission.CREATE_EMPLOYEE});
        when(authorization.positions()).thenReturn(new Position[]{});
        when(authorization.allowIdFallback()).thenReturn(false);


        List<String> permissions = List.of("user.customer.view");
        when(claims.get("permissions")).thenReturn(permissions);
        when(claims.get("isAdmin", Boolean.class)).thenReturn(false);


        Object result = authAspect.authorize(joinPoint);


        verify(joinPoint, never()).proceed();
        ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.FORBIDDEN, responseEntity.getStatusCode());
    }
}