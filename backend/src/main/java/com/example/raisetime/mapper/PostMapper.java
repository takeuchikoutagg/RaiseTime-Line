package com.example.raisetime.mapper;

import com.example.raisetime.entity.Post;
import com.example.raisetime.entity.PostWithAuthor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// SQL は src/main/resources/mapper/PostMapper.xml
@Mapper
public interface PostMapper {

    // 採番された id と、DB が入れた created_at は post に入る
    void insert(Post post);

    Post findById(Long id);

    // 投稿詳細（F-05）。投稿者の名前、いいね数・コメント数、viewerId がいいね済みかを合わせて取る。無ければ null
    PostWithAuthor findDetailById(@Param("postId") Long postId, @Param("viewerId") Long viewerId);

    // 自分＋フォロー中の人の投稿を、新しい順（id の大きい順）に limit 件。before があれば、それより古いもの。
    // いいね数・コメント数・viewerId がいいね済みかも合わせて取る
    List<PostWithAuthor> findTimeline(@Param("viewerId") Long viewerId, @Param("before") Long before,
                                      @Param("limit") int limit);

    // ユーザーページ（F-08）：userId の人の投稿を、新しい順に limit 件。before があれば、それより古いもの。
    // いいね数・コメント数・viewerId がいいね済みかも合わせて取る
    List<PostWithAuthor> findByUser(@Param("userId") Long userId, @Param("viewerId") Long viewerId,
                                    @Param("before") Long before, @Param("limit") int limit);

    // タイムラインに、after より新しい（id が大きい）投稿があるか。新しい投稿のお知らせ（F-03）に使う
    boolean existsTimelineNewerThan(@Param("viewerId") Long viewerId, @Param("after") long after);

    // 投稿を消す。画像・コメント・いいねの行は ON DELETE CASCADE で一緒に消える
    int delete(Long id);
}
