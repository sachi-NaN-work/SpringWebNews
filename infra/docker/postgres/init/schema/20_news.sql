-- ニュース本体（正規化: 本文/見出しは news_content に分離）
CREATE TABLE IF NOT EXISTS news (
  -- 主キー：UUID（デフォルトで自動採番）
  news_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  -- 外部キー：カテゴリ（必須）
  category_id UUID NOT NULL REFERENCES categories(category_id),
  -- 公開日時（デフォルト現在時刻）
  published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- 有効/無効フラグ（デフォルト有効）
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- ニュース本文/見出し（ニュース本体から分離）
CREATE TABLE IF NOT EXISTS news_content (
  -- 主キー兼外部キー（news と 1:1）
  news_id UUID PRIMARY KEY REFERENCES news(news_id) ON DELETE CASCADE,
  -- タイトル
  news_title VARCHAR(20) NOT NULL,
  -- 本文
  news_body TEXT NOT NULL
);

-- 新着順取得を速くするためのインデックス（降順）
CREATE INDEX IF NOT EXISTS idx_news_published_at ON news(published_at DESC);
-- カテゴリ別取得を速くするためのインデックス
CREATE INDEX IF NOT EXISTS idx_news_category ON news(category_id);
-- 有効ニュース絞り込みを速くするためのインデックス
CREATE INDEX IF NOT EXISTS idx_news_enabled ON news(enabled);
