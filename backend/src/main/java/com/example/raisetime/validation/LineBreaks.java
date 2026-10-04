package com.example.raisetime.validation;

// 改行を LF（\n）にそろえる。
// ブラウザは multipart/form-data で文字を送るとき、改行を CRLF（\r\n）に変える（FormData の仕様）。
// そのままだと改行1つを2文字と数えてしまい、画面では280文字（自己紹介は160文字）に収まっているのに
// サーバーでは超えてしまう。投稿の本文・プロフィールなど、multipart で受け取る文字は、チェックと保存の前にそろえる
public final class LineBreaks {

    private LineBreaks() {
    }

    public static String normalize(String text) {
        return text == null ? null : text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
