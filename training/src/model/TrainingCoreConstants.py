"""TrainingCore の定数群。

学習・推論で共有する係数や重みルールを集約します。
（TrainingCore.py を肥大化させないための分割ファイル）
"""

from typing import Dict
from zoneinfo import ZoneInfo


# フィードバック無し警告の目印文字列を用意
WARN_NO_FEEDBACK_MARKER = "[WARN_NO_FEEDBACK]"

# UNIX時間の起点
REFERENCE_DATE = "1970-01-01"
# 既定のローカルタイムゾーン（日本）を用意
DEFAULT_LOCAL_TZ = ZoneInfo("Asia/Tokyo")

# フィードバック種別
FEEDBACK_UNKNOWN = "unknown"
FEEDBACK_CLICK = "click"
FEEDBACK_VIEW = "view"
FEEDBACK_FAVORITE = "favorite"
FEEDBACK_UNFAVORITE = "unfavorite"
FEEDBACK_GOOD = "good"
FEEDBACK_BAD = "bad"
FEEDBACK_CLEAR = "reaction_clear"

# ------------------------------------------------------------
# 重みルール（学習データ取り込みルール）
# ------------------------------------------------------------
# 正例の重み
TYPE_WEIGHT: Dict[str, float] = {
    # お気に入りの明示正例重み（強め）
    "favorite": 2.0,
    # 「good」反応の明示正例重み
    "good": 1.5,
    # 有効閲覧（view）の擬似正例重み（基準）
    "view": 1.0,
}

# 負例の重み
# 「bad」反応の明示負例における最小重みを用意
BAD_NEG_WEIGHT_MIN = 0.5
# 「bad」反応の明示負例における重み倍率を用意
BAD_NEG_WEIGHT_MULT = 5.0

# 嗜好一致度比率
# カテゴリ比率
PER_PREF_CAT = 0.50
# タグ比率
PER_TAG = 0.50

# ------------------------------------------------------------
# 推論側 と同じ係数（デフォルト）
# ------------------------------------------------------------
# 繰り返し閲覧ペナルティの閲覧回数区切り
# 閲覧回数が0の場合
REPEAT_VIEW0 = 0.0
# 閲覧回数が1〜5の場合
REPEAT_VIEW1 = 1.0
# 閲覧回数が6〜10の場合
REPEAT_VIEW2 = 5.0
# 閲覧回数が11以上の場合
REPEAT_VIEW3 = 10.0

# 露出スコア のバケット値
# 閲覧回数が0の場合
EXPOSURE_BUCKET0 = 1.00
# 閲覧回数が1〜5の場合
EXPOSURE_BUCKET1 = 0.95
# 閲覧回数が6〜10の場合
EXPOSURE_BUCKET2 = 0.85
# 閲覧回数が11以上の場合
EXPOSURE_BUCKET3 = 0.70

# 繰り返し閲覧ペナルティ（段階係数 + 割合調整）
# 繰り返し閲覧ペナルティの係数（段階0）を用意
REPEAT_STAGE0 = 1.00
# 繰り返し閲覧ペナルティの係数（段階1）を用意
REPEAT_STAGE1 = 0.95
# 繰り返し閲覧ペナルティの係数（段階2）を用意
REPEAT_STAGE2 = 0.70
# 繰り返し閲覧ペナルティの係数（段階3）を用意
REPEAT_STAGE3 = 0.10
# 繰り返し閲覧割合の調整係数を用意
REPEAT_SHARE_ADJ_COEFF = 0.5
# 繰り返し閲覧割合の調整上限を用意
REPEAT_SHARE_ADJ_CAP = 0.2

# --------------------------------------
# 特徴量の順序（推論側と一致させる）
# --------------------------------------
FEATURE_ORDER = [
    "exposure",
    "cat_viewShare",
    "cat_favShare",
    "cat_goodShare",
    "cat_badShare",
    "strongest_tag_viewShare",
    "strongest_tag_favShare",
    "strongest_tag_goodShare",
    "strongest_tag_badShare",
]