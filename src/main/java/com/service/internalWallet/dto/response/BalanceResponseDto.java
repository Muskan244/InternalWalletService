package com.service.internalWallet.dto.response;

import java.util.UUID;

public record BalanceResponseDto(
        UUID userId,
        String assetName,
        Long balance
) {
}
