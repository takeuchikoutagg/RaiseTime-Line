package com.example.raisetime.mapper;

import com.example.raisetime.entity.PostImage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

// SQL は src/main/resources/mapper/PostImageMapper.xml
@Mapper
public interface PostImageMapper {

    void insert(PostImage image);

    // 複数の投稿の画像をまとめて取る（投稿ごとに1回ずつ取る N+1 問題を避ける）。投稿 id・表示順の順に並ぶ
    List<PostImage> findByPostIds(List<Long> postIds);
}
