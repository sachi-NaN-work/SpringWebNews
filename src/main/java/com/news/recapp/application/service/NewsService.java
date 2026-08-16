package com.news.recapp.application.service;

import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.exception.AppMessageException;
import com.news.recapp.config.NewsProperties;
import com.news.recapp.persistence.entity.CategoryEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import com.news.recapp.application.port.out.CategoryPort;
import com.news.recapp.application.port.out.NewsHashtagPort;
import com.news.recapp.application.port.out.NewsPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * ニュースの業務処理を提供
 */
@Service
public class NewsService {

    // ニュース記事を保持
    private final NewsPort newsPort;
    // カテゴリを保持
    private final CategoryPort categoryPort;
    // ニュース記事ハッシュタグを保持
    private final NewsHashtagPort newsHashtagPort;
    // ニュース設定を保持
    private final NewsProperties newsProps;

    // コンストラクタ
    public NewsService(
        NewsPort newsPort,
        CategoryPort categoryPort,
        NewsHashtagPort newsHashtagPort,
        NewsProperties newsProps
    ) {
        this.newsPort = newsPort;
        this.categoryPort = categoryPort;
        this.newsHashtagPort = newsHashtagPort;
        this.newsProps = newsProps;
    }

    /**
     * ハッシュタグ名でニュースを取得（全件・新着順）
     */
    public Flux<ApiDtos.NewsSummary> allNewsByHashtagName(String hashtagName) {

        // 画面/手入力の揺れ（前後空白、先頭 #/＃）を吸収
        String name = (hashtagName == null) ? "" : hashtagName.trim();
        while (!name.isEmpty() && (name.charAt(0) == '#' || name.charAt(0) == '＃')) {
            name = name.substring(1).trim();
        }

        // ハッシュタグ名が空の場合
        if (name.isBlank()) {
            // 空を返却
            return Flux.empty();
        }

        // ハッシュタグ名に紐づくニュース一覧を返却
        return newsHashtagPort.findAllVisibleNews(name).map(this::toSummary);
    }

    /**
     * カテゴリ一覧を取得
     */
    public Flux<ApiDtos.Category> listCategories() {
        // カテゴリ一覧を返却
        return categoryPort.findAllByEnabledTrueOrderByNameAsc().map(this::toDto);
    }

    /**
     * 最新ニュース一覧を取得
     */
    public Flux<ApiDtos.NewsSummary> latestNews() {
        // 最新ニュース一覧を返却
        return newsPort.findLatestVisible(newsProps.getLatestLimit()).map(this::toSummary);
    }

    /**
     * カテゴリスラグでニュースを取得
     */
    public Flux<ApiDtos.NewsSummary> allNewsByCategorySlug(String slug) {
        // ニュースを返却
        return categoryPort.findBySlug(slug)
                // カテゴリに紐づくニュース一覧の Flux に展開（Mono<CategoryEntity> を Flux<NewsEntity> に変換）
                .flatMapMany(category -> newsPort.findAllVisibleByCategory(category.getId()))
                // ニュース情報をレスポンス用 DTO に変換
                .map(this::toSummary);
    }

    /**
     * 全ニュース一覧を取得
     */
    public Flux<ApiDtos.NewsSummary> allNews() {
        // 全ニュース一覧を返却
        return newsPort.findAllVisible().map(this::toSummary);
    }

