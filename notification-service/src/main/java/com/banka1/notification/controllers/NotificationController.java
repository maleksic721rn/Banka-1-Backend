package com.banka1.notification.controllers;

import com.banka1.notification.sender.FirebaseSender;
import com.banka1.notification.service.DeviceService;
import com.banka1.notification.service.FirebaseService;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
@Tag(name = "Customer API", description = "API za upravljanje notifikacijama")
public class NotificationController {

    private final DeviceService deviceService;
    private final FirebaseSender firebase;
    private final FirebaseService firebaseService;

    public NotificationController(DeviceService deviceService, FirebaseSender firebase, FirebaseService firebaseService) {
        this.deviceService = deviceService;
        this.firebase = firebase;
        this.firebaseService = firebaseService;
    }
    
}
