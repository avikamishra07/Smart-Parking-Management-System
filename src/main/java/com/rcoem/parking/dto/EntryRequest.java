package com.rcoem.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record EntryRequest(
        @NotBlank(message = "vehicleNumber is required")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{2,18}[A-Za-z0-9]$",
                 message = "vehicleNumber must be 4-20 letters/digits (spaces and hyphens allowed inside)")
        String vehicleNumber,

        @NotNull(message = "slotId is required")
        @Positive(message = "slotId must be positive")
        Long slotId) {
}
