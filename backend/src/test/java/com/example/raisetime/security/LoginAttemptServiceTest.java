package com.example.raisetime.security;

import com.example.raisetime.exception.TooManyRequestsException;
import com.example.raisetime.logging.LogEvents;
import com.example.raisetime.support.LogCapture;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static final String EMAIL = "taro@example.com";
    private static final String IP = "203.0.113.1";

    // テストの中で時刻を進められる Clock
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-04T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(email, ip);
        }
    }

    @Test
    void allowsUntilFailuresReachLimitForEmail() {
        fail(EMAIL, IP, LoginAttemptService.MAX_FAILURES_PER_EMAIL - 1);
        assertThatCode(() -> service.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();

        service.recordFailure(EMAIL, IP);
        assertThatThrownBy(() -> service.checkAllowed(EMAIL, IP))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage(LoginAttemptService.LOCKED_MESSAGE);
    }

    @Test
    void lockedEmail_isBlockedFromAnyIp_butOtherEmailsAreNot() {
        fail(EMAIL, IP, LoginAttemptService.MAX_FAILURES_PER_EMAIL);

        assertThatThrownBy(() -> service.checkAllowed(EMAIL, "198.51.100.7"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> service.checkAllowed("hanako@example.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void lockEndsWhenWindowPasses_andRetryAfterIsTheRemainingTime() {
        fail(EMAIL, IP, LoginAttemptService.MAX_FAILURES_PER_EMAIL);
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.checkAllowed(EMAIL, IP))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMinutes(5)));

        clock.advance(Duration.ofMinutes(5));
        assertThatCode(() -> service.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void failuresOlderThanWindow_areNotCounted() {
        fail(EMAIL, IP, LoginAttemptService.MAX_FAILURES_PER_EMAIL - 1);
        clock.advance(LoginAttemptService.WINDOW);
        service.recordFailure(EMAIL, IP);

        assertThatCode(() -> service.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void success_resetsEmailCount() {
        fail(EMAIL, IP, LoginAttemptService.MAX_FAILURES_PER_EMAIL - 1);
        service.recordSuccess(EMAIL);
        fail(EMAIL, IP, LoginAttemptService.MAX_FAILURES_PER_EMAIL - 1);

        assertThatCode(() -> service.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    // 多くのアカウントを順に試す攻撃：メールアドレスごとの回数は少なくても、同じ IP からの失敗で止める
    @Test
    void manyEmailsFromSameIp_lockTheIp() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_IP; i++) {
            service.recordFailure("user" + i + "@example.com", IP);
        }

        assertThatThrownBy(() -> service.checkAllowed("new@example.com", IP))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> service.checkAllowed("new@example.com", "198.51.100.7")).doesNotThrowAnyException();
    }

    // T-08 境界値：同じ IP からの失敗が49回なら、まだログインできる（50回目でロック）
    @Test
    void failuresFromSameIp_oneBelowTheLimit_areStillAllowed() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_IP - 1; i++) {
            service.recordFailure("user" + i + "@example.com", IP);
        }
        assertThatCode(() -> service.checkAllowed("new@example.com", IP)).doesNotThrowAnyException();

        service.recordFailure("last@example.com", IP);
        assertThatThrownBy(() -> service.checkAllowed("new@example.com", IP))
                .isInstanceOf(TooManyRequestsException.class);
    }

    // 自分のアカウントでログインに成功しても、IP の回数は消えない
    @Test
    void success_doesNotResetIpCount() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_IP; i++) {
            service.recordFailure("user" + i + "@example.com", IP);
        }
        service.recordSuccess("mine@example.com");

        assertThatThrownBy(() -> service.checkAllowed("mine@example.com", IP))
                .isInstanceOf(TooManyRequestsException.class);
    }

    // ログインできなくなった瞬間に1回だけ記録する（docs/ログ・監視設計.md の M-5）。メールアドレスは一部を隠す
    @Test
    void reachingTheEmailLimit_isLoggedOnce_withMaskedEmail() {
        try (LogCapture logs = LogCapture.of(LoginAttemptService.class)) {
            fail(EMAIL, IP, LoginAttemptService.MAX_FAILURES_PER_EMAIL - 1);
            assertThat(logs.count(LogEvents.LOGIN_LOCKED)).isZero();

            // 上限に達したあとも失敗が続く（記録は増えない）
            fail(EMAIL, IP, 3);

            assertThat(LogCapture.fields(logs.single(LogEvents.LOGIN_LOCKED)))
                    .containsEntry("locked_by", "email")
                    .containsEntry("email", "t***@example.com")
                    .containsEntry("client_ip", IP)
                    .containsEntry("failure_count", LoginAttemptService.MAX_FAILURES_PER_EMAIL);
            assertThat(logs.allText()).doesNotContain(EMAIL);
        }
    }

    @Test
    void reachingTheIpLimit_isLoggedAsLockedByIp() {
        try (LogCapture logs = LogCapture.of(LoginAttemptService.class)) {
            for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_IP; i++) {
                service.recordFailure("user" + i + "@example.com", IP);
            }

            assertThat(LogCapture.fields(logs.single(LogEvents.LOGIN_LOCKED)))
                    .containsEntry("locked_by", "ip")
                    .containsEntry("client_ip", IP);
        }
    }

    @Test
    void purgeExpired_removesOnlyOldRecords() {
        service.recordFailure(EMAIL, IP);
        clock.advance(LoginAttemptService.WINDOW);
        service.recordFailure("hanako@example.com", "198.51.100.7");

        service.purgeExpired();

        // 残るのは後から失敗した hanako のメールアドレスと IP の2件
        assertThat(service.size()).isEqualTo(2);
    }
}
