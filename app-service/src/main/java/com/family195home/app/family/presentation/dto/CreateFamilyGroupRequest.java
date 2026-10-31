package com.family195home.app.family.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFamilyGroupRequest(
        @NotBlank @Size(max = 100, message = "群組名稱不可超過 100 個字") String name) {}
