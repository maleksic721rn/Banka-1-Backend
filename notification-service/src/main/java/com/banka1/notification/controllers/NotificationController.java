package com.banka1.notification.controllers;

import com.banka1.notification.DTO.request.AddDeviceDTO;
import com.banka1.notification.DTO.request.TestNotificationDto;
import com.banka1.notification.aspect.Authorization;
import com.banka1.notification.sender.FirebaseSender;
import com.banka1.notification.service.DeviceService;
import com.banka1.notification.service.FirebaseService;
import com.banka1.notification.service.implementation.AuthService;
import com.banka1.notification.utils.ResponseTemplate;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
@Tag(name = "Customer API", description = "API za upravljanje notifikacijama")
public class NotificationController {

    private final AuthService authService;
    private final DeviceService deviceService;
    private final FirebaseSender firebase;
    private final FirebaseService firebaseService;

    public NotificationController(AuthService authService, DeviceService deviceService, FirebaseSender firebase, FirebaseService firebaseService) {
        this.authService = authService;
        this.deviceService = deviceService;
        this.firebase = firebase;
        this.firebaseService = firebaseService;
    }

    @PostMapping("/add-device")
    @Authorization
    public ResponseEntity<?> addDevice(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody AddDeviceDTO addDeviceDTO) {
        Long customerId = authService.parseToken(authService.getToken(authorization)).get("id", Long.class);

        deviceService.addDevice(customerId, addDeviceDTO.deviceToken);

        return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, null, null);
    }

    @PostMapping("/test")
    public ResponseEntity<?> testNotification(@Valid @RequestBody TestNotificationDto notif) {
        firebaseService.sendNotification(notif.getTitle(), notif.getMessage(), notif.getDeviceToken(), notif.getData());

        return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, null, null);
    }
}
