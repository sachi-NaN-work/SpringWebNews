"""A/B テスト設定の読み込みを提供。

Java 側（RecService#resolveAb）と同じ割当ルールで
学習データを A/B に分けるため、rec.rt-rec.* から
ab-salt / ab-b-percent を読み取ります。
"""

import logging
import os
from pathlib import Path
from typing import Optional, Tuple

import yaml

from training.src.common.MessageCatalog import MessageCatalog

logger = logging.getLogger(__name__)

# メッセージカタログ（日本語）を保持
MESSAGES = MessageCatalog(locale="ja")


# --------------------------------------
# rec.rt-rec.* の A/B 設定を読むクラス
# --------------------------------------
class AbTestLoaderClass:

    # --------------------------------------
    # YAML から (salt, b_percent) を読取
    # --------------------------------------
    def load_ab_test(self, yaml_path: Optional[Path]) -> Tuple[str, int]:

        # YAML 未指定の場合は既定パスを探索
        if yaml_path is None:
            yaml_path = self._find_default_yaml()

        # YAML が取得できなかった場合は Java 側の既定値に合わせる
        if yaml_path is None or not yaml_path.exists():
            return ("", 10)

        try:
            # YAML を読み取り（空の場合は空辞書）
            data = yaml.safe_load(yaml_path.read_text(encoding="utf-8")) or {}

            # rec.rt-rec 配下の設定を取得
            rt = (((data.get("rec") or {}).get("rt-rec")) or {})

            # salt（未指定は空文字）
            salt = rt.get("ab-salt")
            salt = "" if salt is None else str(salt)

            # bPercent（未指定は 10%）
            b_percent = rt.get("ab-b-percent", 10)
            try:
                b_percent = int(b_percent)
            except Exception:
                b_percent = 10

            # 0〜100 に丸め
            if b_percent < 0:
                b_percent = 0
            if b_percent > 100:
                b_percent = 100

            return (salt, b_percent)

        except Exception as ex:
            logger.warning(MESSAGES.get("warn.yaml_parse_failed"), exc_info=ex)
            return ("", 10)

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

        return None
