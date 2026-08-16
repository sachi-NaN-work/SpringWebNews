-- 学習用: feedback ログを時系列再生して「サンプル時点以前だけ」で特徴量を計算する
-- created_at が同一の行があっても順序が揺れないよう、PK(feedback_id) を含めて安定化する
SELECT feedback_id, user_id, news_id, category_id, feedback_type, created_at
FROM feedback
ORDER BY created_at ASC, feedback_id ASC
