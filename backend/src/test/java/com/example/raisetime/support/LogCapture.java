package com.example.raisetime.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// テストの間、あるクラスが出したログを捕まえて、中身（event や項目）を確かめられるようにする。
// try-with-resources で使う（終わったら捕まえるのをやめる）
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> loggingClass) {
        logger = (Logger) LoggerFactory.getLogger(loggingClass);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture of(Class<?> loggingClass) {
        return new LogCapture(loggingClass);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    // event の項目がその値のログ（無ければテストを失敗させる）
    public ILoggingEvent single(String eventName) {
        List<ILoggingEvent> matched = appender.list.stream()
                .filter(e -> eventName.equals(fields(e).get("event")))
                .toList();
        if (matched.size() != 1) {
            throw new AssertionError("event=" + eventName + " のログが " + matched.size() + " 件ありました: "
                    + appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        }
        return matched.getFirst();
    }

    public long count(String eventName) {
        return appender.list.stream().filter(e -> eventName.equals(fields(e).get("event"))).count();
    }

    // addKeyValue で付けた項目
    public static Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            pairs.forEach(pair -> fields.put(pair.key, pair.value));
        }
        return fields;
    }

    // ログ全体（メッセージ・項目・MDC）を1つの文字列にしたもの。「この文字がどこにも出ていない」を確かめるのに使う
    public String allText() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            text.append(event.getFormattedMessage()).append(' ')
                    .append(fields(event)).append(' ')
                    .append(event.getMDCPropertyMap()).append('\n');
        }
        return text.toString();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
