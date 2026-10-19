package com.family195home.app.family.application;

/** 以 LINE 授權碼取得使用者資料的 port。 */
public interface LineLoginClient {

    LineProfile fetchProfile(String code);
}
