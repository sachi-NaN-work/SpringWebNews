package com.news.recapp.application.service;

import com.news.recapp.persistence.entity.CategoryEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import com.news.recapp.application.port.out.CategoryPort;
import com.news.recapp.application.port.out.NewsPort;
import com.news.recapp.application.exception.AppMessageException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 管理ニュースの業務処理を提供
 */
@Service
public class AdminNewsService {

    // カテゴリスラグの最大文字数を表す定数
    private static final int CATEGORY_SLUG_MAX = 10;
    // カテゴリ名の最大文字数を表す定数
    private static final int CATEGORY_NAME_MAX = 10;
    // ニュースタイトルの最大文字数を表す定数
    private static final int TITLE_NAME_MAX = 20;

    // カテゴリを保持
    private final CategoryPort categoryPort;
    // ニュース記事を保持
    private final NewsPort newsPort;

    // 日本時間のタイムゾーン
    private static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");

    // コンストラクタ
    public AdminNewsService(
        CategoryPort categoryPort,
        NewsPort newsPort
    ) {
        this.categoryPort = categoryPort;
        this.newsPort = newsPort;
    }

    /**
     * カテゴリの作成
     */
    public Mono<CategoryEntity> createCategory(String slug, String name) {

        // スラグをトリムして取得
        String textSlug = (slug == null) ? "" : slug.trim();

        // カテゴリ名をトリムして取得
        String textName = (name == null) ? "" : name.trim();

        // スラグが空の場合
        if (textSlug.isBlank()) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException(
                    "news.category.create.err.slug.required"
            ));
        }

        // スラグが最大文字数を超える場合
        if (textSlug.length() > CATEGORY_SLUG_MAX) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException(
                    "news.category.create.err.slug.too_long",
                    CATEGORY_SLUG_MAX
            ));
        }

        // カテゴリ名が取得できなかった場合
        if (textName.isBlank()) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException(
                    "news.category.create.err.name.required"
            ));
        }

        // カテゴリ名が最大文字数を超える場合
        if (textName.length() > CATEGORY_NAME_MAX) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException(
                    "news.category.create.err.name.too_long",
                    CATEGORY_NAME_MAX
            ));
        }

        // カテゴリ名のスラグ確認
        Mono<CategoryEntity> slugCheck = categoryPort.findBySlug(textSlug)
                // スラグ重複が発生した場合、エラーメッセージを付与
                .flatMap(existing ->
                        Mono.error(new AppMessageException(
                                "news.category.create.err.slug.duplicate"
                        ))
                );

        // カテゴリ名の確認
        Mono<CategoryEntity> nameCheck = categoryPort.findByNameIgnoreCase(textName)
                // カテゴリ名重複が発生した場合、エラーメッセージを付与
                .flatMap(existing ->
                        Mono.error(new AppMessageException(
                                "news.category.create.err.name.duplicate"
                        ))
                );

        // スラグ確認が取得できた場合、そのまま返却
        return slugCheck
                // スラグ確認ができなかった場合、カテゴリ名確認を返却
                .switchIfEmpty(nameCheck)
                // スラグ確認・カテゴリ名確認ができなかった場合、新規作成して保存して返却
                .switchIfEmpty(Mono.defer(() -> categoryPort.save(new CategoryEntity(
                        UUID.randomUUID(),
                        textSlug,
                        textName,
                        Boolean.TRUE)))
                );
    }

    /**
     * ニュースを作成
     */
    public Mono<NewsEntity> createNews(UUID categoryId, String title, String body) {

        // ニュースタイトルを取得
        String newsTitle = (title == null) ? "" : title.trim();

        // ニュース本文を取得
        String newsBody = (body == null) ? "" : body.trim();

        // カテゴリ ID が取得できなかった場合
        if (categoryId == null) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("news.ctrl.err.category.select"));
        }

        // ニュースタイトルが取得できなかった場合
        if (newsTitle.isBlank()) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException(
                    "news.article.ctrl.err.title.required.full"
            ));
        }

        // ニュースタイトルが最大文字数を超える場合
        if (newsTitle.length() > TITLE_NAME_MAX) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException(
                    "news.article.ctrl.err.title.too_long.full",
                    TITLE_NAME_MAX
            ));
        }

        // ニュース本文が空の場合
        if (newsBody.isBlank()) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException(
                    "news.article.ctrl.err.body.required"
            ));
        }

        // ニュースオブジェクトを生成
        NewsEntity item = new NewsEntity(
                UUID.randomUUID(),
                categoryId,
                newsTitle,
                newsBody,
                OffsetDateTime.now(JAPAN_ZONE),
                Boolean.TRUE
        );

        // ニュースを保存して返却
        return newsPort.saveNews(item);
    }

    /**
     * カテゴリの有効フラグを設定
     */
    public Mono<Void> setCategoryEnabled(UUID categoryId, boolean enabled) {

        // カテゴリ ID が取得できなかった場合
        if (categoryId == null) {
            // 値なしで返却
            return Mono.empty();
        }

        // カテゴリを取得
        return categoryPort.findByCategoryId(categoryId)
                // カテゴリが取得できなかった場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.error(new AppMessageException("news.category.disable.err.not_found")))

                // カテゴリ
                .flatMap(category -> {
                    // カテゴリの有効フラグを設定
                    category.setEnabled(enabled);
                    // カテゴリを保存して返却
                    return categoryPort.save(category).then();
                });
    }

    /**
     * ニュース記事の有効フラグを設定
     */
    public Mono<Void> setNewsEnabled(UUID newsId, boolean enabled) {

        // ニュース ID が取得できなかった場合
        if (newsId == null) {
            // 値なしで返却
            return Mono.empty();
        }

        // ニュース記事を取得
        return newsPort.findByNewsId(newsId)
                // ニュース記事が取得できなかった場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.error(new AppMessageException("news.hashtag.edit.err.news_not_found")))
                // ニュース記事
                .flatMap(news -> {
                    // ニュースの有効フラグを設定
                    news.setEnabled(enabled);
                    // ニュースを保存して返却
                    return newsPort.saveNews(news).then();
                });
    }

    /**
     * カテゴリ一覧を取得
     */
    public Flux<CategoryEntity> listCategoriesForAdmin() {
        return categoryPort.findAllCategory();
    }

    /**
     * ニュース一覧を取得
     */
    public Flux<NewsEntity> listNewsForAdmin() {
        return newsPort.findAllNews();
    }

}
