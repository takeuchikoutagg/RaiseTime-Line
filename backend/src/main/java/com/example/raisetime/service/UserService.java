package com.example.raisetime.service;

import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.dto.UserSearchResponse;
import com.example.raisetime.dto.UserSummaryResponse;
import com.example.raisetime.entity.UserProfile;
import com.example.raisetime.entity.UserSummary;
import com.example.raisetime.exception.BadRequestException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

// F-08 ユーザーページ（ユーザー情報）/ F-09 ユーザー検索
@Service
public class UserService {

    static final String USER_NOT_FOUND = "ユーザーが見つかりません";
    static final String KEYWORD_MESSAGE = "検索キーワードは1〜50文字で入力してください";
    static final int PAGE_SIZE = 20;
    private static final int MAX_KEYWORD_LENGTH = 50;

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String username, Long viewerId) {
        UserProfile profile = userMapper.findProfile(username, viewerId);
        if (profile == null) {
            throw new NotFoundException(USER_NOT_FOUND);
        }
        return UserProfileResponse.of(profile, viewerId);
    }

    // 前後の空白と先頭の @ を除いたキーワードで、@ユーザーID・名前を部分一致で探す（大文字小文字は区別しない）
    @Transactional(readOnly = true)
    public UserSearchResponse search(String rawKeyword, int offset, Long viewerId) {
        String keyword = normalizeKeyword(rawKeyword);
        if (offset < 0) {
            throw new BadRequestException("offset は0以上にしてください");
        }
        // 1件多く取り、次のページがあるかを判定する
        List<UserSummary> rows = userMapper.search(escapeLike(keyword), viewerId, PAGE_SIZE + 1, offset);
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<UserSummaryResponse> items = rows.stream()
                .limit(PAGE_SIZE)
                .map(row -> UserSummaryResponse.of(row, viewerId))
                .toList();
        return new UserSearchResponse(items, hasMore ? offset + PAGE_SIZE : null);
    }

    static String normalizeKeyword(String rawKeyword) {
        String keyword = rawKeyword == null ? "" : rawKeyword.strip();
        if (keyword.startsWith("@")) {
            keyword = keyword.substring(1).strip();
        }
        int length = keyword.codePointCount(0, keyword.length());
        if (length < 1 || length > MAX_KEYWORD_LENGTH) {
            throw new BadRequestException(KEYWORD_MESSAGE);
        }
        return keyword.toLowerCase(Locale.ROOT);
    }

    // LIKE の特殊文字（\ % _）を普通の文字として扱わせる。例えば「_」で検索すると全員がヒットしてしまうのを防ぐ
    static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
