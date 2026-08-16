// 無操作タイムアウト時に共通エラー画面へ自動遷移します。（ファイル概要）
// 同一ブラウザ内の全タブで最後に操作された時刻から、設定時間が経過した場合にタイムアウト処理へ遷移します。（対象UI）

(() => {

  // JSの実行ルール厳格モードを有効化
  'use strict';

  // 初期化済みキーを保持
  const initializedKey = '__springWebNewsIdleTimeoutInitialized';
  // 既に初期化済みの場合
  if (window[initializedKey]) return;
  // 初期化済みとして記録
  window[initializedKey] = true;

  // 共通ヘッダー要素を取得
  const header = document.querySelector('.site-header');
  // 共通ヘッダー要素 が取得できていない場合
  if (!header) return;

  // 現在のパスを取得
  const pathname = window.location.pathname;
  // 除外対象パスを保持
  const excludedPathnames = ['/login', '/error', '/timeout'];
  // 除外対象パスの場合
  if (excludedPathnames.includes(pathname)) return;

  // ブラウザの最終操作時刻キーを保持
  const lastActivityKey = 'springWebNews.lastActivityAt';
  // ログアウト通知キーを保持
  const logoutNoticeKey = 'springWebNews.logoutAt';
  // タイムアウト時間をミリ秒で取得
  const timeoutMillis = Number(header.dataset.idleTimeoutMillis || '300000');

  // タイムアウト時間が不正な場合
  if (!Number.isFinite(timeoutMillis) || timeoutMillis <= 0) return;

  // タイマーIDを初期化
  let timerId = null;
  // タイムアウト済みフラグを初期化
  let timedOut = false;
  // ログアウト済みフラグを初期化
  let loggedOut = false;

  // ブラウザの保存領域の利用可否フラグを初期化
  let storageAvailable = true;
  // ブラウザの保存領域が利用不可時のタブ内保存領域を保持
  const fallbackStorage = new Map();

  /**
   * ブラウザの保存領域の値を取得
   */
  function getStorageValue(key) {

    // ブラウザの保存領域が利用可能な場合
    if (storageAvailable) {

      try {

        // ブラウザの保存領域から値を取得
        const value = window.localStorage.getItem(key);

        // 値が取得できた場合
        if (value !== null) {

          // タブ内保存領域にも値を保持
          fallbackStorage.set(key, value);

          // 取得した値を返却
          return value;
        }

        // ブラウザの保存領域が使えない場合
      } catch (e) {

        // ブラウザの保存領域を利用不可として設定
        storageAvailable = false;
      }
    }

    // タブ内保存領域の値を返却
    return fallbackStorage.get(key) ?? null;
  }

  /**
   * 値を保存
   */
  function setStorageValue(key, value) {

    // タブ内保存領域へ値を設定
    fallbackStorage.set(key, value);

    // ブラウザの保存領域が利用できない場合
    if (!storageAvailable) return;

    try {

      // ブラウザの保存領域へ値を設定
      window.localStorage.setItem(key, value);

      // ブラウザの保存領域が使えない場合
    } catch (e) {

      // ブラウザの保存領域が利用不可として設定
      storageAvailable = false;
    }
  }

  /**
   * ブラウザの最終操作時刻を取得
   */
  function getLastActivityAt() {

    // ブラウザの最終操作時刻を数値で取得
    const value = Number(getStorageValue(lastActivityKey));
    // ブラウザの最終操作時刻が有効な場合
    if (Number.isFinite(value) && value > 0) return value;

    // 現在時刻を返却
    return Date.now();

  }

  /**
   * タイムアウト処理へ遷移
   */
  function redirectToTimeout() {

    // タイムアウト済みとして設定
    timedOut = true;
    // 既存タイマーを解除
    window.clearTimeout(timerId);
    // タイムアウト処理へ遷移
    window.location.assign('/timeout');

  }

  /**
   * ログアウト画面へ遷移
   */
  function redirectToLogout() {

    // ログアウト済みとして設定
    loggedOut = true;
    // 既存タイマーを解除
    window.clearTimeout(timerId);
    // ログアウト完了画面へ遷移
    window.location.assign('/login?logout');

  }

  /**
   * タイムアウト到達を判定
   */
  function checkTimeout() {

    // タイムアウト済み、またはログアウト済みの場合
    if (timedOut || loggedOut) return;

    // 現在時刻を取得
    const now = Date.now();
    // 最終操作時刻を取得
    const lastActivityAt = getLastActivityAt();
    // タイムアウトまでの残り時間を計算
    const remainingMillis = timeoutMillis - (now - lastActivityAt);

    // タイムアウトに到達している場合
    if (remainingMillis <= 0) {
      // タイムアウト処理へ遷移
      redirectToTimeout();
      // ここで処理を終了
      return;
    }

    // 残り時間でタイマーを再設定
    startTimer(remainingMillis);

  }

  /**
   * タイマーを開始
   */
  function startTimer(delayMillis) {

    // 既存タイマーを解除
    window.clearTimeout(timerId);
    // 指定時間後にタイムアウト判定を行う
    timerId = window.setTimeout(checkTimeout, delayMillis);

  }

  /**
   * 最終操作時刻を更新
   */
  function updateLastActivity() {

    // タイムアウト済み、またはログアウト済みの場合
    if (timedOut || loggedOut) return;

    // 現在時刻を文字列で保持
    const now = String(Date.now());
    // ブラウザの最終操作時刻を全タブ向けに更新
    setStorageValue(lastActivityKey, now);
    // タイマーを再設定
    startTimer(timeoutMillis);

  }

  /**
   * ブラウザの画面表示状態変更時の処理
   */
  function onVisibilityChange() {

    // 画面が表示状態に戻った場合
    if (!document.hidden) {
      // タイムアウト到達を判定
      checkTimeout();
    }

  }

  /**
   * 他タブの状態変更を反映
   */
  function onStorage(event) {

    // ログアウト通知を受け取った場合
    if (event.key === logoutNoticeKey) {
      // ログアウト画面へ遷移
      redirectToLogout();
      // ここで処理を終了
      return;
    }

    // ブラウザの最終操作時刻が更新された場合
    if (event.key === lastActivityKey) {
      // タイムアウト到達を判定
      checkTimeout();
    }

  }

  /**
   * ログアウト送信時の処理
   */
  function onLogoutSubmit() {

    // ログアウト済みとして設定
    loggedOut = true;
    // ログアウト通知を全タブ向けに更新
    setStorageValue(logoutNoticeKey, String(Date.now()));
    // 既存タイマーを解除
    window.clearTimeout(timerId);

  }

  // ログアウトフォーム要素を取得
  const logoutForm = document.querySelector('form[data-logout-form="true"]');
  // ログアウトフォーム要素 が取得できている場合
  if (logoutForm) logoutForm.addEventListener('submit', onLogoutSubmit);

  // 無操作判定をリセットするイベント一覧を保持
  const activityEvents = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll', 'input', 'change'];

  // 各イベントへ最終操作時刻更新処理を登録
  activityEvents.forEach((eventName) => {
    // 操作イベントを監視
    window.addEventListener(eventName, updateLastActivity, { passive: true });
  });

  // 他タブの状態変更を監視
  window.addEventListener('storage', onStorage);
  // ブラウザの画面表示状態の変更を監視
  document.addEventListener('visibilitychange', onVisibilityChange);
  // 初期の最終操作時刻を設定
  updateLastActivity();

})();
