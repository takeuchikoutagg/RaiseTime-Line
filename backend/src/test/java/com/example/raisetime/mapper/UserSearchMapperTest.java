package com.example.raisetime.mapper;

import com.example.raisetime.entity.User;
import com.example.raisetime.entity.UserSummary;
import com.example.raisetime.service.UserServiceTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ユーザー検索（UserMapper.search）の SQL を、Docker の PostgreSQL で実際に実行して確認する。テストごとにロールバックされる。
// 開発用の DB にはテストデータのユーザーもいるので、ほかと重ならないキーワード（qzx）で確かめる
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserSearchMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FollowMapper followMapper;

    private Long viewer;

    private Long insertUser(String username, String name) {
        User user = new User(username, name, username + "@example.com", "$2a$10$hash");
        userMapper.insert(user);
        return user.getId();
    }

    private List<String> search(String keyword, int limit, int offset) {
        String lower = keyword.toLowerCase();
        return userMapper.search(lower, UserServiceTestSupport.escapeLike(lower), viewer, limit, offset).stream()
                .map(UserSummary::getUsername)
                .toList();
    }

    @BeforeEach
    void setUp() {
        viewer = insertUser("qzx_viewer", "見ている人");
        insertUser("aqzx", "部分一致");
        insertUser("qzx", "完全一致");
        insertUser("qzxb", "前方一致B");
        insertUser("qzxa", "前方一致A");
        insertUser("other1", "名前にQZXを含む");
    }

    @Test
    void ordersExactThenPrefixThenOthers_andByUsernameWithinEachGroup() {
        // 完全一致 → 前方一致（@ID の昇順）→ その他（@ID の昇順）
        assertThat(search("qzx", 20, 0)).containsExactly("qzx", "qzx_viewer", "qzxa", "qzxb", "aqzx", "other1");
    }

    @Test
    void isCaseInsensitive_forUsernameAndName() {
        assertThat(search("QZX", 20, 0)).containsExactly("qzx", "qzx_viewer", "qzxa", "qzxb", "aqzx", "other1");
        assertThat(search("前方一致", 20, 0)).containsExactly("qzxa", "qzxb");
    }

    @Test
    void underscoreAndPercent_areMatchedLiterally() {
        // エスケープしないと「_」は任意の1文字になり、qzx_viewer 以外もヒットしてしまう
        assertThat(search("qzx_", 20, 0)).containsExactly("qzx_viewer");
        assertThat(search("%qzx", 20, 0)).isEmpty();
    }

    // SQL インジェクション：キーワードは #{} で値として渡すので、' や ; があっても SQL の一部として解釈されない
    @Test
    void sqlInKeyword_isTreatedAsPlainText() {
        insertUser("qzx_quote", "O'Brien");

        // 「' OR '1'='1」が SQL になると全員がヒットしてしまう
        assertThat(search("' OR '1'='1", 20, 0)).isEmpty();
        assertThat(search("qzx'; DELETE FROM users; --", 20, 0)).isEmpty();
        // ' を含む名前も、ただの文字として探せる。上の DELETE も実行されていない
        assertThat(search("o'b", 20, 0)).containsExactly("qzx_quote");
        assertThat(search("qzx", 20, 0)).hasSize(7);
    }

    @Test
    void limitAndOffset_pageThroughResults() {
        assertThat(search("qzx", 2, 0)).containsExactly("qzx", "qzx_viewer");
        assertThat(search("qzx", 2, 2)).containsExactly("qzxa", "qzxb");
        assertThat(search("qzx", 2, 6)).isEmpty();
    }

    @Test
    void followedByMe_isTrueOnlyForFolloweesOfViewer() {
        Long target = userMapper.findByUsername("qzxa").getId();
        followMapper.insertIgnore(viewer, target);

        List<UserSummary> rows = userMapper.search("qzxa", "qzxa", viewer, 20, 0);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(target);
            assertThat(row.getName()).isEqualTo("前方一致A");
            assertThat(row.isFollowedByMe()).isTrue();
        });
        assertThat(userMapper.search("qzxb", "qzxb", viewer, 20, 0).getFirst().isFollowedByMe()).isFalse();
    }
}
