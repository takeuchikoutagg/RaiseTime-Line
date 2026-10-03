-- 投稿編集（F-10）で画像の表示順を並べ替えられるよう、UNIQUE(post_id, position) を「遅らせてチェックできる」制約にする。
-- 1件ずつ position を更新すると、途中で一時的に同じ表示順が重なる（例：1番と2番を入れ替える）。
-- ふだんは今までどおりすぐにチェックし（INITIALLY IMMEDIATE）、編集のときだけ
-- SET CONSTRAINTS ... DEFERRED でトランザクションの最後（コミット時）にまとめてチェックする。
-- こうすると、画像の行を消して入れ直さずに済み、編集しても画像の id が変わらない（古い画面から保存しても画像が消えない）
ALTER TABLE post_images DROP CONSTRAINT uq_post_images_post_position;
ALTER TABLE post_images
    ADD CONSTRAINT uq_post_images_post_position UNIQUE (post_id, position) DEFERRABLE INITIALLY IMMEDIATE;
