-- ニュースとハッシュタグの紐付け（学習用）
--
-- userMatch は「カテゴリ + タグ」の嗜好一致度を使うため、タグ一覧が必要。
-- Java 側は enabled=true の hashtag のみを候補として扱うため、学習側も同条件で取得する。
SELECT nh.news_id, nh.hashtag_id
FROM news_hashtags nh
JOIN hashtags h ON h.hashtag_id = nh.hashtag_id
WHERE h.enabled = TRUE
ORDER BY nh.news_id ASC, nh.hashtag_id ASC

