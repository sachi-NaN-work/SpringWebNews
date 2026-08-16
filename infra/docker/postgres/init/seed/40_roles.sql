-- ===== ロール =====
INSERT INTO
    roles (role_code, role_name)
VALUES
    -- 管理者（全権限）
    ('ADMIN', '管理者'),
    -- ユーザー管理担当
    ('USER_MGR', 'ユーザー管理者'),
    -- 学習モデル管理担当
    ('MODEL_MGR', '学習モデル管理者'),
    -- ニュース管理担当（カテゴリ/記事/ハッシュタグの作成・編集・有効無効）
    ('NEWS_MGR', 'ニュース管理者'),
    -- デモユーザー（学習モデル管理者相当、パスワード変更不可）
    ('DEMO', 'デモユーザー'),
    -- 一般ユーザー
    ('USER', '一般ユーザー')
ON CONFLICT (role_code) DO NOTHING;
