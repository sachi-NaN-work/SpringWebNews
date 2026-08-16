// ログインフォームの簡易バリデーション（クライアント側）です。（ファイル概要）
// 目的は「未入力のまま送信してからエラーになる」前に気付けるようにすることです。（目的）
// 最終的な入力チェックはサーバ側で行うため、ここは補助（UX向上）として扱います。（注意事項）

(() => {

  // JSの実行ルール厳格モードを有効化
  'use strict';

  // ログインフォーム要素を取得
  const form = document.getElementById('loginForm');
  // ユーザー ID 入力を取得
  const username = document.getElementById('username');
  // パスワード入力を取得
  const password = document.getElementById('password');
  // ユーザー ID のエラー表示領域を取得
  const errU = document.getElementById('err-username');
  // パスワードのエラー表示領域を取得
  const errP = document.getElementById('err-password');
  // Cookie 無効メッセージ表示領域を取得
  const cookieWarning = document.getElementById('cookie-warning');

  // フォーム が取得できていない、またはユーザー ID 入力欄 が取得できていない、またはパスワード入力欄 が取得できていない、またはユーザー ID エラー表示 が取得できていない、またはパスワードエラー表示 が取得できていない、または Cookie 無効メッセージ表示 が取得できていない場合
  if (!form || !username || !password || !errU || !errP || !cookieWarning) return;

  /**
   * エラー表示領域へメッセージを設定
   */
  function setErr(el, msg) {
    // 表示文字列を更新
    el.textContent = msg;
  }

  /**
   * エラー表示領域をクリア
   */
  function clearErr(el) {
    // 表示文字列を空に設定
    el.textContent = '';
  }

  /**
   * Cookie が利用可能か判定する
   */
  function isCookieAvailable() {

    // Cookie 判定用の名前を用意
    const testName = 'SWN_cookie_test';
    // Cookie 判定用の値を用意
    const testValue = String(Date.now());

    try {
      // 判定用 Cookie を書き込む
      document.cookie = `${testName}=${testValue}; path=/; SameSite=Lax`;
      // 書き込んだ Cookie が読み取れるか確認
      const enabled = document.cookie
          .split(';')
          .map((v) => v.trim())
          .some((v) => v === `${testName}=${testValue}`);
      // 判定用 Cookie を削除
      document.cookie = `${testName}=; Max-Age=0; path=/; SameSite=Lax`;
      // 判定結果を返す
      return enabled;
      // Cookie 読み書きで例外が発生した場合
    } catch (error) {
      // Cookie は利用不可として扱う
      return false;
    }
  }

  /**
   * Cookie 利用可否をチェックして送信可否を返す
   */
  function validateCookieAvailable() {

    // Cookie が利用可能な場合
    if (isCookieAvailable()) {
      // Cookie 無効メッセージを非表示
      cookieWarning.hidden = true;
      // OK として返す
      return true;
    }

    // Cookie 無効メッセージを表示
    cookieWarning.hidden = false;
    // NG として返す
    return false;
  }

  /**
   * 必須項目をチェックして送信可否を返す
   */
  function validateRequired() {

    // すべて OK と仮定して開始
    let ok = true;

    // ユーザー ID をトリムして取得
    const user = (username.value || '').trim();
    // パスワードをトリムして取得
    const pwd = (password.value || '').trim();

    // ユーザー ID が取得できていない場合
    if (!user) {
      // ユーザー ID の不足を表示
      setErr(errU, 'ユーザーIDを入力してください');
      // NG としてマーク
      ok = false;
    // ユーザー ID が入力されている場合の分岐
    } else {
      // エラー表示を消す
      clearErr(errU);
    }

    // メッセージ本文要素 が取得できていない場合
    if (!pwd) {
      // パスワードの不足を表示
      setErr(errP, 'パスワードを入力してください');
      // NG としてマーク
      ok = false;
    // パスワードが入力されている場合の分岐
    } else {
      // エラー表示を消す
      clearErr(errP);
    }

    // チェック結果を返す
    return ok;
  }

  // 初期表示時に Cookie 利用可否を確認
  validateCookieAvailable();

  // 送信時に Cookie 利用可否と必須チェックを行う
  form.addEventListener('submit', (event) => {
    // Cookie 利用可否チェックが NG である場合
    if (!validateCookieAvailable()) {
      // フォーム送信を中止
      event.preventDefault();
      // 後続処理を終了
      return;
    }

    // 必須入力チェックが NG である場合
    if (!validateRequired()) event.preventDefault();
  });

  // ユーザー ID 入力中にエラーを即時解除
  username.addEventListener('input', () => {
    // ユーザー ID が入力されている場合
    if (username.value.trim()) clearErr(errU);
  });

  // パスワード入力中にエラーを即時解除
  password.addEventListener('input', () => {
    // パスワードが入力されている場合
    if (password.value.trim()) clearErr(errP);
  });
})();
