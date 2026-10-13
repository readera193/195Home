package com.family195home.app.family.infrastructure.line;

import com.family195home.app.family.application.LineLoginClient;
import com.family195home.app.family.application.LineProfile;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 開發/測試用假登入：把 code 直接當作暱稱，不呼叫 LINE。 */
@Component
@Profile({"dev", "test"})
public class DevLineLoginClient implements LineLoginClient {

    @Override
    public LineProfile fetchProfile(String code) {
        String name = code.trim();
        return new LineProfile("dev-" + name, name);
    }
}
