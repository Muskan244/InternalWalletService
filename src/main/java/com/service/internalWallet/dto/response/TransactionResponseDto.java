package com.service.internalWallet.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.service.internalWallet.enums.Status;
import com.service.internalWallet.enums.TransactionType;

public record TransactionResponseDto(
        UUID transactionId,
        TransactionType transactionType,
        Long amount,
        Long balanceAfter,
        Status status,
        LocalDateTime createdAt
) {
}
