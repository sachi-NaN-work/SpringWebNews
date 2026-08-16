"""
TrainingCore の学習用データセット構築。

リーク対策（イベント反映前スナップショットで特徴量計算 / 同時刻バッチ処理）を含め、
学習用の (X, y, sample_weight) を生成します。
"""

import logging
import random
from collections import defaultdict
from dataclasses import dataclass
from datetime import timezone
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd

from training.src.common.MessageCatalog import MessageCatalog
from training.src.model.TrainingCoreConstants import (
    REFERENCE_DATE,
    WARN_NO_FEEDBACK_MARKER,
    FEEDBACK_UNKNOWN,
    FEEDBACK_CLICK,
    FEEDBACK_VIEW,
    FEEDBACK_FAVORITE,
    FEEDBACK_GOOD,
    FEEDBACK_BAD,
    FEATURE_ORDER,
)
from training.src.model.TrainingCoreFeatures import (
    exposure_score,
    feature_vector,
    repeat_penalty,
)
from training.src.model.TrainingCoreSignals import (
    GlobalSignals,
    UserSignals,
    _update_signals_with_event,
)


# ログ出力用のロガーを用意
logger = logging.getLogger(__name__)
# 日本語メッセージ辞書を用意
MESSAGES = MessageCatalog(locale="ja")


# --------------------------------------
# 学習用データセット
# --------------------------------------
# データクラス用デコレータ（オプション指定）
@dataclass(frozen=True)
# 学習入力（特徴量・ラベル・重み）をまとめる データ構造
class TrainingDataset:
    # 特徴量行列（件数×9；値32）
    X: np.ndarray
    # ラベル配列（件数；1=正例/0=負例）
    y: np.ndarray
    # サンプル重み（件数；種別×元×繰り返し閲覧ペナルティ 等）
    sample_weight: np.ndarray


# --------------------------------------
# 作成日時を世界標準時のタイムスタンプに正規化
# --------------------------------------
def _to_utc_timestamp(created_time: object) -> pd.Timestamp:
    # 日時を用意
    created_datetime = pd.to_datetime(created_time, errors="coerce")
    # 日時が取得できた場合
    if pd.isna(created_datetime):
        # UNIX時間の起点時刻（世界標準時）を返却
        return pd.Timestamp(REFERENCE_DATE, tz=timezone.utc)

    # Timestamp 型の場合
    if isinstance(created_datetime, pd.Timestamp):

        # タイムゾーンが無い場合
        if created_datetime.tzinfo is None:
            # 開始時刻（世界標準時）を返却
            return created_datetime.tz_localize(timezone.utc)

        # 開始時刻（世界標準時）を返却
        return created_datetime.tz_convert(timezone.utc)

    # 開始時刻（世界標準時）を返却
    return pd.Timestamp(created_datetime, tz=timezone.utc)


# --------------------------------------
# データベース の フィードバック種別 から (種別, 元) を抽出
# --------------------------------------
def parse_feedback_type(feedback_type: str) -> Tuple[str, str]:
    # フィードバック種別が存在しない場合
    if not feedback_type:
        # フィードバック種別（不明）を返却
        return FEEDBACK_CLICK, FEEDBACK_UNKNOWN

    # 合計数を設定
    sum = str(feedback_type).strip().lower()
    # 条件を満たす場合
    if ":" not in sum:
        # フィードバック種別（不明）を返却
        return sum, FEEDBACK_UNKNOWN

    # 「種別:元」 を最初の ':' で分割して抽出
    t, src = sum.split(":", 1)
    # フィードバック種別を返却
    return (t or FEEDBACK_CLICK), (src or FEEDBACK_UNKNOWN)


