"""source 重み設定の読み込みを提供。

Java 側の application.yml にある設定（rec.feedback-weight.*）を、
学習側でも同じルールで読み取ります。
"""

import logging
from pathlib import Path
from typing import Dict, Optional

import yaml

from training.src.common.MessageCatalog import MessageCatalog

logger = logging.getLogger(__name__)

# メッセージカタログ（日本語）を保持
MESSAGES = MessageCatalog(locale="ja")


# --------------------------------------
# source 重みの読込クラス
# --------------------------------------
class WeightLoaderClass:

    DEFAULT = {
        "rec": 1.0,
        "category": 0.6,
        "latest": 0.4,
        "external": 0.2,
        "unknown": 0.3,
    }

    DEFAULT_TYPE_WEIGHT = {
        "favorite": 2.0,
        "good": 1.5,
        "view": 1.0,
    }

    DEFAULT_BAD_NEGATIVE_RULE = {
        "min": 0.5,
        "mult": 5.0,
    }

    # --------------------------------------
    # YAML から source 重みを読取
    # --------------------------------------
    def load_source_weights(self, yaml_path: Optional[Path]) -> Dict[str, float]:

        # YAML 全体を読み込み
        data = self._load_yaml(yaml_path)

        # YAML が取得できなかった場合
        if data is None:
            # デフォルト値を返却
            return dict(self.DEFAULT)

        try:
            # rec.feedback-weight 配下の設定を取得
            weights = (((data.get("rec") or {}).get("feedback-weight")) or {})

            # デフォルト値へ上書きする形でマージ（キーは小文字へ統一）
            merged = {
                **self.DEFAULT,
                **{str(k).lower(): float(v) for k, v in weights.items()},
            }

            # 読み込んだ重みを返却
            return merged

        except Exception as ex:

            # ログ出力（例外はスタックトレース付きで出力）
            logger.warning(MESSAGES.get("warn.yaml_parse_failed"), exc_info=ex)

            # デフォルト値を返却
            return dict(self.DEFAULT)

    # --------------------------------------
    # YAML から正例の重みを読取
    # --------------------------------------
    def load_type_weights(self, yaml_path: Optional[Path]) -> Dict[str, float]:

        # YAML 全体を読み込み
        data = self._load_yaml(yaml_path)

        # YAML が取得できなかった場合
        if data is None:
            # デフォルト値を返却
            return dict(self.DEFAULT_TYPE_WEIGHT)

        try:
            # rec.training-weight 配下の設定を取得
            weights = (((data.get("rec") or {}).get("training-weight")) or {})

            # デフォルト値を用意
            merged = dict(self.DEFAULT_TYPE_WEIGHT)

            # favorite の重みが設定されている場合
            if "favorite" in weights:
                # 設定値で上書き
                merged["favorite"] = float(weights["favorite"])

            # good の重みが設定されている場合
            if "good" in weights:
                # 設定値で上書き
                merged["good"] = float(weights["good"])

            # view の重みが設定されている場合
            if "view" in weights:
                # 設定値で上書き
                merged["view"] = float(weights["view"])

            # 読み込んだ重みを返却
            return merged

        except Exception as ex:

            # ログ出力（例外はスタックトレース付きで出力）
            logger.warning(MESSAGES.get("warn.yaml_parse_failed"), exc_info=ex)

            # デフォルト値を返却
            return dict(self.DEFAULT_TYPE_WEIGHT)

    # --------------------------------------
    # YAML から bad 負例ルールを読取
    # --------------------------------------
    def load_bad_negative_rule(self, yaml_path: Optional[Path]) -> Dict[str, float]:

        # YAML 全体を読み込み
        data = self._load_yaml(yaml_path)

        # YAML が取得できなかった場合
        if data is None:
            # デフォルト値を返却
            return dict(self.DEFAULT_BAD_NEGATIVE_RULE)

        try:
            # rec.training-weight 配下の設定を取得
            weights = (((data.get("rec") or {}).get("training-weight")) or {})

            # デフォルト値を用意
            merged = dict(self.DEFAULT_BAD_NEGATIVE_RULE)

            # bad の最小重みが設定されている場合
            if "bad-negative-min" in weights:
                # 設定値で上書き
                merged["min"] = float(weights["bad-negative-min"])

            # bad の倍率が設定されている場合
            if "bad-negative-mult" in weights:
                # 設定値で上書き
                merged["mult"] = float(weights["bad-negative-mult"])

            # 読み込んだ重みを返却
            return merged

        except Exception as ex:

            # ログ出力（例外はスタックトレース付きで出力）
            logger.warning(MESSAGES.get("warn.yaml_parse_failed"), exc_info=ex)

            # デフォルト値を返却
            return dict(self.DEFAULT_BAD_NEGATIVE_RULE)

    # --------------------------------------
    # YAML 全体を読取
    # --------------------------------------
    def _load_yaml(self, yaml_path: Optional[Path]) -> Optional[dict]:

        # YAML が取得できなかった場合
        if yaml_path is None or not yaml_path.exists():
            # None を返却
            return None

        try:
            # YAML を読み取り（空の場合は空辞書）
            return yaml.safe_load(yaml_path.read_text(encoding="utf-8")) or {}
        except Exception as ex:

            # ログ出力（例外はスタックトレース付きで出力）
            logger.warning(MESSAGES.get("warn.yaml_parse_failed"), exc_info=ex)

            # None を返却
            return None
