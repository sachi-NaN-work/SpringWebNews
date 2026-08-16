-- ニュースの分類（カテゴリ）マスタ
CREATE TABLE IF NOT EXISTS categories (
  -- 主キー：UUID（デフォルトで自動採番）
  category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  -- URL等で使う短い識別子（重複禁止）
  slug VARCHAR(10) NOT NULL UNIQUE,
  -- 表示名
  category_name VARCHAR(10) NOT NULL,
  -- 有効/無効フラグ（デフォルト有効）
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- カテゴリ名も重複禁止（大文字小文字無視）
CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_name_ci ON categories (lower(category_name));
-- 有効カテゴリ絞り込みを速くするためのインデックス
CREATE INDEX IF NOT EXISTS idx_categories_enabled ON categories(enabled);
