package com.family195home.app.family.dto;

public record MyFamilyResponse(Long familyGroupId, String name, String status, String role, String inviteCode) {

    public static MyFamilyResponse none() {
        return new MyFamilyResponse(null, null, null, null, null);
    }
}
