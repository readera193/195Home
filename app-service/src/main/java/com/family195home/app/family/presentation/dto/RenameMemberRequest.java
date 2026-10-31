package com.family195home.app.family.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameMemberRequest(@NotBlank @Size(max = 50) String displayName) {}
