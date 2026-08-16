package com.news.recapp.application.port.out;

import com.news.recapp.persistence.entity.NewsEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ニュースのポート
 */
public interface NewsPort {

    /**
     * 最新ニュース一覧を取得
     */
    Flux<NewsEntity> findLatestVisible(long limit);

    /**
     * カテゴリ指定で表示ニュースを全件取得
     */
    Flux<NewsEntity> findAllVisibleByCategory(UUID categoryId);

    /**
     * 表示可能ニュース記事一覧を公開日時降順で取得
     */
    Flux<NewsEntity> findAllVisible();

    /**
     * 表示可能ニュースをランダムに取得
     */
    Flux<NewsEntity> findVisibleRandom(long limit);

    /**
     * 表示可能ニュースをカテゴリ指定でランダムに取得
     */
    Flux<NewsEntity> findVisibleRandomByCategory(UUID categoryId, long limit);

    /**
     * 全ニュース一覧を取得
     */
    Flux<NewsEntity> findAllNews();

    /**
     * ニュース ID からニュースを取得
     */
    Mono<NewsEntity> findByNewsId(UUID id);

    /**
     * ニュースを保存
     */
    Mono<NewsEntity> saveNews(NewsEntity entity);
}
