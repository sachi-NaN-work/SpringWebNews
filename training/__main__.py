import sys
sys.dont_write_bytecode = True

from training.src.main import run_main

# --------------------------------------
# メイン処理
# --------------------------------------
def main() -> int:

    # 引数一覧（スクリプト名を除く）を取得
    argv = sys.argv[1:]
    # 実行して終了コードを取得
    exit_code = run_main(argv)
    # 終了コードを返却
    return int(exit_code)

if __name__ == "__main__":
    raise SystemExit(main())
