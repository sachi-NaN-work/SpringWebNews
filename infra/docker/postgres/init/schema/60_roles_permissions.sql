-- ===== ロール・権限 =====
-- ロール（権限セット）の定義マスタ
CREATE TABLE IF NOT EXISTS roles (
  -- 主キー：ロールコード
  role_code VARCHAR(32) PRIMARY KEY,
  -- ロール名（表示用）
  role_name VARCHAR(64) NOT NULL
);

-- 権限（アクション）定義マスタ
CREATE TABLE IF NOT EXISTS permissions (
  -- 主キー：権限コード
  permission_code VARCHAR(64) PRIMARY KEY,
  -- 権限名（表示用）
  permission_name VARCHAR(128) NOT NULL
);

-- ロールと権限の紐付け（多対多）
CREATE TABLE IF NOT EXISTS role_permissions (
  -- 外部キー：ロールコード（ロール削除時に紐付けも削除）
  role_code VARCHAR(32) NOT NULL REFERENCES roles(role_code) ON DELETE CASCADE,
  -- 外部キー：権限コード（権限削除時に紐付けも削除）
  permission_code VARCHAR(64) NOT NULL REFERENCES permissions(permission_code) ON DELETE CASCADE,
  -- 複合主キー（同じ紐付けの重複を防止）
  PRIMARY KEY (role_code, permission_code)
);
-- ロール別に権限を引く検索を速くするためのインデックス
CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role_code);
