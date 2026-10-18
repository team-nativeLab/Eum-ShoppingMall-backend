package com.example.shopping.domain.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminOrderCancelRequest(@NotBlank @Size(max = 200) String reason) {
}
