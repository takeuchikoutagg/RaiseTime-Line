package com.example.raisetime.security;

import com.example.raisetime.exception.TooManyRequestsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// ログインの総当たり（パスワードを何度も試す攻撃）を防ぐため、ログインの失敗回数を数える。
// - 同じメールアドレスで15分以内に5回失敗したら、その15分が終わるまでログインできなくする（1つのアカウントを狙う攻撃）
// - 同じ IP アドレスから15分以内に50回失敗したら、同じようにログインできなくする（多くのアカウントを順に試す攻撃）
// 回数はメモリに持つ（サーバーを再起動すると消える。EC2 1台の構成のため、サーバー間で共有はしない）
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES_PER_EMAIL = 5;
    static final int MAX_FAILURES_PER_IP = 50;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final String LOCKED_MESSAGE = "ログインの失敗が続いたため、一時的にログインできません。しばらくたってからもう一度お試しください";

    private static final String EMAIL_KEY = "email:";
    private static final String IP_KEY = "ip:";

    private final Clock clock;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    // windowStart：数え始めた（最初に失敗した）時刻。そこから WINDOW の間の失敗を数える
    private record Failures(int count, Instant windowStart) {

        boolean expired(Instant now) {
            return !windowStart.plus(WINDOW).isAfter(now);
        }
    }

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    // ログインできない状態なら TooManyRequestsException（429）。パスワードが正しくても止める（止めないと試し続けられるため）
    public void checkAllowed(String email, String ip) {
        Instant now = clock.instant();
        Duration wait = max(remainingLock(EMAIL_KEY + email, MAX_FAILURES_PER_EMAIL, now),
                remainingLock(IP_KEY + ip, MAX_FAILURES_PER_IP, now));
        if (!wait.isZero()) {
            throw new TooManyRequestsException(LOCKED_MESSAGE, wait);
        }
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        increment(EMAIL_KEY + email, now);
        increment(IP_KEY + ip, now);
    }

    // ログインできたら、そのメールアドレスの回数を消す。
    // IP の回数は消さない（自分のアカウントでログインして、IP の回数を消しながら他人のアカウントを試せないように）
    public void recordSuccess(String email) {
        failures.remove(EMAIL_KEY + email);
    }

    // 期限が過ぎた記録を5分ごとに消す（メモリが増え続けないように）
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void purgeExpired() {
        Instant now = clock.instant();
        failures.values().removeIf(f -> f.expired(now));
    }

    int size() {
        return failures.size();
    }

    private void increment(String key, Instant now) {
        failures.compute(key, (k, current) -> current == null || current.expired(now)
                ? new Failures(1, now)
                : new Failures(current.count() + 1, current.windowStart()));
    }

    // 上限まで失敗していたら、ログインできるようになるまでの時間。できるなら 0
    private Duration remainingLock(String key, int maxFailures, Instant now) {
        Failures f = failures.get(key);
        if (f == null || f.expired(now) || f.count() < maxFailures) {
            return Duration.ZERO;
        }
        return Duration.between(now, f.windowStart().plus(WINDOW));
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
