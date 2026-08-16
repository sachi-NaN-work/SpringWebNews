-- ===== ハッシュタグ =====
-- ハッシュタグマスタ
CREATE TABLE IF NOT EXISTS hashtags (
  hashtag_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  hashtag_name VARCHAR(10) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);
-- 同一名の重複禁止（大文字小文字無視）
CREATE UNIQUE INDEX IF NOT EXISTS uq_hashtags_name_ci ON hashtags (lower(hashtag_name));
CREATE INDEX IF NOT EXISTS idx_hashtags_enabled ON hashtags(enabled);

-- ニュースとハッシュタグの紐付け（要件: テーブル名は news_hashtags のまま）
CREATE TABLE IF NOT EXISTS news_hashtags (
  news_hashtag_id BIGSERIAL PRIMARY KEY,
  news_id UUID NOT NULL REFERENCES news(news_id) ON DELETE CASCADE,
  hashtag_id UUID NOT NULL REFERENCES hashtags(hashtag_id) ON DELETE CASCADE
);
-- 同一ニュースへの同一ハッシュタグ重複登録禁止
CREATE UNIQUE INDEX IF NOT EXISTS uq_news_hashtags_news_hashtag ON news_hashtags(news_id, hashtag_id);
CREATE INDEX IF NOT EXISTS idx_news_hashtags_news_id ON news_hashtags(news_id);
CREATE INDEX IF NOT EXISTS idx_news_hashtags_hashtag_id ON news_hashtags(hashtag_id);
