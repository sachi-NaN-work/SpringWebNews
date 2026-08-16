// フィードバック送信のヘルパー（クライアント側）です。（ファイル概要）
// 役割は「他スクリプトから使える送信APIの提供」と「ニュース詳細リンクのクリックログ送信」です。（役割）

(() => {

  // JSの実行ルール厳格モードを有効化
  'use strict';

  /**
   * クライアント側イベントIDを生成
   */
  function uuid() {

    // crypto が利用できる、かつ randomUUID が利用できる場合、イベントIDを生成を返却
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();

    // 未対応環境は時刻 + 乱数で、イベントIDを代替
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;

  }

  /**
   * フィードバックペイロードをサーバへ送信
   */
  function send(payload) {

    try {

      // JSON 文字列へ変換
      const body = JSON.stringify(payload);
      // sendBeacon 用に Blob へ詰める
      const blob = new Blob([body], { type: 'application/json' });

      // sendBeacon が利用できる場合
      if (navigator.sendBeacon) {

        // 送信
        navigator.sendBeacon('/feedback', blob);
        // sendBeacon を使った場合はここで終了
        return;

      }

      // フォールバックとして fetch で送る
      // ログ送信は画面操作や画面遷移を止めないため、あえて await しない
      fetch('/feedback', {

        // POST で送信
        method: 'POST',
        // JSON 送信を宣言
        headers: { 'Content-Type': 'application/json' },
        // 送信ボディに JSON 文字列を設定
        body,

      }).catch(() => {});

    } catch (_) {
      //
    }
  }

  // 他スクリプトから利用できるようにグローバルへ公開
  window.recDemoFeedback = { uuid, send };

  // クリックイベントを監視
  document.addEventListener(
    // クリックを対象に設定
    'click',
    // クリック時ハンドラを定義
    (event) => {

      // クリック元から最寄りのリンク要素を取得
      const aEvent = event.target.closest('a');
      // リンク要素 が取得できていない、またはa.href が取得できていない場合
      if (!aEvent || !aEvent.href) return;

      // クリック先 URL を正規化して解析
      const url = new URL(aEvent.href, window.location.origin);
      // ニュース詳細ページではない場合
      if (!url.pathname.startsWith('/news/')) return;

      // パスをスラッシュで分割
      const parts = url.pathname.split('/');
      // 末尾要素をニュース ID として取り出す
      const newsId = parts[parts.length - 1];
      // ニュース ID が取得できていない場合
      if (!newsId) return;

      // どこから来たクリックか（src）を取得
      const source = url.searchParams.get('src') || 'external';
      // 推薦などのリクエスト ID（rid）を取得
      const requestId = url.searchParams.get('rid') || '';

      // クリックログとして送信
      send({

        // 重複排除や分析用にイベント ID を付与
        eventId: uuid(),
        // 対象ニュース ID を付与
        newsId,
        // 種別は click
        feedbackType: 'click',
        // 発生源を付与
        source,
        // 推薦の相関 ID を付与
        requestId,

      });
    },

    // 画面遷移が早い場合でも拾いやすいよう capture で処理
    { capture: true }
  );

})();
