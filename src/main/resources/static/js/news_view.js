// ニュース詳細の「有効閲覧(view)」ログを送信します。（ファイル概要）
// 可視状態で一定時間（0.2秒=200ms）滞在したら /feedback へ送ります。

(() => {

  // JSの実行ルール厳格モードを有効化
  'use strict';

  // 設定値が埋め込まれた<main>を取得
  const main = document.querySelector('main[data-news-id]');
  // data-news-idからニュース ID を取り出す
  let newsId = main ? main.getAttribute('data-news-id') : '';

  // ニュース ID が取得できていない場合
  if (!newsId) {
    // URL末尾から ID を抽出
    const newsIds = window.location.pathname.match(/\/news\/([0-9a-fA-F-]{16,})$/);
    // 抽出結果をニュース ID へ反映
    newsId = newsIds ? newsIds[1] : '';
  }

  // ニュース ID が取得できていない場合
  if (!newsId) return;

  // 有効閲覧(view) の送信設定（data-view-ms。未設定/不正値なら 200ms）
  const thresholdMsRaw = Number(main?.dataset?.viewMs);
  const thresholdMs =
      Number.isFinite(thresholdMsRaw) && thresholdMsRaw > 0
          ? thresholdMsRaw
          : 200;

  // ナビゲーション種別を取得
  const navEntries = performance.getEntriesByType('navigation');
  // 種別を取り出す（未取得ならnavigate扱い）
  const navType = navEntries && navEntries.length ? navEntries[0].type : 'navigate';

  // 遷移種別 が「reload」である、または遷移種別 が「back_forward」である場合
  if (navType === 'reload' || navType === 'back_forward') return;

  // 現在 URL を解析
  const url = new URL(window.location.href);
  // 遷移元（src）を取得
  const source = url.searchParams.get('src') || 'external';
  // 推薦などの相関 ID（rid）を取得
  const requestId = url.searchParams.get('rid') || '';

  // 送信済み判定キーを生成
  const sentKey = `view_sent_${newsId}`;
  // 送信済みフラグが存在する場合
  if (sessionStorage.getItem(sentKey)) return;

  // イベント ID の保存キーを生成
  const eventKey = `view_event_${newsId}`;
  // 既存のイベント ID を取得
  let eventId = sessionStorage.getItem(eventKey);

  // イベント ID が取得できていない場合
  if (!eventId) {

    // feedback.js の UUID が使えるか判定
    eventId = window.recDemoFeedback?.uuid
      // UUID 生成関数を使う
      ? window.recDemoFeedback.uuid()
      // 代替として時刻 + 乱数を使う
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

    // イベント ID をタブ内に固定保存
    sessionStorage.setItem(eventKey, eventId);
  }

  // 送信関数 が取得できていない場合
  if (!window.recDemoFeedback?.send) return;

  // 残り時間（ms）を初期化
  let remaining = thresholdMs;
  // タイマー開始時刻を初期化
  let startedAt = 0;
  // setTimeout ハンドルを初期化
  let timer = null;

  /**
   * タイマーを開始
   */
  function startTimer() {

    // タイマー が取得できている場合
    if (timer) return;
    // 開始時刻を記録
    startedAt = Date.now();
    // 残り時間後に送信処理を呼ぶ
    timer = setTimeout(fire, remaining);

  }

  /**
   * タイマーを一時停止
   */
  function pauseTimer() {

    // タイマー が取得できていない場合
    if (!timer) return;
    // タイマーを停止
    clearTimeout(timer);
    // ハンドルをクリア
    timer = null;

    // 経過時間を計算
    const elapsed = Date.now() - startedAt;
    // 残り時間を更新する（0未満にしない）
    remaining = Math.max(0, remaining - elapsed);

  }

  /**
   * 閾値到達時の送信処理を行う
   */
  function fire() {

    // 送信済みフラグが存在する場合
    if (sessionStorage.getItem(sentKey)) return;
    // 先に送信済みマークを立てる
    sessionStorage.setItem(sentKey, '1');

    // /feedback へ送信
    window.recDemoFeedback.send({
      // 固定イベント ID を付与
      eventId,
      // 対象ニュース ID を付与
      newsId,
      // 種別は view
      feedbackType: 'view',
      // 遷移元を付与
      source,
      // 相関 ID を付与
      requestId,
    });

    // 後続イベントで重複しないよう停止
    pauseTimer();
    // visibility 監視を解除
    document.removeEventListener('visibilitychange', onVisibility);

  }

  /**
   * 可視/不可視の切替を処理
   */
  function onVisibility() {

    // ページが非表示である場合
    if (document.hidden) {
      // タイマーを止める
      pauseTimer();
    // 表示に戻った場合の分岐
    } else {
      // タイマーを再開
      startTimer();
    }
  }

  // タブ可視状態の変更を監視
  document.addEventListener('visibilitychange', onVisibility);
  // ページが表示である場合
  if (!document.hidden) startTimer();

})();
