"""TrainingCore の特徴量計算。

推論側（Spring/WebFlux）のスコアリング実装と特徴量定義を一致させる。

注意:
- A/B ブーストは学習対象にしない（特徴量に含めない）。
"""

from typing import Dict, List, Optional

from training.src.model.TrainingCoreConstants import (
    PER_PREF_CAT,
    PER_TAG,
    REPEAT_VIEW0,
    REPEAT_VIEW1,
    REPEAT_VIEW2,
    REPEAT_VIEW3,
    EXPOSURE_BUCKET0,
    EXPOSURE_BUCKET1,
    EXPOSURE_BUCKET2,
    EXPOSURE_BUCKET3,
    REPEAT_STAGE0,
    REPEAT_STAGE1,
    REPEAT_STAGE2,
    REPEAT_STAGE3,
    REPEAT_SHARE_ADJ_CAP,
    REPEAT_SHARE_ADJ_COEFF,
)
from training.src.model.TrainingCoreSignals import UserSignals


# カテゴリ割合の補正母数
CATEGORY_SHARE_PRIOR_TOTAL = 20.0


# --------------------------------------
# 露出スコア（全体×記事閲覧段階）を算出
# --------------------------------------
def exposure_score(
        g_news_view_capped11: int,
        rt_rec: Optional[Dict[str, float]] = None
) -> float:

    # 設定が無い場合はデフォルト係数を使う
    rt = {
        "exposure_bucket0": float(EXPOSURE_BUCKET0),
        "exposure_bucket1": float(EXPOSURE_BUCKET1),
        "exposure_bucket2": float(EXPOSURE_BUCKET2),
        "exposure_bucket3": float(EXPOSURE_BUCKET3),
    }
    if rt_rec:
        rt.update({k: float(v) for k, v in rt_rec.items() if k in rt})

    # 閲覧回数（0..11に丸めた値）を用意
    v = int(min(11, max(0, int(g_news_view_capped11))))
    # 閲覧回数 が 0 の場合
    if v == REPEAT_VIEW0:
        # 値を返す
        return float(rt["exposure_bucket0"])
    # 閲覧回数 が 5 以下の場合
    if v <= REPEAT_VIEW2:
        # 値を返す
        return float(rt["exposure_bucket1"])
    # 閲覧回数 が 10 以下の場合
    if v <= REPEAT_VIEW3:
        # 値を返す
        return float(rt["exposure_bucket2"])
    # 値を返す
    return float(rt["exposure_bucket3"])


# --------------------------------------
# 割合（部分/合計） を算出
# --------------------------------------
def share(part: int, total: int) -> float:
    # 値を返す
    return float(part) / float(max(int(total), 1))


# --------------------------------------
# カテゴリ割合（少数行動補正あり） を算出
# --------------------------------------
def category_share(part: int, total: int) -> float:
    # 件数を下限 0 で補正
    adjusted_part = max(int(part), 0)
    # 合計値を下限 0 で補正
    adjusted_total = max(int(total), 0)
    # 補正後の分母を用意
    denominator = float(adjusted_total) + float(CATEGORY_SHARE_PRIOR_TOTAL)
    # 値を返す
    return min(1.0, float(adjusted_part) / max(denominator, 1.0))


# --------------------------------------
# 0..1 に丸める（0..1に丸め）
# --------------------------------------
def clamp01(repeat_view: float) -> float:

    # 閲覧回数 が 0.0 未満の場合
    if repeat_view < REPEAT_VIEW0:
        # 戻り値を返す
        return REPEAT_VIEW0
    # 閲覧回数 が 1.0 より大きい場合
    if repeat_view > REPEAT_VIEW1:
        # 戻り値を返す
        return REPEAT_VIEW1
    # 値を返す
    return float(repeat_view)


# --------------------------------------
# 嗜好スコアを 割合 から合成（割合から嗜好度を合成）
# --------------------------------------
def pref_from_shares(
        view_share: float,
        fav_share: float,
        good_share: float,
        bad_share: float
) -> float:
    # 戻り値を返す
    return clamp01(
        (0.20 * view_share) + (0.30 * fav_share) + (0.50 * good_share) - (0.60 * bad_share)
    )


# --------------------------------------
# 繰り返し閲覧ペナルティ（繰り返し表示抑制係数）を算出
# --------------------------------------
def repeat_penalty(
        raw_repeat: int,
        total_views: int,
        rt_rec: Optional[Dict[str, float]] = None
) -> float:

    # 設定が無い場合はデフォルト係数を使う
    rt = {
        "repeat_stage0": float(REPEAT_STAGE0),
        "repeat_stage1": float(REPEAT_STAGE1),
        "repeat_stage2": float(REPEAT_STAGE2),
        "repeat_stage3": float(REPEAT_STAGE3),
        "repeat_share_adj_coeff": float(REPEAT_SHARE_ADJ_COEFF),
        "repeat_share_adj_cap": float(REPEAT_SHARE_ADJ_CAP),
    }
    if rt_rec:
        rt.update({k: float(v) for k, v in rt_rec.items() if k in rt})

    # 閲覧回数を用意
    rv = max(0, int(raw_repeat))
    # 閲覧回数 が 0 の場合
    if rv == REPEAT_VIEW0:
        # 段階係数を設定
        stage = rt["repeat_stage0"]
    # 閲覧回数 が 5 以下の場合
    elif rv <= REPEAT_VIEW2:
        # 段階係数を設定
        stage = rt["repeat_stage1"]
    # 閲覧回数 が 10 以下の場合
    elif rv <= REPEAT_VIEW3:
        # 段階係数を設定
        stage = rt["repeat_stage2"]
    # それ以外の場合
    else:
        # 段階係数を設定
        stage = rt["repeat_stage3"]

    # 繰り返し閲覧の割合を用意
    share_repeat = float(rv) / float(max(int(total_views), 1))
    # 割合調整量を用意
    adj = 1.0 - (rt["repeat_share_adj_coeff"] * min(share_repeat, rt["repeat_share_adj_cap"]))
    # 割合調整量を用意
    adj = clamp01(adj)
    # 値を返す
    return float(stage * adj)


