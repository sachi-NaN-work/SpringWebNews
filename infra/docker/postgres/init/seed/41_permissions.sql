-- ===== 権限 =====
-- permissions (permission_code, permission_name)
INSERT INTO permissions (permission_code, permission_name) VALUES
  ('PASSWORD_CHANGE', '自分のパスワード変更'),
  ('USER_CREATE', 'ユーザーの新規追加'),
  ('USER_ENABLE_DISABLE', 'ユーザーの有効/無効化'),
  ('USER_ROLE_CHANGE', 'ユーザーのロール変更'),
  ('MODEL_RELOAD', '学習モデル読込（再読込）'),
  ('MODEL_UPLOAD', '学習モデルアップロード'),
  ('MODEL_TRAIN', '学習モデル作成（学習実行）'),
  ('NEWS_CATEGORY_CREATE', 'カテゴリ作成'),
  ('NEWS_ARTICLE_CREATE', '記事作成'),
  ('NEWS_CATEGORY_DISABLE', 'カテゴリの有効/無効切替'),
  ('NEWS_ARTICLE_DISABLE', '記事の有効/無効切替'),
  ('NEWS_HASHTAG_MASTER', 'ハッシュタグマスタ管理'),
  ('NEWS_ARTICLE_HASHTAG_EDIT', '既存ニュースのハッシュタグ編集')
ON CONFLICT (permission_code) DO NOTHING;
