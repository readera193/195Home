package com.family195home.notification.infrastructure.line;

import com.family195home.notification.application.LineMessageSendException;
import com.family195home.notification.application.LineMessageSender;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.stereotype.Component;

@Component
public class LineMessageSenderImpl implements LineMessageSender {

    private final LineMessagingClient lineMessagingClient;

    public LineMessageSenderImpl(LineMessagingClient lineMessagingClient) {
        this.lineMessagingClient = lineMessagingClient;
    }

    @Override
    public void pushText(String lineUserId, String text) {
        try {
            lineMessagingClient.pushMessage(new PushMessage(lineUserId, new TextMessage(text))).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LineMessageSendException("LINE 推播被中斷", e);
        } catch (Exception e) {
            throw new LineMessageSendException("LINE 推播失敗：" + e.getMessage(), e);
        }
    }
}
