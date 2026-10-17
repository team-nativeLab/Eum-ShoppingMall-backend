package com.example.shopping.domain.home.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SiteContentUpsertRequest(
		@NotBlank @Size(max = 200) String title,
		@NotBlank @Size(max = 100_000) String body) {
}
