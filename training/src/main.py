"""学習バッチのメイン処理を提供。

処理は Java 側のサービス実装と同じ考え方で、
役割ごとにクラスへ分割し、ここではオーケストレーションのみ行います。

処理フロー:
  1. 前処理 (pre_process)  : 引数/設定/DB接続/データ読込
  2. メイン (main_process) : 特徴量生成 → 学習 → ONNX 出力
  3. 後処理 (post_process) : レポート書き出し
"""

import sys
from typing import List

from training.src.argument.ArgumentParserClass import ArgumentParserClass
from training.src.common.LoggerClass import LoggerClass
from training.src.process.main_process.MainProcessClass import MainProcessClass
from training.src.process.post_process.PostProcessClass import PostProcessClass
from training.src.process.pre_process.PreProcessClass import PreProcessClass

# --------------------------------------
# メイン処理
# --------------------------------------
def run_main(argv: List[str] | None = None) -> int:

    # 引数一覧（未指定の場合は CLI 引数）を取得
    args_argv = argv or sys.argv[1:]

    # コマンドライン引数を解釈
    args = ArgumentParserClass().parse(args_argv)

    # ロガー設定（verbose に応じてログレベルを切り替え）
    LoggerClass().configure(verbose=args.verbose)

    # 前処理を実行し、共有コンテキストを生成
    context = PreProcessClass().run(args)

    # メイン処理を実行し、学習結果（ONNX/レポート）をコンテキストへ反映
    context = MainProcessClass().run(context)

    # 後処理を実行し、レポートを書き出し
    PostProcessClass().run(context)

    # 正常終了コードを返却
    return 0
