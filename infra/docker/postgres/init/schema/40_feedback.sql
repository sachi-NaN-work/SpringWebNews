-- ユーザー行動（クリック/お気に入り等）の記録
CREATE TABLE IF NOT EXISTS feedback (
  -- 主キー：連番
  feedback_id BIGSERIAL PRIMARY KEY,
  -- ユーザー識別子（アプリ側のID）
  user_id VARCHAR(128) NOT NULL,
  -- 対象ニュース（必須）
  news_id UUID NOT NULL REFERENCES news(news_id),
  -- 対象カテゴリ（必須）
  category_id UUID NOT NULL REFERENCES categories(category_id),
  -- 行動種別（例：click|favorite）
  feedback_type VARCHAR(32) NOT NULL, -- click|favorite
  -- 記録日時（デフォルト現在時刻）
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- ユーザー別に最新の行動を追うための複合インデックス
CREATE INDEX IF NOT EXISTS idx_feedback_user_created ON feedback(user_id, created_at DESC);

-- good/bad の状態復元（ユーザー×ニュース単位）を高速化するためのインデックス
CREATE INDEX IF NOT EXISTS idx_feedback_user_news_type_created
  ON feedback(user_id, news_id, feedback_type, created_at DESC);
