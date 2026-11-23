package com.example.raisetime.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Optional;

// 利用者の IP アドレスを決める（docs/テスト設計書.md D-6）。ログインの総当たり対策（同じ IP の失敗回数）とログの client_ip で使う。
// 本番は「利用者 → CloudFront → ALB → アプリ」の順に通るので、getRemoteAddr() は CloudFront の IP になる。
// そこで、CloudFront が付けるヘッダー（CloudFront-Viewer-Address: IP:ポート）から、本当の利用者の IP を取る。
// ヘッダーを信じてよいのは CloudFront を通ってきた通信だけ。CloudFront 以外から届かないようにするのはインフラの役目
// （ALB は CloudFront が付ける秘密のヘッダーがあるときだけ転送する。docs/インフラ構成図.md 7章）
@Component
public class ClientIpResolver {

    // いちばん長い正しい値は 51文字（IPv6 の45文字＋「:65535」）。これより十分長いものは読まずに断る
    static final int MAX_HEADER_LENGTH = 64;
    private static final int MAX_PORT = 65535;

    // ヘッダーの名前。空なら使わない（開発・テスト。CloudFront を通らないので、送られてきても信じない）
    private final String headerName;

    public ClientIpResolver(@Value("${app.client-ip.header:}") String headerName) {
        this.headerName = headerName == null ? "" : headerName.trim();
    }

    public String resolve(HttpServletRequest request) {
        if (headerName.isEmpty()) {
            return request.getRemoteAddr();
        }
        return parseViewerAddress(request.getHeader(headerName)).orElseGet(request::getRemoteAddr);
    }

    // 「IP:ポート」の形のときだけ、IP を決まった形の文字に直して返す（ログに送られた文字がそのまま入らないように）。
    // IPv6 にもコロンが入るので、最後のコロンで分ける（例：2001:db8::1:443 → 2001:db8::1 と 443）
    static Optional<String> parseViewerAddress(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_HEADER_LENGTH) {
            return Optional.empty();
        }
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || !isPort(value.substring(colon + 1))) {
            return Optional.empty();
        }
        try {
            // ofLiteral は IP アドレスの文字だけを読む（ホスト名を DNS に問い合わせない）
            return Optional.of(InetAddress.ofLiteral(value.substring(0, colon)).getHostAddress());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean isPort(String port) {
        if (port.isEmpty() || port.length() > 5 || !port.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        return Integer.parseInt(port) <= MAX_PORT;
    }
}
