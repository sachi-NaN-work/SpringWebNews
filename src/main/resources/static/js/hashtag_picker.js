// ハッシュタグ選択UI（検索 + 複数選択 + 最大件数制限）です。（ファイル概要）

(() => {

  // JSの実行ルール厳格モードを有効化
  'use strict';

  // 選択できる最大件数を定義
  const MAX = 5;
  // id 検索の短縮関数を定義
  const $ = (id) => document.getElementById(id);

  /**
   * エラー表示を行う
   */
  function showErr(errEl, text) {

    // エラー表示領域 が取得できていない場合
    if (!errEl) return;

    // エラーメッセージ本文を設定
    errEl.textContent = text;
    // エラー領域を表示
    errEl.style.display = 'block';

  }

  /**
   * エラー表示を消す
   */
  function clearErr(errEl) {

    // エラー表示領域 が取得できていない場合
    if (!errEl) return;

    // 表示文字列を空に設定
    errEl.textContent = '';
    // 領域は残したまま非表示に設定
    errEl.style.display = 'none';

  }

  /**
   * 現在チェックされている件数を数える
   */
  function countChecked(list) {

    // 対象チェックボックスを取得
    const boxes = list.querySelectorAll('input[type="checkbox"][name="hashtagIds"]');
    // カウンタを初期化
    let checked = 0;

    // 全チェックボックスを走査
    boxes.forEach((b) => {
      // チェックボックス がチェック済みである場合
      if (b.checked) checked++;
    });

    // 件数を返す
    return checked;
  }

  /**
   * 上限到達時に未選択項目を disabled 化
   */
  function updateDisableState(list) {

    // 対象チェックボックスを取得
    const boxes = list.querySelectorAll('input[type="checkbox"][name="hashtagIds"]');
    // 現在のチェック数を取得
    const checked = countChecked(list);
    // 上限到達かどうかを判定
    const limitReached = checked >= MAX;

    // 各チェックボックスの状態を更新
    boxes.forEach((box) => {

      // 行要素（表示行）を取得
      const row = box.closest('.tag-row');

      // チェックボックス が未選択である、かつ上限到達フラグ が取得できている場合
      if (!box.checked && limitReached) {
        // これ以上選べないように設定
        box.disabled = true;
        // 行要素 が取得できている場合
        if (row) row.classList.add('is-disabled');

      // それ以外の場合は
      } else {

        // 選択可能に戻す
        box.disabled = false;
        // 行要素 が取得できている場合
        if (row) row.classList.remove('is-disabled');

      }
    });

    // 現在件数を返す
    return checked;
  }

  /**
   * 検索文字列で行をフィルタ
   */
  function applyFilter(list, query) {

    // 入力文字列を正規化
    const queryFilter = (query || '').trim().toLowerCase();
    // 表示行を取得
    const rows = list.querySelectorAll('.tag-row');

    // 各行の表示/非表示を決める
    rows.forEach((row) => {

      // 行のハッシュタグ名を取得
      const name = (row.getAttribute('data-name') || row.textContent || '').toLowerCase();
      // 部分一致で可視判定
      const visible = !queryFilter || name.includes(queryFilter);
      // 結果をCSSクラスに反映
      row.classList.toggle('is-hidden', !visible);

    });
  }

  /**
   * 画面要素を取得してイベントを登録
   */
  function init() {

    // ハッシュタグ一覧のコンテナを取得
    const hashtagList = $('hashtagList');
    // ハッシュタグ一覧が取得できていない場合
    if (!hashtagList) return;

    // 検索入力を取得
    const search = $('hashtagSearch');
    // エラー表示領域を取得
    const err = $('hashtagPickError');
    // 「上限超過」文言を埋め込むDOMを取得
    const msgNode = $('hashtagTooManyMsg');

    // 外部ファイル由来文言を取り出す
    let tooManyMsg = msgNode ? msgNode.textContent || '' : '';
    // tooManyMsg が取得できていない場合
    if (!tooManyMsg) tooManyMsg = `ハッシュタグは最大${MAX}件まで選択できます。`;

    // 「最低 1 件」文言を埋め込む DOM を取得
    const reqNode = $('hashtagRequiredMsg');
    // 外部ファイル由来文言を取り出す
    let requiredMsg = reqNode ? reqNode.textContent || '' : '';

    // requiredMsg が取得できていない場合
    if (!requiredMsg) requiredMsg = 'ハッシュタグを最低1件選択してください。';
    // 初期状態で disabled 状態を反映
    updateDisableState(hashtagList);

    // 検索入力欄 が取得できている場合
    if (search) {
      // 入力のたびにフィルタ
      search.addEventListener('input', () => {
        // 現在値でフィルタを適用
        applyFilter(hashtagList, search.value);
      });
    }

    // チェック変更を監視
    hashtagList.addEventListener('change', (event) => {

      // 変更対象を取得
      const target = event.target;
      // target が取得できていない、または target.tagName が「INPUT」ではない、または target.type が「checkbox」ではない場合
      if (!target || target.tagName !== 'INPUT' || target.type !== 'checkbox') return;

      // まずエラー表示を消す
      clearErr(err);
      // 現在の選択数を数える
      const checked = countChecked(hashtagList);

      // 選択数 が MAX である場合
      if (checked > MAX) {
        // 直ちにチェックを戻す
        target.checked = false;
        // 超過理由を表示
        showErr(err, tooManyMsg);
      }

      // disabled 状態を更新
      updateDisableState(hashtagList);

    });

    // 送信対象フォームを取得
    const form = hashtagList.closest('form');

    // フォーム が取得できている場合
    if (form) {

      // 送信前に最低1件を検証
      form.addEventListener('submit', (event) => {

        // まずエラー表示を消す
        clearErr(err);

        // 選択数 が 1 より小さい場合
        if (countChecked(hashtagList) < 1) {

          // 送信を止める
          event.preventDefault();
          // 必須理由を表示
          showErr(err, requiredMsg);
          // 検索入力欄 が取得できている、かつ無効化されていない場合
          if (search && !search.disabled) search.focus();

        }
      });
    }
  }

  // DOM 構築完了後に初期化
  document.addEventListener('DOMContentLoaded', init);

})();
