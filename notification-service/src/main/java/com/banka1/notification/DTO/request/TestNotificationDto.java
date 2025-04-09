package com.banka1.notification.DTO.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Valid
public class TestNotificationDto {

    @NotNull(message = "Title cannot be null")
    private String title;

    @NotNull(message = "Message cannot be null")
    private String message;


    private Map<String, String> data;

    @NotNull(message = "Device token cannot be null")
    private String deviceToken;
}
