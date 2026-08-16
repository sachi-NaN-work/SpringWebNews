// good/bad ボタンのフィードバック（学習用）を /feedback に送信します。（ファイル概要）

(() => {

  // JSの実行ルール厳格モードを有効化
  'use strict';

  /**
   * DOM 準備完了後にコールバックを実行
   */
  function onReady(fn) {

    // DOM 読み込み状態 が「loading」である場合
    if (document.readyState === 'loading') {

      // 読み込み完了後に実行
      document.addEventListener('DOMContentLoaded', fn);

    // すでに読み込み済みの場合の分岐
    } else {

      // 直ちに実行
      fn();

    }

  }

  /**
   * URL からニュース ID を取得
   */
  function getNewsId() {

    // URL 形式 /news/{uuid} を想定して抽出
    const newsIds = window.location.pathname.match(/\/news\/([0-9a-fA-F-]{36})/);
    // 抽出できた場合、ニュース ID を返却
    return newsIds ? newsIds[1] : null;

  }

  /**
   * 選択状態（good/bad）をUIへ反映
   */
  function applySelected(container, selectedType) {

    // 対象ボタン一覧を取得
    const reactionButtons = container.querySelectorAll('.reaction-btn');

    // 各ボタンを走査
    reactionButtons.forEach((reactionButton) => {
      // ボタンが表す種別を取得
      const reactionType = reactionButton.getAttribute('data-feedback-type');
      // 選択状態の CSS クラスを切り替える
      reactionButton.classList.toggle('selected', reactionType === selectedType);
    });

    // 現在状態を data 属性にも同期
    container.setAttribute('data-user-reaction', selectedType || '');
  }

  /**
   * 現在の選択状態を取得
   */
  function getSelected(container) {

    // サーバから渡される data 属性を優先して読む
    const reaction = container.getAttribute('data-user-reaction');
    // data 属性が取得できない場合、そのまま返却
    if (reaction != null && reaction !== '') return reaction;

    // 念のため DOM から選択ボタンを探す
    const selectedBtn = container.querySelector('.reaction-btn.selected');
    // DOM 側の値を返却
    return selectedBtn ? selectedBtn.getAttribute('data-feedback-type') || '' : '';
  }

  /**
   * フィードバックをサーバへ送信
   *  - 非同期処理（通信の成否を確認、その結果を待ってからUI更新・エラー表示・ボタン再有効化）
   *  - 現在ページのUI更新
   */
  async function sendReaction(newsId, type) {

    // 送信用ペイロードを組み立てる
    const payload = {
      // 対象ニュース ID を付与
      newsId,
      // 種別（good/bad/clear）を付与
      feedbackType: type,
      // 発生源を reaction として固定
      source: 'reaction',
      // イベント ID は任意（未使用の場合は空文字）
      eventId: '',
      // リクエスト ID は任意（未使用の場合は空文字）
      requestId: '',
    };

    // /feedback へ POST
    const res = await fetch('/feedback', {
      // HTTP メソッドは POST と
      method: 'POST',
      // JSON 送信を宣言
      headers: { 'Content-Type': 'application/json' },
      // ペイロードを JSON 化して送る
      body: JSON.stringify(payload),
      // same-origin の Cookie を送る
      credentials: 'same-origin',
    });

    // レスポンスが正常ではない場合、エラーステータスを付与してスロー
    if (!res.ok) throw new Error(`status=${res.status}`);

  }

  // DOM 準備完了後に初期化
  onReady(() => {

    // 対象ニュース ID を取得
    const newsId = getNewsId();
    // ニュース ID が取得できていない場合
    if (!newsId) return;

    // ボタンコンテナを取得
    const container = document.querySelector('.reactions');
    // ボタンコンテナ が取得できていない場合
    if (!container) return;
    // 初期状態を UI へ反映
    applySelected(container, container.getAttribute('data-user-reaction') || '');
    // ボタン一覧を取得
    const reactionButtons = container.querySelectorAll('.reaction-btn');

    // ボタン一覧 が取得できていない、またはボタン件数 が 0 である場合
    if (!reactionButtons || reactionButtons.length === 0) return;

    // 各ボタンへクリック処理を登録
    reactionButtons.forEach((reactionButton) => {

      // クリック時の送信処理を定義
      reactionButton.addEventListener('click', async () => {

        // ボタン が無効化されている場合
        if (reactionButton.disabled) return;

        // このボタンの種別を取得
        const feedbackType = reactionButton.getAttribute('data-feedback-type');
        // 現在の選択状態を取得
        const current = getSelected(container);
        // 同じボタンの再押下なら解除と判定
        const typeClear = current === feedbackType;
        // 送信する種別（解除ならclear）を決める
        const sendType = typeClear ? 'reaction_clear' : feedbackType;

        try {

          // 連打防止のため送信中は全ボタンを無効化
          reactionButtons.forEach((reactionButton) => {
            reactionButton.disabled = true;
          });

          // フィードバックを送信
          await sendReaction(newsId, sendType);

          // 成功したらUI状態を反映
          applySelected(container, typeClear ? '' : feedbackType);

        } catch (e) {

          // 失敗内容をコンソールに出す
          console.warn('[reaction] failed', e);
          // ユーザーへ失敗を通知
          alert('送信に失敗しました（ログインが必要です）');

        } finally {

          // 送信完了後はボタンを再有効化
          reactionButtons.forEach((reactionButton) => {
            reactionButton.disabled = false;
          });

        }
      });
    });
  });
})();
