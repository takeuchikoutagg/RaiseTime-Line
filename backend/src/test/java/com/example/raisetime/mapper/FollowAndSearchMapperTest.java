package com.example.raisetime.mapper;

import com.example.raisetime.entity.User;
import com.example.raisetime.entity.UserProfile;
import com.example.raisetime.entity.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// フォロー（FollowMapper）と、ユーザー情報・検索（UserMapper）の SQL を、Docker の PostgreSQL で実際に実行して確認する。
// テストごとにロールバックされる。開発中のデータと混ざらないよう、@ユーザーID は「zq」で始まる名前にしている
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FollowAndSearchMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FollowMapper followMapper;

    private Long taro;
    private Long hanako;
    private Long taroFan;

    private Long insertUser(String username, String name) {
        User user = new User(username, name, username + "@example.com", "$2a$10$hash");
        userMapper.insert(user);
        return user.getId();
    }

    @BeforeEach
    void createUsers() {
        taro = insertUser("zqtaro", "山田太郎");
        hanako = insertUser("zqhanako", "佐藤花子");
        taroFan = insertUser("zqtaro_fan", "タロウ好き");
    }

    // ---------- フォロー ----------

    @Test
    void insertIfAbsent_secondTimeDoesNothing() {
        assertThat(followMapper.insertIfAbsent(taro, hanako)).isEqualTo(1);
        assertThat(followMapper.insertIfAbsent(taro, hanako)).isZero();

        assertThat(followMapper.exists(taro, hanako)).isTrue();
        assertThat(followMapper.exists(hanako, taro)).isFalse();
        assertThat(followMapper.countFollowers(hanako)).isEqualTo(1);
        assertThat(followMapper.countFollowing(taro)).isEqualTo(1);
    }

    @Test
    void delete_removesOnlyThatFollow_andSecondTimeDoesNothing() {
        followMapper.insertIfAbsent(taro, hanako);
        followMapper.insertIfAbsent(taroFan, hanako);

        assertThat(followMapper.delete(taro, hanako)).isEqualTo(1);
        assertThat(followMapper.delete(taro, hanako)).isZero();
        assertThat(followMapper.countFollowers(hanako)).isEqualTo(1);
    }

    @Test
    void followSelf_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> followMapper.insertIfAbsent(taro, taro))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_follows_not_self");
    }

    // ---------- ユーザー情報 ----------

    @Test
    void findProfile_countsFollowsAndChecksFollowedByViewer() {
        followMapper.insertIfAbsent(taro, hanako);
        followMapper.insertIfAbsent(taroFan, hanako);
        followMapper.insertIfAbsent(hanako, taro);

        UserProfile seenByTaro = userMapper.findProfile("zqhanako", taro);
        assertThat(seenByTaro.getId()).isEqualTo(hanako);
        assertThat(seenByTaro.getName()).isEqualTo("佐藤花子");
        assertThat(seenByTaro.getFollowerCount()).isEqualTo(2);
        assertThat(seenByTaro.getFollowingCount()).isEqualTo(1);
        assertThat(seenByTaro.isFollowedByMe()).isTrue();

        assertThat(userMapper.findProfile("zqhanako", hanako).isFollowedByMe()).isFalse();
        assertThat(userMapper.findProfile("zqnobody", taro)).isNull();
    }

    @Test
    void findByUsername_returnsUserOrNull() {
        assertThat(userMapper.findByUsername("zqtaro").getId()).isEqualTo(taro);
        assertThat(userMapper.findByUsername("zqnobody")).isNull();
    }

    // ---------- 検索 ----------

    private List<String> search(String escapedKeyword) {
        return userMapper.search(escapedKeyword, hanako, 20, 0).stream().map(UserSummary::getUsername).toList();
    }

    @Test
    void search_ordersExactThenPrefixThenOthers_byUsername() {
        Long other = insertUser("aazqtaro", "別の人");

        // 完全一致（zqtaro）→ 前方一致（zqtaro_fan）→ 部分一致（aazqtaro）
        assertThat(search("zqtaro")).containsExactly("zqtaro", "zqtaro_fan", "aazqtaro");
        assertThat(other).isNotNull();
    }

    @Test
    void search_matchesNameToo_caseInsensitively() {
        insertUser("zqalice", "ZqAlice Smith");

        assertThat(search("花子")).containsExactly("zqhanako");
        assertThat(search("zqalice smith")).containsExactly("zqalice");
    }

    @Test
    void search_escapedUnderscore_isNotAWildcard() {
        // 「_」は LIKE では「任意の1文字」。エスケープしていれば、本当に _ を含む人だけがヒットする
        assertThat(search("zqtaro\\_")).containsExactly("zqtaro_fan");
        // エスケープしないと、_ が任意の1文字になり「zqtaroX」のような人もヒットしてしまう
        insertUser("zqtarox", "エックス");
        assertThat(search("zqtaro_")).contains("zqtarox");
        assertThat(search("zqtaro\\_")).doesNotContain("zqtarox");
    }

    @Test
    void search_setsFollowedByMe_andSupportsLimitOffset() {
        followMapper.insertIfAbsent(hanako, taro);

        List<UserSummary> first = userMapper.search("zqtaro", hanako, 1, 0);
        List<UserSummary> second = userMapper.search("zqtaro", hanako, 1, 1);

        assertThat(first).extracting(UserSummary::getUsername).containsExactly("zqtaro");
        assertThat(first.getFirst().isFollowedByMe()).isTrue();
        assertThat(second).extracting(UserSummary::getUsername).containsExactly("zqtaro_fan");
        assertThat(second.getFirst().isFollowedByMe()).isFalse();
    }
}
