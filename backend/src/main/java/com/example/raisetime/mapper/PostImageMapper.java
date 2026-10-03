package com.example.raisetime.mapper;

import com.example.raisetime.entity.PostImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// SQL は src/main/resources/mapper/PostImageMapper.xml
@Mapper
public interface PostImageMapper {

    void insert(PostImage image);

    // 複数の投稿の画像をまとめて取る（投稿ごとに1回ずつ取る N+1 問題を避ける）。投稿 id・表示順の順に並ぶ
    List<PostImage> findByPostIds(List<Long> postIds);

    // 編集（F-10）で外した画像の行を消す
    int deleteByIds(List<Long> ids);

    // 編集（F-10）で残す画像の表示順を変える
    int updatePosition(@Param("id") Long id, @Param("position") int position);

    // このトランザクションの間だけ、UNIQUE(post_id, position) のチェックをコミット時まで遅らせる。
    // 表示順を1件ずつ入れ替えるとき、途中で一時的に重なってもエラーにしないため（V8 のマイグレーション）
    void deferPositionCheck();
}
