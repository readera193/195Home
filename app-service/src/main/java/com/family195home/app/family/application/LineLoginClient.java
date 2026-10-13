package com.family195home.app.family.application;

/** 以 LINE 授權碼取得使用者資料的 port；dev/test 為假實作，真實 LINE Login 待接。 */
public interface LineLoginClient {

    LineProfile fetchProfile(String code);
}
