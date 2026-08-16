package com.news.recapp.application.port.out;

import com.news.recapp.persistence.entity.HashtagEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * ニュースとハッシュタグの紐付けのポート
 */
public interface NewsHashtagPort {

    /**
     * ニュース ID に紐づく有効なハッシュタグ一覧を取得
     */
    Flux<HashtagEntity> findEnabledHashtags(UUID newsId);

    /**
     * ハッシュタグ名に紐づく有効なニュース一覧を取得
     */
    Flux<NewsEntity> findAllVisibleNews(String hashtagName);

    /**
     * ハッシュタグ名に紐づく有効なニュース一覧をランダムに取得（時間を使わない候補生成向け）
     */
    Flux<NewsEntity> findVisibleRandomNews(String hashtagName, long limit);

    /**
     * ニュースのハッシュタグ紐付けを置換（削除->追加）
     */
    Mono<Void> replaceNewsHashtags(UUID newsId, List<UUID> hashtagIds);
}
