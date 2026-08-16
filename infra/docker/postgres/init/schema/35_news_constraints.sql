-- ===== ニュース整合性制約（DB側で強制） =====
-- 目的:
--   1) news 作成時に news_content も必須にする（アプリは JOIN news_content 前提）
--   2) news_id ごとのハッシュタグ数を 1〜5 件に制限する（Controller の業務制約）
--   3) enabled=false のハッシュタグは紐付け不可（無効化も不可）
--
-- 実装方針:
--   - (1) は循環参照になるため、news → news_content の外部キーを DEFERRABLE にしてコミット時検証。
--   - (2)(3) は集計/参照整合性を伴うため CHECK 制約では表現できず、
--           DEFERRABLE な CONSTRAINT TRIGGER でコミット時に一括検証する。
--
-- 注意:
--   これらは「コミット時検証」なので、アプリ側は news / news_content / news_hashtags を
--   1トランザクションで確定させる必要がある（NewsAdminArticleController で全体を transactional 化済み）。

-- 後続SQLを流し続けず止める
\set ON_ERROR_STOP on

-- =============================
-- (1) news → news_content を必須化（DEFERRABLE FK）
-- =============================
ALTER TABLE news
  DROP CONSTRAINT IF EXISTS fk_news_requires_news_content;

ALTER TABLE news
  ADD CONSTRAINT fk_news_requires_news_content
  FOREIGN KEY (news_id)
  REFERENCES news_content(news_id)
  DEFERRABLE INITIALLY DEFERRED;

-- =============================
-- (2) ハッシュタグ数 1〜5 件を強制（DEFERRABLE CONSTRAINT TRIGGER）
-- =============================
DROP FUNCTION IF EXISTS validate_news_hashtag_count(UUID);
CREATE OR REPLACE FUNCTION validate_news_hashtag_count(p_news_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $fn$
DECLARE
  v_cnt INTEGER;
BEGIN
  -- news が既に存在しない（同一トランザクション内で削除された等）場合は検証不要
  IF NOT EXISTS (SELECT 1 FROM news WHERE news_id = p_news_id) THEN
    RETURN;
  END IF;

  SELECT COUNT(*) INTO v_cnt FROM news_hashtags WHERE news_id = p_news_id;

  IF v_cnt < 1 OR v_cnt > 5 THEN
    RAISE EXCEPTION 'news % must have 1..5 hashtags (found %)', p_news_id, v_cnt
      USING ERRCODE = '23514';
  END IF;
END;
$fn$;

DROP FUNCTION IF EXISTS trg_validate_hashtag_count_on_news();
CREATE OR REPLACE FUNCTION trg_validate_hashtag_count_on_news()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
  PERFORM validate_news_hashtag_count(COALESCE(NEW.news_id, OLD.news_id));
  RETURN NULL;
END;
$fn$;

DROP FUNCTION IF EXISTS trg_validate_hashtag_count_on_news_hashtags();
CREATE OR REPLACE FUNCTION trg_validate_hashtag_count_on_news_hashtags()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
DECLARE
  v_new UUID;
  v_old UUID;
BEGIN
  v_new := NULL;
  v_old := NULL;

  IF TG_OP = 'INSERT' THEN
    v_new := NEW.news_id;
  ELSIF TG_OP = 'DELETE' THEN
    v_old := OLD.news_id;
  ELSE
    -- UPDATE
    v_new := NEW.news_id;
    v_old := OLD.news_id;
  END IF;

  IF v_new IS NOT NULL THEN
    PERFORM validate_news_hashtag_count(v_new);
  END IF;

  -- news_id を変更した UPDATE / DELETE の場合、旧 news_id 側も検証
  IF v_old IS NOT NULL AND v_old <> v_new THEN
    PERFORM validate_news_hashtag_count(v_old);
  END IF;

  RETURN NULL;
END;
$fn$;

DROP TRIGGER IF EXISTS ct_news_hashtag_count_news ON news;
DROP TRIGGER IF EXISTS ct_news_hashtag_count_link ON news_hashtags;

CREATE CONSTRAINT TRIGGER ct_news_hashtag_count_news
AFTER INSERT OR UPDATE ON news
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION trg_validate_hashtag_count_on_news();

CREATE CONSTRAINT TRIGGER ct_news_hashtag_count_link
AFTER INSERT OR UPDATE OR DELETE ON news_hashtags
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION trg_validate_hashtag_count_on_news_hashtags();

-- =============================
-- (3) 無効（enabled=false）ハッシュタグは紐付け不可（DB側で強制）
-- =============================
DROP FUNCTION IF EXISTS validate_no_disabled_hashtag_links();
CREATE OR REPLACE FUNCTION validate_no_disabled_hashtag_links()
RETURNS VOID
LANGUAGE plpgsql
AS $fn$
BEGIN
  -- news_hashtags に紐付いている hashtag は常に enabled=true であること
  IF EXISTS (
    SELECT 1
    FROM news_hashtags nh
    JOIN hashtags h ON h.hashtag_id = nh.hashtag_id
    WHERE h.enabled = FALSE
    LIMIT 1
  ) THEN
    RAISE EXCEPTION 'disabled hashtag cannot be linked to any news'
      USING ERRCODE = '23514';
  END IF;
END;
$fn$;

DROP FUNCTION IF EXISTS trg_validate_enabled_hashtags();
CREATE OR REPLACE FUNCTION trg_validate_enabled_hashtags()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
  PERFORM validate_no_disabled_hashtag_links();
  RETURN NULL;
END;
$fn$;

DROP TRIGGER IF EXISTS ct_no_disabled_hashtag_links_link ON news_hashtags;
DROP TRIGGER IF EXISTS ct_no_disabled_hashtag_links_hashtags ON hashtags;

CREATE CONSTRAINT TRIGGER ct_no_disabled_hashtag_links_link
AFTER INSERT OR UPDATE OR DELETE ON news_hashtags
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION trg_validate_enabled_hashtags();

CREATE CONSTRAINT TRIGGER ct_no_disabled_hashtag_links_hashtags
AFTER UPDATE OF enabled ON hashtags
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION trg_validate_enabled_hashtags();
