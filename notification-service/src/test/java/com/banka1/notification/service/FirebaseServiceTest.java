package com.banka1.notification.service;

import com.banka1.notification.model.CustomerDevice;
import com.banka1.notification.repository.CustomerDeviceRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FirebaseServiceTest {

    @InjectMocks
    private FirebaseService firebaseService;

    @Mock
    private CustomerDeviceRepository customerDeviceRepository;

    private Map<String, String> testData;

    @BeforeEach
    public void setup() {
        testData = new HashMap<>();
        testData.put("key1", "value1");
    }

    @Test
    public void testSendNotification() throws FirebaseMessagingException {

        String title = "Test Title";
        String body = "Test Body";
        String deviceToken = "test-device-token";


        FirebaseMessaging mockFirebaseMessaging = mock(FirebaseMessaging.class);
        when(mockFirebaseMessaging.send(any(Message.class))).thenReturn("message-id-123");

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {

            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(mockFirebaseMessaging);


            firebaseService.sendNotification(title, body, deviceToken, testData);


            verify(mockFirebaseMessaging, times(1)).send(any(Message.class));
        }
    }

    @Test
    public void testSendNotificationWithNullData() throws FirebaseMessagingException {

        String title = "Test Title";
        String body = "Test Body";
        String deviceToken = "test-device-token";


        FirebaseMessaging mockFirebaseMessaging = mock(FirebaseMessaging.class);
        when(mockFirebaseMessaging.send(any(Message.class))).thenReturn("message-id-123");

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {

            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(mockFirebaseMessaging);


            firebaseService.sendNotification(title, body, deviceToken, null);


            verify(mockFirebaseMessaging, times(1)).send(any(Message.class));
        }
    }

    @Test
    public void testBroadcastNotification() throws FirebaseMessagingException {

        String title = "Test Title";
        String body = "Test Body";


        FirebaseMessaging mockFirebaseMessaging = mock(FirebaseMessaging.class);
        when(mockFirebaseMessaging.send(any(Message.class))).thenReturn("message-id-123");

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {

            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(mockFirebaseMessaging);


            firebaseService.broadcastNotification(title, body, testData);


            verify(mockFirebaseMessaging, times(1)).send(any(Message.class));
        }
    }

    @Test
    public void testSendNotificationToCustomer() {

        String title = "Test Title";
        String body = "Test Body";
        Long customerId = 123L;

        CustomerDevice device1 = new CustomerDevice();
        device1.setDeviceToken("device-token-1");

        CustomerDevice device2 = new CustomerDevice();
        device2.setDeviceToken("device-token-2");

        when(customerDeviceRepository.findByCustomerId(customerId)).thenReturn(List.of(device1, device2));


        FirebaseService spyFirebaseService = spy(firebaseService);
        doNothing().when(spyFirebaseService).sendNotification(anyString(), anyString(), anyString(), any());


        spyFirebaseService.sendNotificationToCustomer(title, body, customerId, testData);


        verify(spyFirebaseService, times(1)).sendNotification(eq(title), eq(body), eq("device-token-1"), eq(testData));
        verify(spyFirebaseService, times(1)).sendNotification(eq(title), eq(body), eq("device-token-2"), eq(testData));
    }
}