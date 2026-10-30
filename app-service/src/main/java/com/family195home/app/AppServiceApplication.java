package com.family195home.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class AppServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppServiceApplication.class, args);
    }

    /** 統一注入時鐘，測試可替換為固定時間；沿用 JVM 預設時區（容器以 -Duser.timezone 設為 Asia/Taipei）。 */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
