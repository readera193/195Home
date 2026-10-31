package com.family195home.app.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DemoLoginRateLimiterTest {

    /** 可手動推進時間的 Clock。 */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void rejectsClientOverItsLimitButNotOthers() {
        DemoLoginRateLimiter limiter = new DemoLoginRateLimiter(new MutableClock(), 2, 100);
        limiter.acquire("1.1.1.1");
        limiter.acquire("1.1.1.1");

        assertThatThrownBy(() -> limiter.acquire("1.1.1.1"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.getKind()).isEqualTo(ErrorKind.TOO_MANY_REQUESTS);
                            assertThat(e.getCode()).isEqualTo("DEMO_LOGIN_RATE_LIMITED");
                        });
        assertThatCode(() -> limiter.acquire("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenGlobalLimitReached() {
        DemoLoginRateLimiter limiter = new DemoLoginRateLimiter(new MutableClock(), 10, 2);
        limiter.acquire("a");
        limiter.acquire("b");

        assertThatThrownBy(() -> limiter.acquire("c")).isInstanceOf(ApiException.class);
    }

    @Test
    void resetsAfterWindowElapses() {
        MutableClock clock = new MutableClock();
        DemoLoginRateLimiter limiter = new DemoLoginRateLimiter(clock, 1, 1);
        limiter.acquire("a");
        assertThatThrownBy(() -> limiter.acquire("a")).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofHours(1));

        assertThatCode(() -> limiter.acquire("a")).doesNotThrowAnyException();
    }
}
