package com.family195home.app.shared;

/** JWT 解析後的呼叫者身分，作為 Spring Security Authentication 的 principal。 */
public record AuthenticatedUser(Long userId, String email) {
}
