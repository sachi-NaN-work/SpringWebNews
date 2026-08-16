"""TrainingCore の状態（シグナル）管理。

ユーザーごとの累積カウントや、全体側シグナル、
および 1イベント進めるための更新関数を提供します。
"""

from dataclasses import dataclass, field
from typing import Dict, List, Tuple

from training.src.model.TrainingCoreConstants import (
    FEEDBACK_VIEW,
    FEEDBACK_FAVORITE,
    FEEDBACK_UNFAVORITE,
    FEEDBACK_GOOD,
    FEEDBACK_BAD,
    FEEDBACK_CLEAR,
)

@dataclass
# ユーザーごとの累積シグナル（嗜好一致度/繰り返し閲覧ペナルティ 用）
class UserSignals:

    # ユーザーの総数（累積）
    # 閲覧総数の初期値
    total_views: int = 0
    # お気に入り総数の初期値
    total_fav: int = 0
    # 「good」反応総数の初期値
    total_good: int = 0
    # 「bad」反応総数の初期値
    total_bad: int = 0

    # ユーザー×ニュースの閲覧回数（繰り返し閲覧ペナルティ用）
    # ユーザー×ニュース閲覧回数の初期値
    u_news_view: Dict[str, int] = field(default_factory=dict)

    # ユーザー×カテゴリの回数
    # ユーザー×カテゴリ閲覧回数の初期値
    u_cat_view: Dict[str, int] = field(default_factory=dict)
    # ユーザー×カテゴリお気に入り回数の初期値
    u_cat_fav: Dict[str, int] = field(default_factory=dict)
    # ユーザー×カテゴリ「good」反応回数の初期値
    u_cat_good: Dict[str, int] = field(default_factory=dict)
    # ユーザー×カテゴリ「bad」反応回数の初期値
    u_cat_bad: Dict[str, int] = field(default_factory=dict)

    # ユーザー×タグの回数
    # ユーザー×タグ閲覧回数の初期値
    u_tag_view: Dict[str, int] = field(default_factory=dict)
    # ユーザー×タグお気に入り回数の初期値
    u_tag_fav: Dict[str, int] = field(default_factory=dict)
    # ユーザー×タグ「good」反応回数の初期値
    u_tag_good: Dict[str, int] = field(default_factory=dict)
    # ユーザー×タグ「bad」反応回数の初期値
    u_tag_bad: Dict[str, int] = field(default_factory=dict)


# データクラス用デコレータ（ボイラープレート削減）
@dataclass
# 全体側シグナル（お気に入り/good/bad）
class GlobalSignals:

    # お気に入り総数の初期値
    total_fav: int = 0
    # 「good」反応総数の初期値
    total_good: int = 0
    # 「bad」反応総数の初期値
    total_bad: int = 0

    # 全体×カテゴリお気に入り回数の初期値
    g_cat_fav: Dict[str, int] = field(default_factory=dict)
    # 全体×カテゴリ「good」反応回数の初期値
    g_cat_good: Dict[str, int] = field(default_factory=dict)
    # 全体×カテゴリ「bad」反応回数の初期値
    g_cat_bad: Dict[str, int] = field(default_factory=dict)

    # 全体×タグお気に入り回数の初期値
    g_tag_fav: Dict[str, int] = field(default_factory=dict)
    # 全体×タグ「good」反応回数の初期値
    g_tag_good: Dict[str, int] = field(default_factory=dict)
    # 全体×タグ「bad」反応回数の初期値
    g_tag_bad: Dict[str, int] = field(default_factory=dict)


# --------------------------------------
# カウントを増減（キャッシュ側の加算相当）
# --------------------------------------
def _incr(m: Dict[str, int], k: str, delta: int) -> None:
    # 既存値に 増減値 を加算（下限丸めなしで厳密一致）
    m[k] = int(m.get(k, 0)) + int(delta)


