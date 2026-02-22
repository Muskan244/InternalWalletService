package com.service.internalWallet.dto.response;

import java.time.LocalDateTime;

public record ErrorResponseDto(
        String error,
        String message,
        LocalDateTime localDateTime
) {

    public ErrorResponseDto(String error, String message) {
        this(error, message, LocalDateTime.now());
    }
}
