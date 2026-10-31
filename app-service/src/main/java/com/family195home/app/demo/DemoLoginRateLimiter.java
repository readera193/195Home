package com.family195home.app.demo;

import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Demo 體驗登入每次都會建立使用者、群組與數百筆支出，公開環境需要限流以免被腳本灌爆資料庫。 固定時間窗內同時限制「單一來源」與「全站總量」；僅記憶體計數，單一 app-service
 * 實例已足夠。
 */
@Component
public class DemoLoginRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final int maxPerClient;
    private final int maxTotal;

    private final Map<String, Integer> countsByClient = new HashMap<>();
    private int total;
    private long windowStartMillis;

    @Autowired
    public DemoLoginRateLimiter(
            @Value("${app.demo-login.max-per-client-per-hour:20}") int maxPerClient,
            @Value("${app.demo-login.max-total-per-hour:300}") int maxTotal) {
        this(Clock.systemUTC(), maxPerClient, maxTotal);
    }

    DemoLoginRateLimiter(Clock clock, int maxPerClient, int maxTotal) {
        this.clock = clock;
        this.maxPerClient = maxPerClient;
        this.maxTotal = maxTotal;
        this.windowStartMillis = clock.millis();
    }

    /** 計入一次登入；超過任一上限則丟出 429，且不計數。 */
    public synchronized void acquire(String clientKey) {
        long now = clock.millis();
        if (now - windowStartMillis >= WINDOW.toMillis()) {
            countsByClient.clear();
            total = 0;
            windowStartMillis = now;
        }
        int clientCount = countsByClient.getOrDefault(clientKey, 0);
        if (clientCount >= maxPerClient || total >= maxTotal) {
            throw new ApiException(
                    ErrorKind.TOO_MANY_REQUESTS, "DEMO_LOGIN_RATE_LIMITED", "Demo 體驗登入次數過多，請稍後再試");
        }
        countsByClient.put(clientKey, clientCount + 1);
        total++;
    }
}