# --------------------------------------
# データベースログを 1イベント進めてシグナルを更新
# --------------------------------------
def _update_signals_with_event(
    # 以降はキーワード専用引数
    *,
    # イベント種別（閲覧/お気に入り/ good / bad 等）
    feedback_type: str,
    # ユーザー ID
    user_id: str,
    # ニュース ID
    news_id: str,
    # 対象カテゴリ ID
    category_id: str,
    # 対象記事のタグ ID 一覧
    tag_ids: List[str],
    # ユーザー ID → ユーザーシグナル の状態
    user_sig_map: Dict[str, UserSignals],
    # 全体側シグナル状態
    global_sig: GlobalSignals,
    # ニュース ID → 全体閲覧（0..11飽和）
    g_news_view_map: Dict[str, int],
    # （ユーザー ID ,ニュース ID ）→ リアクション状態（ good / bad /空）
    reaction_state: Dict[Tuple[str, str], str],
# 戻り値なし
) -> None:

    # ユーザーシグナルを用意
    sig = user_sig_map.setdefault(user_id, UserSignals())

    # フィードバック種別が 「閲覧（0.5秒）」 の場合
    if feedback_type == FEEDBACK_VIEW:

        # ユーザーの総閲覧回数を増やす
        sig.total_views += 1
        # ユーザー×記事の閲覧回数を増やす（繰り返し閲覧ペナルティ 用）
        _incr(sig.u_news_view, news_id, +1)
        # ユーザー×カテゴリの閲覧回数を増やす
        _incr(sig.u_cat_view, category_id, +1)
        # タグ ID 一覧を全ループ処理
        for tid in tag_ids:
            # ユーザー×タグの閲覧回数を増やす
            _incr(sig.u_tag_view, str(tid), +1)

        # ニュースごとの全体閲覧（0..11飽和）
        g_news_view_map[news_id] = min(11, int(g_news_view_map.get(news_id, 0)) + 1)
        # 戻り値を返す
        return

    # フィードバック種別 が 「お気に入り」, 「お気に入り解除」 のいずれかの場合
    if feedback_type in (FEEDBACK_FAVORITE, FEEDBACK_UNFAVORITE):
        # 増減量を設定
        delta = +1 if feedback_type == FEEDBACK_FAVORITE else -1

        # ユーザーの総お気に入り数を増減（切替ログをそのまま反映）
        sig.total_fav += delta
        # ユーザー×カテゴリのお気に入り数を増減
        _incr(sig.u_cat_fav, category_id, delta)
        # タグ ID 一覧を全ループ処理
        for tid in tag_ids:
            # ユーザー×タグのお気に入り数を増減
            _incr(sig.u_tag_fav, str(tid), delta)

        # 全体の総お気に入り数を増減
        global_sig.total_fav += delta
        # 全体×カテゴリのお気に入り数を増減
        _incr(global_sig.g_cat_fav, category_id, delta)
        # タグ ID 一覧を全ループ処理
        for tid in tag_ids:
            # 全体×タグのお気に入り数を増減
            _incr(global_sig.g_tag_fav, str(tid), delta)
        # 戻り値を返す
        return

    # フィードバック種別 が 「good」, 「bad」, 「リアクション解除」 のいずれかの場合
    if feedback_type in (FEEDBACK_GOOD, FEEDBACK_BAD, FEEDBACK_CLEAR):

        # 参照キーを用意
        key = (user_id, news_id)
        # リアクション状態の取得
        prev = reaction_state.get(key, "")
        # 次の状態を用意
        next_state = "" if feedback_type == FEEDBACK_CLEAR else feedback_type

        # 条件を満たす場合
        if prev == next_state:
            # 戻り値を返す
            return

        # 「good」の増減量を設定
        good_delta = 0
        # 「bad」の増減量を設定
        bad_delta = 0

        # 直前状態 が 「good」 の場合
        if prev == FEEDBACK_GOOD:
            # good を取り消す
            good_delta -= 1
        # 直前状態 が 「bad」 の場合
        if prev == FEEDBACK_BAD:
            # bad を取り消す
            bad_delta -= 1

        # 次状態 が 「good」 の場合
        if next_state == FEEDBACK_GOOD:
            # good を付与する
            good_delta += 1
        # 次状態 が 「bad」 の場合
        if next_state == FEEDBACK_BAD:
            # bad を付与する
            bad_delta += 1

        # 「good」増減値が 0 ではない場合
        if good_delta != 0:
            # ユーザーの総「good」数を差分更新
            sig.total_good += good_delta
            # ユーザー×カテゴリの「good」数を差分更新
            _incr(sig.u_cat_good, category_id, good_delta)
            # タグ ID 一覧を全ループ処理
            for tid in tag_ids:
                # ユーザー×タグの「good」数を差分更新
                _incr(sig.u_tag_good, str(tid), good_delta)

            # 全体の総「good」数を差分更新
            global_sig.total_good += good_delta
            # 全体×カテゴリの「good」数を差分更新
            _incr(global_sig.g_cat_good, category_id, good_delta)
            # タグ ID 一覧を全ループ処理
            for tid in tag_ids:
                # 全体×タグの「good」数を差分更新
                _incr(global_sig.g_tag_good, str(tid), good_delta)

        # 「bad」増減値が 0 ではない場合
        if bad_delta != 0:
            # ユーザーの総「bad」数を差分更新
            sig.total_bad += bad_delta
            # ユーザー×カテゴリの「bad」数を差分更新
            _incr(sig.u_cat_bad, category_id, bad_delta)
            # タグ ID 一覧を全ループ処理
            for tid in tag_ids:
                # ユーザー×タグの「bad」数を差分更新
                _incr(sig.u_tag_bad, str(tid), bad_delta)

            # 全体の総「bad」数を差分更新
            global_sig.total_bad += bad_delta
            # 全体×カテゴリの「bad」数を差分更新
            _incr(global_sig.g_cat_bad, category_id, bad_delta)
            # タグ ID 一覧を全ループ処理
            for tid in tag_ids:
                # 全体×タグの「bad」数を差分更新
                _incr(global_sig.g_tag_bad, str(tid), bad_delta)

        # （ユーザー ID,ニュース ID）のリアクション状態（ good / bad /空）を保持
        reaction_state[key] = next_state
        # 戻り値を返す
        return

    # 戻り値を返す
    return