# --------------------------------------
# ニュース ID → [タグ ID] のマップを構築
# 嗜好一致度 のタグ特徴量で参照する
# --------------------------------------
def _build_news_tags_map(news_hashtags_df: Optional[pd.DataFrame]) -> Dict[str, List[str]]:
    # 条件を満たす場合
    if news_hashtags_df is None or news_hashtags_df.empty:
        # 戻り値を返す
        return {}

    # 辞書の初期値
    news_tags_map: Dict[str, List[str]] = defaultdict(list)
    # 表データの各行を全ループ処理
    for _, row in news_hashtags_df.iterrows():
        # ニュース ID を用意
        nid = str(row.get("news_id"))
        # タグ ID を用意
        tid = str(row.get("hashtag_id"))

        # ニュース ID とタグ ID が一致する場合
        if nid and tid:
            # リストへ追加
            news_tags_map[nid].append(tid)

    # 戻り値を返す
    return dict(news_tags_map)


# --------------------------------------
# フィードバックが無い場合のフォールバック
# 係数が 非数値 になるのを避け、最低限の学習を成立させる
# --------------------------------------
def _append_fallback_samples(
    news_ids: List[str],
    X: List[List[float]],
    y: List[float],
    sw: List[float],
    rt_rec: Optional[Dict[str, float]] = None,
) -> None:

    # ログ出力
    logger.warning(
        MESSAGES.get("warn.no_feedback_with_marker"),
        WARN_NO_FEEDBACK_MARKER,
        MESSAGES.get("warn.no_feedback"),
    )

    # 対象を全ループ処理
    for nid in news_ids[:20]:
        # 一時変数を用意
        _ = nid
        # 露出スコア（全体閲覧段階）を計算
        exp = exposure_score(0, rt_rec)
        # 9特徴量のダミー（露出のみ、残りは 0）
        X.append([float(exp)] + [0.0] * (len(FEATURE_ORDER) - 1))
        # リストへ追加
        y.append(0.0)
        # リストへ追加
        sw.append(1.0)


# --------------------------------------
# 擬似負例のニュース ID を抽出
# --------------------------------------
def _sample_pseudo_negative_ids(
        *,
        positive_news_id: str,
        positive_category_id: str,
        news_by_id: Dict[str, pd.Series],
        news_ids: List[str],
        sig: UserSignals,
        count: int,
) -> List[str]:

    # 擬似負例数が 0 以下の場合
    if count <= 0:
        # 空のリストを返却
        return []

    # 除外ニュース ID を用意
    excluded = {str(positive_news_id)}
    # 既に閲覧済みのニュース ID を除外
    excluded.update(str(nid) for nid in sig.u_news_view.keys())

    # 同一カテゴリ候補を用意
    same_category_ids: List[str] = []
    # その他カテゴリ候補を用意
    other_category_ids: List[str] = []

    # ニュース ID 一覧を全ループ処理
    for nid in news_ids:
        # ニュース ID を文字列化
        nid = str(nid)
        # 除外対象の場合
        if nid in excluded:
            # ループ制御
            continue

        # ニュース行を取得
        row = news_by_id.get(nid)
        # ニュース行が取得できなかった場合
        if row is None:
            # ループ制御
            continue

        # カテゴリ ID を取得
        cat_id = str(row.get("category_id", ""))
        # 正例と同一カテゴリの場合
        if cat_id == str(positive_category_id):
            # 同一カテゴリ候補へ追加
            same_category_ids.append(nid)
        # それ以外の場合
        else:
            # その他カテゴリ候補へ追加
            other_category_ids.append(nid)

    # 同一カテゴリ候補をシャッフル
    random.shuffle(same_category_ids)
    # その他カテゴリ候補をシャッフル
    random.shuffle(other_category_ids)

    # 同一カテゴリを優先して候補を結合
    candidate_ids = same_category_ids + other_category_ids

    # 指定件数へ制限して返却
    return candidate_ids[:count]


