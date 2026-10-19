package org.example.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.dto.QuoteRequest.Item;

import java.util.List;

public record OrderRequest(
        @NotEmpty(message = "At least one item is required")
        @Size(max = 50, message = "An order can contain at most 50 items")
        List<@Valid @NotNull(message = "Item must not be null") Item> items,
        @Size(max = 32, message = "Coupon code must not exceed 32 characters")
        String couponCode
) {}