# --------------------------------------
# 嗜好一致度（カテゴリ + タグの嗜好一致度）を算出
# --------------------------------------
def user_match(
        category_id: str,
        tag_ids: List[str],
        sig: UserSignals
) -> float:

    # カテゴリ嗜好度を用意
    pref_cat = pref_from_shares(
        # カテゴリ閲覧 割合（少数行動補正あり）
        category_share(sig.u_cat_view.get(category_id, 0), sig.total_views),
        # カテゴリお気に入り 割合（少数行動補正あり）
        category_share(sig.u_cat_fav.get(category_id, 0), sig.total_fav),
        # カテゴリ「good」割合（少数行動補正あり）
        category_share(sig.u_cat_good.get(category_id, 0), sig.total_good),
        # カテゴリ「bad」割合（少数行動補正あり）
        category_share(sig.u_cat_bad.get(category_id, 0), sig.total_bad),
    )

    # タグ嗜好度（最大値）を用意
    pref_tag_max = 0.0
    # タグ ID 一覧 が存在する場合
    if tag_ids:
        # タグ ID 一覧を全ループ処理
        for tid in tag_ids:
            # タグ ID を用意
            tid = str(tid)
            # タグ ID が存在しない場合
            if not tid:
                # ループ制御
                continue
            # タグごとの嗜好スコアを算出
            tag_preference_score = pref_from_shares(
                # タグ閲覧 割合（タグ閲覧/総閲覧）
                share(sig.u_tag_view.get(tid, 0), sig.total_views),
                # タグお気に入り 割合（タグお気に入り/総お気に入り）
                share(sig.u_tag_fav.get(tid, 0), sig.total_fav),
                # タグ「good」割合（タグ「good」/総「good」）
                share(sig.u_tag_good.get(tid, 0), sig.total_good),
                # タグ「bad」割合（タグ「bad」/総「bad」）
                share(sig.u_tag_bad.get(tid, 0), sig.total_bad),
            )
            # タグ嗜好度（最大値）を更新
            pref_tag_max = max(pref_tag_max, tag_preference_score)

    # 戻り値を返す
    return clamp01((PER_PREF_CAT * pref_cat) + (PER_TAG * pref_tag_max))


# --------------------------------------
# 特徴量（露出1件 + カテゴリ割合4種 + 最も強いタグ割合4種）を生成
# --------------------------------------
def feature_vector(
        exposure: float,
        category_id: str,
        tag_ids: List[str],
        sig: UserSignals
) -> List[float]:

    # カテゴリの割合（4種・少数行動補正あり）
    cat_view = category_share(sig.u_cat_view.get(category_id, 0), sig.total_views)
    cat_fav = category_share(sig.u_cat_fav.get(category_id, 0), sig.total_fav)
    cat_good = category_share(sig.u_cat_good.get(category_id, 0), sig.total_good)
    cat_bad = category_share(sig.u_cat_bad.get(category_id, 0), sig.total_bad)

    # 最も強いタグの割合（4種）
    strongest_tag_view = 0.0
    strongest_tag_fav = 0.0
    strongest_tag_good = 0.0
    strongest_tag_bad = 0.0
    # 最も強いタグの嗜好スコアを用意
    best_tag_preference_score = -1.0

    # タグ ID リストが存在する場合
    if tag_ids:

        # タグ ID を全ループ処理
        for tid in tag_ids:
            # タグ ID を文字列化
            tid = str(tid)
            # タグ ID が取得できていない場合
            if not tid:
                # スキップ
                continue

            # タグごとの閲覧割合を算出
            tag_view = share(sig.u_tag_view.get(tid, 0), sig.total_views)
            # タグごとのお気に入り割合を算出
            tag_fav = share(sig.u_tag_fav.get(tid, 0), sig.total_fav)
            # タグごとの good 割合を算出
            tag_good = share(sig.u_tag_good.get(tid, 0), sig.total_good)
            # タグごとの bad 割合を算出
            tag_bad = share(sig.u_tag_bad.get(tid, 0), sig.total_bad)
            # タグごとの嗜好スコアを算出
            tag_preference_score = pref_from_shares(tag_view, tag_fav, tag_good, tag_bad)

            # 最も強いタグの場合
            if tag_preference_score > best_tag_preference_score:
                # 最も強いタグの嗜好スコアを更新
                best_tag_preference_score = tag_preference_score
                # 最も強いタグの閲覧割合を設定
                strongest_tag_view = tag_view
                # 最も強いタグのお気に入り割合を設定
                strongest_tag_fav = tag_fav
                # 最も強いタグの good 割合を設定
                strongest_tag_good = tag_good
                # 最も強いタグの bad 割合を設定
                strongest_tag_bad = tag_bad

    # 戻り値を返す
    return [
        float(exposure),
        float(cat_view),
        float(cat_fav),
        float(cat_good),
        float(cat_bad),
        float(strongest_tag_view),
        float(strongest_tag_fav),
        float(strongest_tag_good),
        float(strongest_tag_bad),
    ]