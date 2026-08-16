-- ===== ユーザー =====
-- ログイン用ユーザーの初期データ投入（パスワードは bcrypt ハッシュ）
--
-- 【重要】ここに記載する平文パスワードは「開発・学習用途のサンプル」想定です。
-- 本番環境では絶対に使用しないでください。
--
-- 【アプリ側と揃えるポイント】
-- - パスワードポリシー（8文字以上 + 英大文字/英小文字/数字を含む）に合わせ、
--   平文を用意し、bcrypt でハッシュ化して格納します。
-- - password 列は {bcrypt} プレフィックス付き。

-- ▼ログイン用ユーザー（平文→bcrypt格納）
--   - NewsSampleUser01 (pwd:NewsSampleUser01)

/*
INSERT INTO app_users (username, password, role, enabled) VALUES
 (
     'NewsSampleUser01',
     '{bcrypt}$2y$10$m/7k2M23avMKyXzuDddkXOTtsa3LPg4lavq.SBZtl0gEysYJEQfGm',
     'DEMO',
     TRUE
 )
ON CONFLICT (username) DO NOTHING;
*/
