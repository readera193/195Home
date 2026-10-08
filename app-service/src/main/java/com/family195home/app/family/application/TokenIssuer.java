package com.family195home.app.family.application;

import java.time.Instant;

/** 登入成功後核發存取權杖的 port；由 infrastructure 層（JWT）實作。 */
public interface TokenIssuer {

    IssuedToken issue(Long userId, String email);

    record IssuedToken(String token, Instant expiresAt) {
    }
}
