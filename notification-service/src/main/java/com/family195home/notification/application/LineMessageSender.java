package com.family195home.notification.application;

/**
 * 對外推播 LINE 訊息的 port：scheduler 只依賴此介面，不直接接觸 LINE SDK。
 */
public interface LineMessageSender {

    /** 推播純文字訊息；失敗時拋出 {@link LineMessageSendException}。 */
    void pushText(String lineUserId, String text);
}
