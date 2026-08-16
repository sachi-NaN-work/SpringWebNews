// お気に入り（★）のUIとAPI連携を行います。（ファイル概要）
// 詳細ページの星ボタンと、お気に入り一覧の解除ボタンを処理します。（対象UI）
// API: POST /api/favorites/{newsId}/favToggle（応答: { favorite: true/false, message?: string }）。（バックエンド）

(() => {

  // JSの実行ルール厳格モードを有効化
  'use strict';

  /**
   * 1 リクエストを識別するイベント ID を生成
   */
  function generateEventId() {

    // randomUUID が使えるかブラウザか判定（古いブラウザでも落ちないように）
    return window.crypto && crypto.randomUUID
      // 対応環境では UUID を返す
      ? crypto.randomUUID()
      // 非対応環境では時刻+乱数で代替
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

  }

  /**
   * お気に入りをトグル（登録/解除）
   *  - 非同期処理（お気に入り登録済みか確認、その結果を待ってから画面更新やエラー判定）
   *  - 現在ページのUI更新
   */
  async function toggleFavorite(newsId, source) {

    // favToggle API を呼ぶ
    const res = await fetch(
      // ニュース ID をパスに含める
      `/api/favorites/${encodeURIComponent(newsId)}/favToggle`,
      {
        // POST で送信
        method: 'POST',
        // JSON 送信を宣言
        headers: { 'Content-Type': 'application/json' },
        // 発生源とイベント ID を送る
        body: JSON.stringify({ source, eventId: generateEventId() }),
        // same-origin の Cookie を送る
        credentials: 'same-origin',
      }
    );

    // JSON 応答を読む（失敗時は空オブジェクト）
    const data = await res.json().catch(() => ({}));

    // HTTP レスポンスと JSON をまとめて返す
    return { res, data };
  }

  /**
   * お気に入り解除の確認を行う
   */
  function confirmRemove(title) {

    // dialog 要素を取得
    const dlg = document.getElementById('favConfirmDialog');

    // タイトル有無で文言を変える
    const msg = title
      // タイトル付き文言を生成
      ? `「${title}」をお気に入りから削除しますか？`
      // タイトル無し文言を生成
      : 'お気に入りから削除しますか？';

    // dialog が取得できていない、または showModal が利用できない場合
    if (!dlg || typeof dlg.showModal !== 'function') {
      // confirm で代替しPromiseで返す
      return Promise.resolve(window.confirm(msg));
    }

    // dialog の close イベントで結果を受け取る
    return new Promise((resolve) => {

      // メッセージ表示先を取得
      const pElement = document.getElementById('favConfirmMessage');

      // メッセージ本文要素 が取得できている場合
      if (pElement) pElement.textContent = msg;

      // close 時の処理を定義
      const onClose = () => {
        // リスナーを解除
        dlg.removeEventListener('close', onClose);
        // yes なら true として返す
        resolve(dlg.returnValue === 'yes');
      };

      // close イベントを購読
      dlg.addEventListener('close', onClose);
      // モーダルとして表示
      dlg.showModal();
    });
  }

  /**
   * 詳細ページの星表示を状態に合わせて更新
   */
  function setArticleStarUi(favBtn, favIcon, isFav) {

    // お気に入りボタン が取得できていない、またはお気に入りアイコン が取得できていない場合
    if (!favBtn || !favIcon) return;

    // 表示文字（★/☆）を切り替える
    favIcon.textContent = isFav ? '★' : '☆';
    // CSS クラスで見た目を切り替える
    favBtn.classList.toggle('is-fav', isFav);
    // アクセシビリティ属性も同期
    favBtn.setAttribute('aria-pressed', isFav ? 'true' : 'false');

  }

  /**
   * お気に入り一覧の該当行をDOMから削除
   */
  function removeFavListEntry(newsId) {

    // 一覧コンテナを取得
    const favList = document.getElementById('favList');
    // お気に入り一覧 が取得できていない場合
    if (!favList) return;
    // ニュース ID 一致の行を探す
    const liElement = favList.querySelector(`li[data-news-id='${CSS.escape(newsId)}']`);

    // 一覧項目が取得できている場合
    if (liElement) liElement.remove();
    // 一覧に要素が残っているか判定
    const hasAny = !!favList.querySelector('li[data-news-id]');
    // 空表示要素を取得
    const empty = document.getElementById('favEmpty');
    // お気に入り一覧が空である、かつ空表示要素 が取得できている場合
    if (!hasAny && empty) empty.style.display = 'block';
  }

  /**
   * お気に入りのタイトルにカテゴリ名を追加
   */
  function formatFavoriteTitle(title, categoryName) {

    // カテゴリ名がある場合は先頭に付与
    if (categoryName) return `【${categoryName}】${title || ''}`;

    // カテゴリ名が無い場合はタイトルのみ返す
    return title || '';
  }

  /**
   * お気に入り一覧に行を追加
   */
  function addFavListEntry(newsId, title, categoryName) {

    // 一覧コンテナを取得
    const favList = document.getElementById('favList');
    // お気に入り一覧 が取得できていない場合
    if (!favList) return;
    // 同じニュース ID の行が既に存在する場合
    if (favList.querySelector(`li[data-news-id='${CSS.escape(newsId)}']`)) return;

    // 空表示要素を取得
    const empty = document.getElementById('favEmpty');
    // 空表示要素 が取得できている場合
    if (empty) empty.style.display = 'none';

    // 行要素を生成
    const liElement = document.createElement('li');
    // 行にニュース ID を持たせる
    liElement.setAttribute('data-news-id', newsId);

    // 解除ボタンを生成
    const btn = document.createElement('button');
    // 表示用タイトルを生成
    const displayTitle = formatFavoriteTitle(title, categoryName);

    // ボタン種別を設定
    btn.type = 'button';
    // CSS クラスを設定
    btn.className = 'fav-remove';
    // 解除ボタンであることを属性で示す
    btn.setAttribute('data-fav-remove', '1');
    // 対象ニュース ID を属性へ入れる
    btn.setAttribute('data-news-id', newsId);
    // タイトルを属性へ入れる
    btn.setAttribute('data-title', displayTitle);
    // アクセシビリティラベルを設定
    btn.setAttribute('aria-label', 'お気に入りから削除');

    // アイコン要素を生成
    const icon = document.createElement('span');
    // アイコン用クラスを設定
    icon.className = 'fav-icon';
    // 星アイコン文字を設定
    icon.textContent = '★';
    // ボタンへアイコンを追加
    btn.appendChild(icon);

    // 詳細ページへのリンクを生成
    const link = document.createElement('a');
    // お気に入り経由としてsrcを付ける
    link.href = `/news/${encodeURIComponent(newsId)}?src=fav`;
    // リンクテキストにタイトルを表示
    link.textContent = displayTitle;
    // 行へ解除ボタンを追加
    liElement.appendChild(btn);
    // 行へリンクを追加
    liElement.appendChild(link);

    // 空表示要素（挿入位置の目印）を取得
    const placeholder = document.getElementById('favEmpty');

    // placeholder が取得できている、かつ placeholder.parentElement が favList である場合
    if (placeholder && placeholder.parentElement === favList) {

      // 空表示の直前へ挿入
      favList.insertBefore(liElement, placeholder);

    } else {
      // 末尾へ追加
      favList.appendChild(liElement);
    }
  }

  /**
   * 詳細ページの星ボタンを初期化
   */
  function initArticleStar() {

    // 星ボタン要素を取得
    const favBtn = document.getElementById('favBtn');
    // 星アイコン要素を取得
    const favIcon = document.getElementById('favIcon');

    // お気に入りボタンが取得できていない、またはお気に入りアイコンが取得できていない場合
    if (!favBtn || !favIcon) return;

    // ニュース ID を取得
    const newsId = favBtn.dataset.newsId;
    // ニュース ID が取得できていない場合
    if (!newsId) return;

    // data-title からタイトルを取得
    const title = favBtn.dataset.title || '';
    // カテゴリ名を取得
    const categoryName = favBtn.dataset.categoryName || '';

    // 初期状態を判定
    let isFav = favBtn.getAttribute('aria-pressed') === 'true' || favBtn.classList.contains('is-fav');
    // 初期状態を UI へ反映
    setArticleStarUi(favBtn, favIcon, isFav);

    // クリックでトグル処理を行う
    favBtn.addEventListener('click', async () => {

      try {

        // 現在 URL を解析
        const url = new URL(window.location.href);
        // 発生源（src）を取得
        const source = url.searchParams.get('src') || 'article';

        // API を呼んで結果を取得
        const { res, data } = await toggleFavorite(newsId, source);

        // res.status が 409 である場合
        if (res.status === 409) {
          // 理由を表示
          alert(data.message || 'お気に入りは最大件数に達しています。不要なものを解除してください。');
          // 処理を中断
          return;
        }

        // レスポンスが正常ではない場合
        if (!res.ok) {
          // 失敗を通知
          alert('お気に入り処理に失敗しました');
          // 処理を中断
          return;
        }

        // サーバ応答を真偽へ変換
        isFav = Boolean(data.favorite);
        // UI を更新
        setArticleStarUi(favBtn, favIcon, isFav);
        // isFav が取得できている場合
        if (isFav) addFavListEntry(newsId, title, categoryName);

        // OFF なら一覧から削除する（同一ページにある場合）
        else removeFavListEntry(newsId);

      } catch (err) {

        // 例外を出力
        console.error(err);
        // 失敗を通知
        alert('お気に入り処理に失敗しました');

      }
    });
  }

  // 一覧解除ボタンはイベント委譲で処理
  document.addEventListener(

    // クリックイベントを対象に設定
    'click',

    // クリック時ハンドラを定義
    async (event) => {

      // 解除ボタンを取得
      const btn = event.target.closest("button[data-fav-remove='1']");
      // ボタン が取得できていない場合
      if (!btn) return;

      // 既定動作を止める
      event.preventDefault();
      // 上位への伝播を止める
      event.stopPropagation();

      // 対象ニュース ID を取得
      const newsId = btn.getAttribute('data-news-id');
      // ニュース ID が取得できていない場合
      if (!newsId) return;

      // タイトルを取得
      const title = btn.getAttribute('data-title') || btn.closest('li')?.querySelector('a')?.textContent || '';

      // 削除確認を行う
      const ok = await confirmRemove(title);
      // ok が取得できていない場合
      if (!ok) return;

      try {

        // 一覧解除として API を呼ぶ
        const { res, data } = await toggleFavorite(newsId, 'favlist');

        // res.status が 409 である場合
        if (res.status === 409) {
          // 理由を表示
          alert(data.message || 'お気に入りは最大件数に達しています。');
          // 処理を中断
          return;
        }

        // レスポンスが正常ではない場合
        if (!res.ok) throw new Error(`favorite toggle failed: status=${res.status}`);

        // サーバ側整合を優先してリロード
        window.location.reload();

      } catch (err) {

        // 例外を出力
        console.error(err);
        // 失敗を通知
        alert('お気に入り処理に失敗しました');

      }
    },
    // ネスト要素でも拾えるよう capture で処理
    { capture: true }
  );

  // DOM 読み込み状態 が「loading」である場合
  if (document.readyState === 'loading') {

    // DOM 構築後に初期化
    document.addEventListener('DOMContentLoaded', initArticleStar);

  // すでに DOM 構築済みの場合の分岐
  } else {

    // 直ちに初期化
    initArticleStar();

  }
})();
