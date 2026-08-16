"""リアルタイム推薦の係数設定の読み込みを提供。

Java 側の application.yml にある設定（rec.rt-rec.*）を、
学習側でも同じルールで読み取ります。

読み取る対象:
  - exposure-bucket0..3
  - repeat-stage0..3
  - repeat-share-adj-coeff / repeat-share-adj-cap
"""

import logging
import os
from pathlib import Path
from typing import Dict, Optional

import yaml

from training.src.common.MessageCatalog import MessageCatalog
from training.src.model.TrainingCoreConstants import (
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

logger = logging.getLogger(__name__)

# メッセージカタログ（日本語）を保持
MESSAGES = MessageCatalog(locale="ja")


# --------------------------------------
# rec.rt-rec.* の読込クラス
# --------------------------------------
class RtRecLoaderClass:

    DEFAULT: Dict[str, float] = {
        "exposure_bucket0": float(EXPOSURE_BUCKET0),
        "exposure_bucket1": float(EXPOSURE_BUCKET1),
        "exposure_bucket2": float(EXPOSURE_BUCKET2),
        "exposure_bucket3": float(EXPOSURE_BUCKET3),
        "repeat_stage0": float(REPEAT_STAGE0),
        "repeat_stage1": float(REPEAT_STAGE1),
        "repeat_stage2": float(REPEAT_STAGE2),
        "repeat_stage3": float(REPEAT_STAGE3),
        "repeat_share_adj_coeff": float(REPEAT_SHARE_ADJ_COEFF),
        "repeat_share_adj_cap": float(REPEAT_SHARE_ADJ_CAP),
    }

    # --------------------------------------
    # YAML から rt-rec 係数を読取
    # --------------------------------------
    def load_rt_rec(self, yaml_path: Optional[Path]) -> Dict[str, float]:

        # YAML 未指定の場合は既定パスを探索
        if yaml_path is None:
            yaml_path = self._find_default_yaml()

        # YAML が取得できなかった場合
        if yaml_path is None or not yaml_path.exists():
            # デフォルト値を返却
            return dict(self.DEFAULT)

        try:
            # YAML を読み取り（空の場合は空辞書）
            data = yaml.safe_load(yaml_path.read_text(encoding="utf-8")) or {}

            # rec.rt-rec 配下の設定を取得
            rt = (((data.get("rec") or {}).get("rt-rec")) or {})

            # デフォルト値へ上書きする形でマージ
            merged = dict(self.DEFAULT)

            # exposure バケット
            merged["exposure_bucket0"] = float(rt.get("exposure-bucket0", merged["exposure_bucket0"]))
            merged["exposure_bucket1"] = float(rt.get("exposure-bucket1", merged["exposure_bucket1"]))
            merged["exposure_bucket2"] = float(rt.get("exposure-bucket2", merged["exposure_bucket2"]))
            merged["exposure_bucket3"] = float(rt.get("exposure-bucket3", merged["exposure_bucket3"]))

            # repeat 段階
            merged["repeat_stage0"] = float(rt.get("repeat-stage0", merged["repeat_stage0"]))
            merged["repeat_stage1"] = float(rt.get("repeat-stage1", merged["repeat_stage1"]))
            merged["repeat_stage2"] = float(rt.get("repeat-stage2", merged["repeat_stage2"]))
            merged["repeat_stage3"] = float(rt.get("repeat-stage3", merged["repeat_stage3"]))

            # repeat 割合補正
            merged["repeat_share_adj_coeff"] = float(
                rt.get("repeat-share-adj-coeff", merged["repeat_share_adj_coeff"])
            )
            merged["repeat_share_adj_cap"] = float(rt.get("repeat-share-adj-cap", merged["repeat_share_adj_cap"]))

            # 読み込んだ係数を返却
            return merged

        except Exception as ex:

            # ログ出力（例外はスタックトレース付きで出力）
            logger.warning(MESSAGES.get("warn.yaml_parse_failed"), exc_info=ex)

            # デフォルト値を返却
            return dict(self.DEFAULT)

    # --------------------------------------
    # 既定の application.yml を探索
    # --------------------------------------
    def _find_default_yaml(self) -> Optional[Path]:

        # 有効プロファイル（先頭のみ）を取得
        active_profile = os.getenv("SPRING_PROFILES_ACTIVE", "").split(",")[0].strip()
        # profile 用設定ファイルを優先
        if active_profile:
            profile_yaml = Path(f"config/application-{active_profile}.yml")
            if profile_yaml.exists():
                return profile_yaml

        # 共通設定の application.yml を探索
        p = Path("config/application.yml")
        if p.exists():
            return p

        # 救済用に環境別の設定ファイルも探索
        prod = Path("config/application-prod.yml")
        if prod.exists():
            return prod

        dev = Path("config/application-dev.yml")
        if dev.exists():
            return dev

        # 実行場所が異なる場合に備えて親ディレクトリを探索
        here = Path(__file__).resolve()
        for parent in here.parents:
            # 親ディレクトリでも profile 用設定を優先
            if active_profile:
                profile_guess = parent / "config" / f"application-{active_profile}.yml"
                if profile_guess.exists():
                    return profile_guess

            # 親ディレクトリでも共通設定を探索
            guess = parent / "config" / "application.yml"
            if guess.exists():
                return guess

        # 該当なし
        return None
