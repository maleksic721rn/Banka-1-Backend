package com.banka1.notification.controller;

import com.banka1.notification.DTO.request.AddDeviceDTO;
import com.banka1.notification.DTO.request.TestNotificationDto;
import com.banka1.notification.controllers.NotificationController;
import com.banka1.notification.service.DeviceService;
import com.banka1.notification.service.FirebaseService;
import com.banka1.notification.service.implementation.AuthService;
import com.banka1.notification.sender.FirebaseSender;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private FirebaseSender firebaseSender;

    @Mock
    private FirebaseService firebaseService;

    private NotificationController notificationController;
    private ObjectMapper objectMapper;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notificationController = new NotificationController(authService, deviceService, firebaseSender, firebaseService);
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController).build();
        objectMapper = new ObjectMapper();
        jwtToken = "eyJhbGciOiJIUzI1NiJ9." +
                "eyJpZCI6MSwicG9zaXRpb24iOiJDVVNUT01FUiIsInBlcm1pc3Npb25zIjpbInVzZXIuY3VzdG9tZXIudmlldyJdLCJpc0FkbWluIjpmYWxzZSwiaWF0IjoxNzQwNzEwMTQyLCJleHAiOjE3NDA3MTE5NDJ9." +
                "someSignature";
    }

    @Test
    void testAddDevice_Success() throws Exception {
        // Arrange
        String token = "Bearer " + jwtToken;
        Long customerId = 1L; // Matching the ID in the JWT token
        String deviceToken = "device-token-123";

        AddDeviceDTO addDeviceDTO = new AddDeviceDTO();
        addDeviceDTO.deviceToken = deviceToken;

        // Create real Claims object to match the return type
        Claims claims = mock(Claims.class);
        when(claims.get("id", Long.class)).thenReturn(customerId);

        when(authService.getToken(token)).thenReturn(jwtToken);
        when(authService.parseToken(jwtToken)).thenReturn(claims);

        doNothing().when(deviceService).addDevice(customerId, deviceToken);

        // Act & Assert
        mockMvc.perform(post("/add-device")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addDeviceDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify
        verify(authService).getToken(token);
        verify(authService).parseToken(jwtToken);
        verify(deviceService).addDevice(customerId, deviceToken);
    }



    @Test
    void testTestNotification_Success() throws Exception {
        // Arrange
        String title = "Test Title";
        String message = "Test Message";
        String deviceToken = "device-token-123";
        Map<String, String> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");

        TestNotificationDto testNotificationDto = new TestNotificationDto();
        testNotificationDto.setTitle(title);
        testNotificationDto.setMessage(message);
        testNotificationDto.setDeviceToken(deviceToken);
        testNotificationDto.setData(data);

        doNothing().when(firebaseService).sendNotification(
                eq(title), eq(message), eq(deviceToken), eq(data));

        // Act & Assert
        mockMvc.perform(post("/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testNotificationDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify
        verify(firebaseService).sendNotification(title, message, deviceToken, data);
    }

    @Test
    void testTestNotification_ValidationFailure() throws Exception {
        // Arrange
        TestNotificationDto testNotificationDto = new TestNotificationDto();
        // Missing required fields to trigger validation failure

        // Act & Assert
        mockMvc.perform(post("/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testNotificationDto)))
                .andExpect(status().isBadRequest());

        // Verify no interaction with service
        verifyNoInteractions(firebaseService);
    }
}