-- ===== ユーザー =====
-- アプリ利用者（認証・権限付与の対象）
CREATE TABLE IF NOT EXISTS app_users (
  -- 主キー：ユーザー名（ログインID想定）
  username VARCHAR(128) PRIMARY KEY,
  -- パスワードハッシュ等（平文は想定しない）
  password VARCHAR(200) NOT NULL,
  -- ロール（権限グループ）
  role VARCHAR(32) NOT NULL DEFAULT 'USER', -- USER | ADMIN | USER_MGR | MODEL_MGR | NEWS_MGR | DEMO
  -- 有効/無効フラグ（デフォルト有効）
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  -- 作成日時（デフォルト現在時刻）
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- ロール別検索/集計を速くするためのインデックス
CREATE INDEX IF NOT EXISTS idx_app_users_role ON app_users(role);

-- ロール値の制約は DB では強制しない（アプリ側で正規化して扱う）
-- - UserAccountService.normalizeRole により、許容ロール以外は USER 扱いになる
-- - ただし seed/初期データは「許容ロールのみ」を投入して、運用上のズレを避ける

-- ADMINユーザーの件数/usernameはアプリ側で運用する（DB制約では固定しない）

-- ADMINユーザーは削除不可。role/username/enabled 変更不可（password変更は許可）
CREATE OR REPLACE FUNCTION fn_guard_admin_user() RETURNS trigger AS $$
BEGIN
  IF (TG_OP = 'DELETE') THEN
    IF OLD.role = 'ADMIN' THEN
      RAISE EXCEPTION 'adminユーザーは削除できません';
    END IF;
    RETURN OLD;
  ELSIF (TG_OP = 'UPDATE') THEN
    IF OLD.role = 'ADMIN' THEN
      IF NEW.username <> OLD.username THEN
        RAISE EXCEPTION 'adminユーザーのusernameは変更できません';
      END IF;
      IF NEW.role <> OLD.role THEN
        RAISE EXCEPTION 'adminユーザーのroleは変更できません';
      END IF;
      IF NEW.enabled <> OLD.enabled THEN
        RAISE EXCEPTION 'adminユーザーは有効/無効を変更できません';
      END IF;
    END IF;
    RETURN NEW;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'tr_guard_admin_user'
  ) THEN
    CREATE TRIGGER tr_guard_admin_user
    BEFORE UPDATE OR DELETE ON app_users
    FOR EACH ROW
    EXECUTE FUNCTION fn_guard_admin_user();
  END IF;
END $$;
