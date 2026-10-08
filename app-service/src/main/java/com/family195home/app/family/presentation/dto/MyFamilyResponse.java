package com.family195home.app.family.presentation.dto;

public record MyFamilyResponse(Long familyGroupId, String name, String status, String role, String inviteCode, Long familyMemberId) {

    public static MyFamilyResponse none() {
        return new MyFamilyResponse(null, null, null, null, null, null);
    }
}