# --------------------------------------
# 擬似負例サンプルを追加
# --------------------------------------
def _append_pseudo_negative_samples(
        *,
        X: List[List[float]],
        y: List[float],
        sw: List[float],
        positive_news_id: str,
        positive_category_id: str,
        news_by_id: Dict[str, pd.Series],
        news_ids: List[str],
        news_tags_map: Dict[str, List[str]],
        sig: UserSignals,
        g_news_view_map: Dict[str, int],
        neg_per_pos: int,
        negative_weight: float,
        source_weight: float,
        rt_rec: Optional[Dict[str, float]],
) -> None:

    # 擬似負例ニュース ID 一覧を取得
    negative_ids = _sample_pseudo_negative_ids(
        positive_news_id=positive_news_id,
        positive_category_id=positive_category_id,
        news_by_id=news_by_id,
        news_ids=news_ids,
        sig=sig,
        count=int(neg_per_pos),
    )

    # 擬似負例ニュース ID 一覧を全ループ処理
    for negative_news_id in negative_ids:
        # ニュース行を取得
        row = news_by_id.get(negative_news_id)
        # ニュース行が取得できなかった場合
        if row is None:
            # ループ制御
            continue

        # カテゴリ ID を設定
        category_id = str(row.get("category_id", ""))
        # タグ ID 一覧を設定
        tag_ids = list(news_tags_map.get(negative_news_id, []))

        # ニュースごとの全体 閲覧（0..11飽和）を保持
        exp = exposure_score(g_news_view_map.get(negative_news_id, 0), rt_rec)
        # 9特徴量を生成
        fv = feature_vector(exp, category_id, tag_ids, sig)
        # 繰り返し閲覧ペナルティ（繰り返し閲覧抑制）を計算
        rp = repeat_penalty(sig.u_news_view.get(negative_news_id, 0), sig.total_views, rt_rec)

        # リストへ追加
        X.append([float(v) for v in fv])
        # リストへ追加
        y.append(0.0)
        # 繰り返し閲覧ペナルティを重みに掛けてオンライン後処理を学習に反映
        sw.append(float(max(0.0, negative_weight)) * float(max(0.0, source_weight)) * float(rp))


