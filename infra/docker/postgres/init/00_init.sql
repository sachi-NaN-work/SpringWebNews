-- 初期化SQL実行
\set ON_ERROR_STOP on

\i /docker-entrypoint-initdb.d/schema/01_extensions.sql
\i /docker-entrypoint-initdb.d/schema/10_categories.sql
\i /docker-entrypoint-initdb.d/schema/20_news.sql
\i /docker-entrypoint-initdb.d/schema/30_hashtags.sql
\i /docker-entrypoint-initdb.d/schema/35_news_constraints.sql
\i /docker-entrypoint-initdb.d/schema/40_feedback.sql
\i /docker-entrypoint-initdb.d/schema/50_app_users.sql
\i /docker-entrypoint-initdb.d/schema/60_roles_permissions.sql

-- seed はアプリ仕様（news_content 必須 / ハッシュタグ 1〜5件）を DB 側でも強制するため
-- 1トランザクションで投入し、DEFERRABLE な制約トリガーをコミット時に検証させる。
BEGIN;
\i /docker-entrypoint-initdb.d/seed/10_categories.sql
\i /docker-entrypoint-initdb.d/seed/20_news.sql
\i /docker-entrypoint-initdb.d/seed/21_news_content.sql
\i /docker-entrypoint-initdb.d/seed/30_users.sql
\i /docker-entrypoint-initdb.d/seed/40_roles.sql
\i /docker-entrypoint-initdb.d/seed/41_permissions.sql
\i /docker-entrypoint-initdb.d/seed/42_role_permissions.sql
\i /docker-entrypoint-initdb.d/seed/50_hashtags.sql
\i /docker-entrypoint-initdb.d/seed/51_news_hashtags.sql
COMMIT;
