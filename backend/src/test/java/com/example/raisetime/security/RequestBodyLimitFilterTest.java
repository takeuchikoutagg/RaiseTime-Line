package com.example.raisetime.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// リクエストの本文の大きさの上限（docs/テスト設計書.md 2.1・D-7。T-41・T-42）
class RequestBodyLimitFilterTest {

    private static final int LIMIT = RequestBodyLimitFilter.MAX_BODY_BYTES;

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(new ErrorResponseWriter(new ObjectMapper()));

    // 後ろ（コントローラー）に届いたリクエストと、そこで読めた本文
    private final AtomicReference<ServletRequest> passed = new AtomicReference<>();
    private final AtomicReference<byte[]> readBody = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> {
        passed.set(req);
        readBody.set(req.getInputStream().readAllBytes());
    };

    private static byte[] bytes(int size) {
        byte[] body = new byte[size];
        Arrays.fill(body, (byte) 'a');
        return body;
    }

    private static MockHttpServletRequest json(String method, String uri, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }

    // Content-Length を付けずに送る（chunked）リクエスト。読んだ量も数える
    private static final class ChunkedRequest extends MockHttpServletRequest {
        ChunkedRequest(String method, String uri, String contentType, byte[] body) {
            super(method, uri);
            setContentType(contentType);
            setContent(body);
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }

    private void assertRejected(MockHttpServletResponse response) throws IOException {
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"PAYLOAD_TOO_LARGE\"")
                .contains(RequestBodyLimitFilter.MESSAGE);
        assertThat(passed.get()).isNull();
    }

    // ---------- Content-Length あり（D-7 #3・#4） ----------

    @ParameterizedTest
    @ValueSource(ints = {LIMIT - 1, LIMIT})
    void withContentLength_atOrBelowLimit_passesThrough(int size) throws Exception {
        MockHttpServletRequest request = json("POST", "/api/auth/login", bytes(size));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // 包まずに、そのまま渡す
        assertThat(passed.get()).isSameAs(request);
        assertThat(readBody.get()).hasSize(size);
    }

    @Test
    void withContentLength_overLimit_isRejected_withoutReadingBody() throws Exception {
        MockHttpServletRequest request = json("POST", "/api/auth/login", bytes(LIMIT + 1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertRejected(response);
        // 本文は1バイトも読んでいない
        assertThat(request.getInputStream().readAllBytes()).hasSize(LIMIT + 1);
    }

    // ---------- Content-Length なし（chunked。D-7 #5・#6・#7） ----------

    @Test
    void chunked_atLimit_passesSameBody() throws Exception {
        byte[] body = bytes(LIMIT);
        body[0] = '{';
        body[LIMIT - 1] = '}';

        filter.doFilter(new ChunkedRequest("POST", "/api/posts/1/comments", "application/json", body),
                new MockHttpServletResponse(), chain);

        // 先に読んだ本文が、欠けずに後ろに届く
        assertThat(readBody.get()).isEqualTo(body);
    }

    @Test
    void chunked_overLimit_isRejected_afterReadingOnlyLimitPlusOne() throws Exception {
        ChunkedRequest request = new ChunkedRequest("POST", "/api/auth/signup", "application/json", bytes(LIMIT + 100));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertRejected(response);
        // 上限＋1バイトだけ読んで止めている（残りは読んでいない）
        assertThat(request.getInputStream().readAllBytes()).hasSize(99);
    }

    @Test
    void noBody_passesThrough() throws Exception {
        filter.doFilter(new ChunkedRequest("GET", "/api/timeline", null, new byte[0]), new MockHttpServletResponse(), chain);

        assertThat(readBody.get()).isEmpty();
    }

    @Test
    void cachedBody_canBeReadAsText_withRequestEncodingOrUtf8() throws Exception {
        String text = "{\"body\":\"こんにちは\"}";
        FilterChain readsText = (req, res) -> readBody.set(req.getReader().readLine().getBytes(StandardCharsets.UTF_8));

        ChunkedRequest utf8 = new ChunkedRequest("POST", "/api/posts/1/comments", "application/json",
                text.getBytes(StandardCharsets.UTF_8));
        filter.doFilter(utf8, new MockHttpServletResponse(), readsText);
        assertThat(new String(readBody.get(), StandardCharsets.UTF_8)).isEqualTo(text);

        ChunkedRequest shiftJis = new ChunkedRequest("POST", "/api/posts/1/comments", "application/json",
                text.getBytes("Shift_JIS"));
        shiftJis.setCharacterEncoding("Shift_JIS");
        filter.doFilter(shiftJis, new MockHttpServletResponse(), readsText);
        assertThat(new String(readBody.get(), StandardCharsets.UTF_8)).isEqualTo(text);
    }

    @Test
    void cachedBodyStream_behavesLikeServletInputStream() throws Exception {
        FilterChain checksStream = (req, res) -> {
            ServletInputStream in = req.getInputStream();
            assertThat(in.isReady()).isTrue();
            assertThat(in.isFinished()).isFalse();
            // 1バイトずつ読んでも、まとめて読んでも、同じ本文
            assertThat(in.read()).isEqualTo('a');
            assertThat(in.read()).isEqualTo('b');
            assertThat(in.isFinished()).isTrue();
            assertThat(in.read()).isEqualTo(-1);
            // 非同期の読み込みは使わない（使おうとしたら気づけるように、例外にする）
            assertThatThrownBy(() -> in.setReadListener(null)).isInstanceOf(UnsupportedOperationException.class);
            readBody.set(new byte[0]);
        };

        filter.doFilter(new ChunkedRequest("POST", "/api/posts/1/comments", "application/json", "ab".getBytes()),
                new MockHttpServletResponse(), checksStream);

        assertThat(readBody.get()).isEmpty();
    }

    // ---------- multipart（D-7 #1・#2） ----------

    @ParameterizedTest
    @CsvSource({
        "POST,  /api/posts",
        "PATCH, /api/posts/123",
        "PATCH, /api/users/me",
    })
    void imageUploadApis_areNotChecked(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContentType("multipart/form-data; boundary=abc");
        request.setContent(bytes(LIMIT * 4));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // 画像は Spring の上限（1枚 5MB・全体 21MB）に任せる
        assertThat(passed.get()).isSameAs(request);
    }

    @Test
    void contentTypeIsCaseInsensitive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/posts");
        request.setContentType("Multipart/Form-Data; boundary=abc");
        request.setContent(bytes(LIMIT + 1));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(passed.get()).isSameAs(request);
    }

    @ParameterizedTest
    @CsvSource({
        // 画像つきの API ではないのに multipart と書いて送る
        "POST,  /api/auth/login",
        "POST,  /api/posts/123/comments",
        // メソッド・パスが少しだけ違う
        "PUT,   /api/posts",
        "POST,  /api/posts/",
        "PATCH, /api/posts/123/likes",
        "POST,  /api/users/me",
    })
    void multipartToOtherApis_isLimited(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContentType("multipart/form-data; boundary=abc");
        request.setContent(bytes(LIMIT + 1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        // multipart と書けば上限を逃れられる、にならない
        assertRejected(response);
    }
}