# --------------------------------------
# 学習用データセットを構築
#
# - フィードバック を (作成日時 昇順, フィードバック識別子 昇順) で再生
# - 各サンプルの特徴量は「イベント反映前」のスナップショットで計算（リーク対策）
# - 作成日時 が同一の行は“同時刻バッチ”として扱い、バッチ開始時点のスナップショットで一括サンプル化
# - 正例: お気に入り/良い/閲覧, 負例: 悪い + 擬似負例（擬似負例（ランダム））
# --------------------------------------
def build_training_dataset(
        *,
        news_df: pd.DataFrame,
        feedback_df: pd.DataFrame,
        source_weights: Dict[str, float],
        type_weights: Dict[str, float],
        neg_per_pos: int,
        negative_weight: float,
        bad_negative_weight_min: float,
        bad_negative_weight_mult: float,
        seed: int,
        news_hashtags_df: Optional[pd.DataFrame] = None,
        rt_rec: Optional[Dict[str, float]] = None,
) -> TrainingDataset:

    # 乱数 乱数種 を固定（擬似負例サンプリングを再現可能に）
    random.seed(int(seed))

    # 条件を満たす場合
    if news_df is None or news_df.empty:
        # 異常系は例外で停止
        raise RuntimeError(MESSAGES.get("error.news_empty"))

    # 破壊的変更を避けるため ニュース表 をコピー
    news_df = news_df.copy()
    # ニュース ID → 行（カテゴリ/公開日時など）を引ける辞書を構築
    # ニュース情報の参照表
    news_by_id = {str(r["id"]): r for _, r in news_df.iterrows()}
    # ニュース ID 一覧
    news_ids = list(news_by_id.keys())

    # ニュース→タグ ID 一覧の対応表
    news_tags_map = _build_news_tags_map(news_hashtags_df)

    # 特徴量 の初期値
    X: List[List[float]] = []
    # ラベル の初期値
    y: List[float] = []
    # サンプル重み の初期値
    sw: List[float] = []

    # 条件を満たす場合
    if feedback_df is None or feedback_df.empty:
        # フォールバック用のダミーサンプルを追加（学習を成立させる）
        _append_fallback_samples(news_ids, X, y, sw, rt_rec)
        # 作成したデータセットを返却
        return TrainingDataset(
            # 特徴量配列を用意
            X=np.asarray(X, dtype=np.float32),
            # ラベル配列を用意
            y=np.asarray(y, dtype=np.float32),
            # サンプル重み配列を用意
            sample_weight=np.asarray(sw, dtype=np.float32),
        )

    # 破壊的変更を避けるため フィードバック表 をコピー
    fb = feedback_df.copy()

    # 作成日時 を 世界標準時 に正規化
    fb["_ts_utc"] = fb["created_at"].apply(_to_utc_timestamp) if "created_at" in fb.columns else pd.Timestamp("1970-01-01", tz=timezone.utc)

    # フィードバック表の列が feedback_id の場合
    if "feedback_id" in fb.columns:
        # 同時刻の順序安定化のため フィードバック識別子(主キー) を数値化して使う
        fb["_pk"] = pd.to_numeric(fb["feedback_id"], errors="coerce").fillna(-1).astype("int64")
    # それ以外の場合
    else:
        # フィードバック識別子 が無い場合は行番号で順序を安定化
        fb["_pk"] = np.arange(len(fb), dtype=np.int64)

    # 安定ソート（同キー時の順序を維持）で決定性を確保
    fb = fb.sort_values(["_ts_utc", "_pk"], kind="mergesort")

    # ユーザーごとの累積シグナル状態
    user_sig_map: Dict[str, UserSignals] = {}
    # 全体側シグナル状態（お気に入り/良い/悪い）を保持
    global_sig = GlobalSignals()
    # ニュースごとの全体閲覧（0..11飽和）を保持
    g_news_view_map: Dict[str, int] = {}
    # （ユーザー ID,ニュース ID） のリアクション状態（良い/悪い/空）を保持
    reaction_state: Dict[Tuple[str, str], str] = {}

    # 「bad」の明示負例の重み
    bad_w = max(float(bad_negative_weight_min), float(negative_weight) * float(bad_negative_weight_mult))

    # 同一作成日時(世界標準時) を1バッチとして処理（同時刻内リーク防止）
    for ts, batch in fb.groupby("_ts_utc", sort=False):
        # 一時変数を用意
        _ = ts
        # 安定ソート（同キー時の順序を維持）で決定性を確保
        batch = batch.sort_values(["_pk"], kind="mergesort")

        # 1) サンプル生成（更新前スナップショット）
        # 表データの各行を全ループ処理
        for _, row in batch.iterrows():
            # ユーザー ID を設定
            _uid = row.get("user_id")
            user_id = "" if _uid is None else str(_uid)
            # ニュース ID を設定
            news_id = str(row.get("news_id"))
            # 条件を満たす場合
            if news_id not in news_by_id:
                # ループ制御
                continue

            # データベースのフィードバック種別を分解
            t, src = parse_feedback_type(row.get("feedback_type"))

            # カテゴリ ID を設定
            cat_id = str(news_by_id[news_id].get("category_id", row.get("category_id")))
            # タグ ID 一覧を設定
            tag_ids = list(news_tags_map.get(news_id, []))

            # イベント反映前のユーザー状態（初回ユーザーはゼロ初期値）を参照
            sig = user_sig_map.get(user_id, UserSignals())

            # ニュースごとの全体 閲覧（0..11飽和）を保持
            exp = exposure_score(g_news_view_map.get(news_id, 0), rt_rec)
            # 9特徴量を生成
            fv = feature_vector(exp, cat_id, tag_ids, sig)
            # 繰り返し閲覧ペナルティ（繰り返し閲覧抑制）を計算
            rp = repeat_penalty(sig.u_news_view.get(news_id, 0), sig.total_views, rt_rec)

            # 明示正例
            # フィードバック種別 が「お気に入り」, 「good」 のいずれかの場合
            if t in (FEEDBACK_FAVORITE, FEEDBACK_GOOD):

                # 種別（お気に入り/ good）に応じた重みを取得
                type_w = float(type_weights.get(t, 0.1))
                # 元（推薦/最新 等）に応じた重みを取得（未知は 不明）
                src_w = float(source_weights.get(src, source_weights.get(FEEDBACK_UNKNOWN, 0.3)))
                # 正例重み（種別×元）を計算
                pos_w = type_w * src_w

                # リストへ追加
                X.append([float(v) for v in fv])
                # リストへ追加
                y.append(1.0)
                # 繰り返し閲覧ペナルティを重みに掛けてオンライン後処理を学習に反映
                sw.append(float(pos_w) * float(rp))
                # 擬似負例サンプルを追加
                _append_pseudo_negative_samples(
                    X=X,
                    y=y,
                    sw=sw,
                    positive_news_id=news_id,
                    positive_category_id=cat_id,
                    news_by_id=news_by_id,
                    news_ids=news_ids,
                    news_tags_map=news_tags_map,
                    sig=sig,
                    g_news_view_map=g_news_view_map,
                    neg_per_pos=neg_per_pos,
                    negative_weight=negative_weight,
                    source_weight=src_w,
                    rt_rec=rt_rec,
                )

            # 擬似正例（view）
            # フィードバック種別 が「閲覧」の場合
            elif t == FEEDBACK_VIEW:

                # 種別（閲覧）に応じた重みを取得
                type_w = float(type_weights.get(t, 0.1))
                # 元（推薦/最新 等）に応じた重みを取得（未知は 不明）
                src_w = float(source_weights.get(src, source_weights.get(FEEDBACK_UNKNOWN, 0.3)))
                # 正例重み（種別×元）を計算
                pos_w = type_w * src_w

                # リストへ追加
                X.append([float(v) for v in fv])
                # リストへ追加
                y.append(1.0)
                # 繰り返し閲覧ペナルティを重みに掛けてオンライン後処理を学習に反映
                sw.append(float(pos_w) * float(rp))
                # 擬似負例サンプルを追加
                _append_pseudo_negative_samples(
                    X=X,
                    y=y,
                    sw=sw,
                    positive_news_id=news_id,
                    positive_category_id=cat_id,
                    news_by_id=news_by_id,
                    news_ids=news_ids,
                    news_tags_map=news_tags_map,
                    sig=sig,
                    g_news_view_map=g_news_view_map,
                    neg_per_pos=neg_per_pos,
                    negative_weight=negative_weight,
                    source_weight=src_w,
                    rt_rec=rt_rec,
                )

            # 明示負例（bad）
            # フィードバック種別 が「bad」の場合
            elif t == FEEDBACK_BAD:
                # リストへ追加
                X.append([float(v) for v in fv])
                # リストへ追加
                y.append(0.0)
                # 繰り返し閲覧ペナルティ を重みに掛けてオンライン後処理を学習に反映
                sw.append(float(bad_w) * float(rp))

        # 2) バッチのイベントで状態を進める（次の作成日時から効く）
        # 表データの各行を全ループ処理
        for _, row in batch.iterrows():
            # ユーザー ID を設定
            _uid = row.get("user_id")
            user_id = "" if _uid is None else str(_uid)
            # ニュース ID を設定
            news_id = str(row.get("news_id"))
            # 条件を満たす場合
            if news_id not in news_by_id:
                # ループ制御
                continue

            # フィードバック種別 を (種別, 元) に分解
            t, _src = parse_feedback_type(row.get("feedback_type"))
            # カテゴリ ID を設定
            cat_id = str(news_by_id[news_id].get("category_id", row.get("category_id")))
            # タグ ID 一覧を設定
            tag_ids = list(news_tags_map.get(news_id, []))

            # バッチ内イベントでシグナル状態を更新（次バッチから効く）
            _update_signals_with_event(
                feedback_type=t,
                user_id=user_id,
                news_id=news_id,
                category_id=cat_id,
                tag_ids=tag_ids,
                user_sig_map=user_sig_map,
                global_sig=global_sig,
                g_news_view_map=g_news_view_map,
                reaction_state=reaction_state,
            )

    # 特徴量 が存在しない場合
    if not X:
        # フォールバック用のダミーサンプルを追加（学習を成立させる）
        _append_fallback_samples(news_ids, X, y, sw, rt_rec)

    # 作成したデータセットを返却
    return TrainingDataset(
        # 特徴量配列を用意
        X=np.asarray(X, dtype=np.float32),
        # ラベル配列を用意
        y=np.asarray(y, dtype=np.float32),
        # サンプル重み配列を用意
        sample_weight=np.asarray(sw, dtype=np.float32),
    )