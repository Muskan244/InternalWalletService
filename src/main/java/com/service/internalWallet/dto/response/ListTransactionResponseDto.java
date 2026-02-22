package com.service.internalWallet.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.service.internalWallet.enums.Status;
import com.service.internalWallet.enums.TransactionType;

public record ListTransactionResponseDto(
        UUID transactionId,
        TransactionType transactionType,
        Status status,
        LocalDateTime createdAt
) {
}
