package com.family195home.app.family.application;

/** 以 LINE 授權碼取得使用者資料的 port；dev/test 為假實作，prod 為 LINE OAuth 實作。 */
public interface LineLoginClient {

    LineProfile fetchProfile(String code);
}
