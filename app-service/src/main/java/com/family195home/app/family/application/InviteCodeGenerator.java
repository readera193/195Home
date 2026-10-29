package com.family195home.app.family.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 產生家庭群組邀請碼：使用 SecureRandom，字元集排除易混淆的 0/O、1/I。 */
@Component
public class InviteCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