    /**
     * ニュースを取得
     */
    public Mono<ApiDtos.NewsDetail> getNews(UUID newsId) {

        // ニュース ID からニュースを取得
        return newsPort.findByNewsId(newsId)
                // 有効なニュースのみ抽出
                .filter(newsEntity -> newsEntity.getEnabled() == null || Boolean.TRUE.equals(newsEntity.getEnabled()))
                // カテゴリ情報を取得
                .flatMap(newsEntity -> categoryPort.findByCategoryId(newsEntity.getCategoryId())
                // 有効なカテゴリのみ抽出
                .filter(categoryEntity -> categoryEntity.getEnabled() == null || Boolean.TRUE.equals(categoryEntity.getEnabled()))
                                // 有効なカテゴリが存在しない場合、エラーを返却
                                .switchIfEmpty(Mono.error(new AppMessageException("news.hashtag.edit.err.news_not_found")))
                                // 処理完了後、後続処理を実行
                                .then(
                                        // ニュース ID に紐づく有効なハッシュタグ一覧を取得
                                        Mono.defer(() -> Mono.justOrEmpty(newsHashtagPort.findEnabledHashtags(newsEntity.getId())))
                                                // ハッシュタグ一覧の Flux を取り出す（Mono<Flux<HashtagEntity>> を Flux<HashtagEntity> に平坦化）
                                                .flatMapMany(flux -> flux)
                                                // ハッシュタグ情報を取得
                                                .map(hashtagEntity -> new ApiDtos.Hashtag(hashtagEntity.getId(), hashtagEntity.getName()))
                                                // リストにまとめる
                                                .collectList()
                                                // カテゴリ一覧が空の場合、デフォルト値で補完
                                                .defaultIfEmpty(List.of())
                                                // レスポンスを生成
                                                .map(tags -> new ApiDtos.NewsDetail(
                                                        newsEntity.getId(),
                                                        newsEntity.getCategoryId(),
                                                        newsEntity.getTitle(),
                                                        newsEntity.getBody(),
                                                        newsEntity.getPublishedAt(),
                                                        tags
                                                ))
                                )
                )
                // カテゴリが取得できなかった場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.error(new AppMessageException("news.hashtag.edit.err.news_not_found")));
    }

    /**
     * ニュース情報を取得
     */
    public Mono<ApiDtos.NewsSummary> getNewsSummary(UUID newsId) {
        // ニュース情報を返却
        return newsPort.findByNewsId(newsId)
                // キーと件数が有効な要素のみ抽出
                .filter(newsEntity -> newsEntity.getEnabled() == null || Boolean.TRUE.equals(newsEntity.getEnabled()))
                // カテゴリ情報の取得
                .flatMap(newsEntity -> categoryPort.findByCategoryId(newsEntity.getCategoryId())
                // キーと件数が有効な要素のみ抽出
                .filter(categoryEntity -> categoryEntity.getEnabled() == null || Boolean.TRUE.equals(categoryEntity.getEnabled()))
                // ニュース情報を概要レスポンス用オブジェクトに変換
                .map(categoryEntity -> toSummary(newsEntity)));
    }

    /**
     * ニュース記事を取得
     */
    public Mono<NewsEntity> getNewsEntity(UUID newsId) {
        // ニュース記事を返却
        return newsPort.findByNewsId(newsId);
    }

    /**
     * カテゴリを取得
     */
    public Mono<CategoryEntity> getCategory(UUID categoryId) {
        // カテゴリを返却
        return categoryPort.findByCategoryId(categoryId);
    }

    /**
     * カテゴリスラグでカテゴリを取得
     */
    public Mono<CategoryEntity> getCategoryBySlug(String slug) {
        // カテゴリを返却
        return categoryPort.findBySlug(slug);
    }

    /**
     * カテゴリ情報をレスポンス用オブジェクトに変換
     */
    private ApiDtos.Category toDto(CategoryEntity category) {
        // カテゴリ情報レスポンス用オブジェクトを返却
        return new ApiDtos.Category(category.getId(), category.getSlug(), category.getName());
    }

    /**
     * ニュース記事情報をレスポンス用オブジェクトに変換
     */
    private ApiDtos.NewsSummary toSummary(NewsEntity name) {
        // ニュース記事情報レスポンス用オブジェクトを返却
        return new ApiDtos.NewsSummary(name.getId(), name.getCategoryId(), name.getTitle(), name.getPublishedAt());
    }
}
