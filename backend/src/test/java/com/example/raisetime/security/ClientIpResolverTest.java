package com.example.raisetime.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

// 利用者の IP アドレスの決め方（docs/テスト設計書.md 2.1「利用者の IP アドレスのヘッダー」・D-6）
class ClientIpResolverTest {

    private static final String HEADER = "CloudFront-Viewer-Address";
    private static final String REMOTE_ADDR = "10.0.1.23";

    // ---------- 値の形（2.1。T-38） ----------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "203.0.113.5:54321                         | 203.0.113.5",
        // IPv6 にもコロンが入るので、最後のコロンで分ける。決まった形の文字に直す
        "2001:db8::1:443                           | 2001:db8:0:0:0:0:0:1",
        "2001:0DB8:0000:0000:0000:0000:0000:0001:1 | 2001:db8:0:0:0:0:0:1",
        "[2001:db8::1]:443                         | 2001:db8:0:0:0:0:0:1",
        "::ffff:203.0.113.5:443                    | 203.0.113.5",
        // IP の境界値
        "255.255.255.255:80                        | 255.255.255.255",
        // ポートの境界値
        "203.0.113.5:0                             | 203.0.113.5",
        "203.0.113.5:65535                         | 203.0.113.5",
    })
    void validValue_returnsNormalizedIp(String value, String expected) {
        assertThat(ClientIpResolver.parseViewerAddress(value)).contains(expected);
    }

    @Test
    void longestValidValue_isAccepted() {
        // IPv6 の45文字（IPv4 の形を含む、いちばん長い書き方）＋「:65535」＝51文字
        String value = "ffff:ffff:ffff:ffff:ffff:ffff:255.255.255.255:65535";
        assertThat(value).hasSize(51);
        assertThat(ClientIpResolver.parseViewerAddress(value)).contains("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        // IP の部分が正しくない
        "256.0.0.1:80",
        "example.com:443",
        "localhost:80",
        "fe80::1%eth0:443",
        // ポートの部分が正しくない
        "203.0.113.5:65536",
        "203.0.113.5:",
        "203.0.113.5:http",
        "203.0.113.5:-1",
        "203.0.113.5:123456",
        "203.0.113.5:１２３",
        // ポートが無い（IPv4 はコロンが無い。IPv6 は最後の部分をポートと読むので、IP が欠ける）
        "203.0.113.5",
        "2001:db8::1",
        ":80",
        // 空白・改行（ログに偽の行を作られないように、形が違えば使わない）
        " 203.0.113.5:80",
        "203.0.113.5:80 ",
        "203.0.113.5:80\n",
        "203.0.113.5\n:80",
        "203.0.113.5, 198.51.100.1:80",
    })
    void invalidValue_isNotUsed(String value) {
        assertThat(ClientIpResolver.parseViewerAddress(value)).isEmpty();
    }

    // 長さの境界値。IPv6 は区切りの中を0でいくらでも長くでき、ofLiteral はそれも読めるので、長さで先に断る
    private static String paddedIpv6WithPort(int totalLength) {
        String head = "0000:0000:0000:0000:0000:0000:0000:";
        String port = ":443";
        return head + "0".repeat(totalLength - head.length() - port.length() - 1) + "1" + port;
    }

    @Test
    void valueOfMaxLength_isUsed() {
        String value = paddedIpv6WithPort(ClientIpResolver.MAX_HEADER_LENGTH);
        assertThat(value).hasSize(64);
        assertThat(ClientIpResolver.parseViewerAddress(value)).contains("0:0:0:0:0:0:0:1");
    }

    @Test
    void valueOverMaxLength_isNotUsed() {
        String value = paddedIpv6WithPort(ClientIpResolver.MAX_HEADER_LENGTH + 1);
        assertThat(value).hasSize(65);
        assertThat(ClientIpResolver.parseViewerAddress(value)).isEmpty();
    }

    // ---------- 使う IP アドレスの決め方（D-6。T-39） ----------

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(REMOTE_ADDR);
        return request;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void d6_1_headerNotConfigured_usesRemoteAddr_evenIfHeaderIsSent(String configuredName) {
        MockHttpServletRequest request = request();
        request.addHeader(HEADER, "203.0.113.5:54321");

        assertThat(new ClientIpResolver(configuredName).resolve(request)).isEqualTo(REMOTE_ADDR);
    }

    @Test
    void d6_2_headerConfigured_butMissingOrEmpty_usesRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver(HEADER);
        assertThat(resolver.resolve(request())).isEqualTo(REMOTE_ADDR);

        MockHttpServletRequest empty = request();
        empty.addHeader(HEADER, "");
        assertThat(resolver.resolve(empty)).isEqualTo(REMOTE_ADDR);
    }

    @Test
    void d6_3_headerConfigured_invalidValue_usesRemoteAddr() {
        MockHttpServletRequest request = request();
        request.addHeader(HEADER, "evil\nvalue");

        assertThat(new ClientIpResolver(HEADER).resolve(request)).isEqualTo(REMOTE_ADDR);
    }

    @Test
    void d6_4_headerConfigured_validValue_usesHeaderIp() {
        MockHttpServletRequest request = request();
        // ヘッダーの名前は、大文字・小文字を区別しない（HTTP の決まり）
        request.addHeader("cloudfront-viewer-address", "203.0.113.5:54321");

        assertThat(new ClientIpResolver(HEADER).resolve(request)).isEqualTo("203.0.113.5");
        // 設定の前後の空白は無視する
        assertThat(new ClientIpResolver(" " + HEADER + " ").resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void d6_5_sameHeaderTwice_usesFirst() {
        MockHttpServletRequest request = request();
        request.addHeader(HEADER, "203.0.113.5:54321");
        request.addHeader(HEADER, "198.51.100.7:1234");

        assertThat(new ClientIpResolver(HEADER).resolve(request)).isEqualTo("203.0.113.5");
    }

    // ---------- 設定（本番は使う・開発は使わない） ----------

    @Test
    void productionUsesCloudFrontHeader_andDevDoesNot() throws Exception {
        Properties production = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        Properties dev = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-dev.properties"));

        assertThat(production.getProperty("app.client-ip.header")).isEqualTo("${CLIENT_IP_HEADER:" + HEADER + "}");
        // 開発で使うと、ヘッダーを送るだけで好きな IP のふりをして、ログインの失敗回数の制限を逃れられる
        assertThat(dev.getProperty("app.client-ip.header")).isEmpty();
    }
}
