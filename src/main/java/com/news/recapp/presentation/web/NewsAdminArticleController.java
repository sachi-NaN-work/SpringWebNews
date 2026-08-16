package com.news.recapp.presentation.web;

import com.news.recapp.application.service.AdminNewsService;
import com.news.recapp.application.service.HashtagAdminService;
import com.news.recapp.application.exception.AppMessageException;
import com.news.recapp.application.port.out.NewsHashtagPort;
import com.news.recapp.application.service.UserAccountService;
import com.news.recapp.persistence.entity.AppUserEntity;
import com.news.recapp.persistence.entity.CategoryEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import com.news.recapp.persistence.entity.HashtagEntity;
import com.news.recapp.util.Messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * ニュース記事管理（管理画面）を扱うコントローラ
 * - 作成: GET /mypage/admin/news/article/create , POST /mypage/admin/news/article/create
 * - 有効/無効切替: GET /mypage/admin/news/article/disable , POST /mypage/admin/news/article/disable/{id}
 * 管理権限（PreAuthorize）により、画面表示・更新操作を制御する。
 */
@Controller
public class NewsAdminArticleController {

    private static final Logger log = LoggerFactory.getLogger(NewsAdminArticleController.class);

    /**
     * ニュース情報レコード
     */
    public record NewsRow(UUID id, String publishedAt, String categoryName, String title, boolean enabled) {}

    // ログインユーザーサービスを保持
    private final UserAccountService userAccountService;
    // ニュース管理サービスを保持
    private final AdminNewsService adminNewsService;
    // ハッシュタグ管理サービスを保持
    private final HashtagAdminService hashtagAdminService;
    // ハッシュタグポートを保持
    private final NewsHashtagPort newsHashtagPort;

    // トランザクション演算子プロバイダ（DB側の DEFERRABLE 制約を満たすため、複数の書込みを1トランザクションにまとめる）
    private final ObjectProvider<TransactionalOperator> txProvider;
    // メッセージサービスを保持
    private final MessageSource messageSource;
    // 画面向けメッセージを保持
    private final UiMessageResolver uiMessageResolver;

    // コンストラクタ
    public NewsAdminArticleController(
        UserAccountService userAccountService,
        AdminNewsService adminNewsService,
        HashtagAdminService hashtagAdminService,
        NewsHashtagPort newsHashtagPort,
        ObjectProvider<TransactionalOperator> txProvider,
        UiMessageResolver uiMessageResolver,
        MessageSource messageSource
    ) {
        this.userAccountService = userAccountService;
        this.adminNewsService = adminNewsService;
        this.hashtagAdminService = hashtagAdminService;
        this.newsHashtagPort = newsHashtagPort;
        this.txProvider = txProvider;
        this.uiMessageResolver = uiMessageResolver;
        this.messageSource = messageSource;
    }

    /**
     * ニュース作成ページを表示
     */
    @PreAuthorize("hasAuthority('NEWS_ARTICLE_CREATE')")
    @GetMapping("/mypage/admin/news/article/create")
    public Mono<Rendering> newsArticleCreatePage(
        @RequestParam(name = "msg", required = false) String msg,
        @RequestParam(name = "err", required = false) String err,
        Authentication auth
    ) {
        // ログインユーザー名を取得
        String username = auth.getName();

        // ログインユーザー情報を取得
        Mono<AppUserEntity> meMono = userAccountService.getUser(username)
                // 取得できなかった場合は、最低限の情報を持つダミー情報で補完
                .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true));

        // カテゴリ一覧を取得
        Mono<List<CategoryEntity>> categoriesMono = adminNewsService.listCategoriesForAdmin()
                .filter(category -> Boolean.TRUE.equals(category.getEnabled()))
                .collectList();

        // ハッシュタグ一覧を取得
        Mono<List<HashtagEntity>> hashTagsMono = hashtagAdminService.listEnabled().collectList();

        // 各情報を付与して、ニュース作成管理画面を返却
        return Mono.zip(meMono, categoriesMono, hashTagsMono)
            .map(tuple ->
                    Rendering.view("mypage/admin_news_article_create")
                            .modelAttribute("msg", msg)
                            .modelAttribute("err", err)
                            .modelAttribute("me", tuple.getT1())
                            .modelAttribute("categories", tuple.getT2())
                            .modelAttribute("hashtags", tuple.getT3())
                            .build());
    }

    /**
     * ニュースを作成
     */
    @PreAuthorize("hasAuthority('NEWS_ARTICLE_CREATE')")
    @PostMapping(
        value = "/mypage/admin/news/article/create",
        consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE }
    )
    public Mono<Rendering> createNews(ServerWebExchange exchange) {

        // パラメータを読取
        return NewsAdminSupport.readParams(exchange)
            .flatMap(form -> {

                // ログに出力
                log.info(Messages.getMsg("log.news.article.create"),
                        // レスポンスヘッダーを取得
                        exchange.getRequest().getHeaders().getContentType(),
                        // キー一覧を出力
                        NewsAdminSupport.safeKeys(form)
                );

                // カテゴリ ID を取得
                String categoryIdStr = NewsAdminSupport.firstNonBlank(form, "categoryId");
                // ニュースタイトルを取得
                String title = NewsAdminSupport.firstNonBlank(form, "title", "headline", "subject");
                // ニュース本文を取得
                String body = NewsAdminSupport.firstNonBlank(form, "body", "content", "text");

                // カテゴリ ID が取得できなかった場合
                if (categoryIdStr == null || categoryIdStr.isBlank()) {
                    // エラーメッセージを付与して、ニュース作成管理画面へリダイレクトを返却
                    return Mono.just(NewsAdminSupport.redirectErr(
                            "/mypage/admin/news/article/create",
                            messageSource.getMessage(
                                    "news.ctrl.err.category.select",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }
                // ニュースタイトルが取得できなかった場合
                if (title == null || title.isBlank()) {
                    // エラーメッセージを付与して、ニュース作成管理画面へリダイレクトを返却
                    return Mono.just(NewsAdminSupport.redirectErr(
                            "/mypage/admin/news/article/create",
                            messageSource.getMessage(
                                    "news.article.ctrl.err.title.required",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }
                // ニュースタイトルが取得できなかった場合
                if (body == null || body.isBlank()) {
                    // エラーメッセージを付与して、ニュース作成管理画面へリダイレクトを返却
                    return Mono.just(NewsAdminSupport.redirectErr(
                            "/mypage/admin/news/article/create",
                            messageSource.getMessage(
                                    "news.article.ctrl.err.body.required",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }

                // カテゴリ ID
                UUID categoryId;
                try {
                    // カテゴリ ID を取得
                    categoryId = UUID.fromString(categoryIdStr.trim());
                } catch (Exception exception) {
                    // エラーメッセージを付与して、ニュース作成管理画面へリダイレクトを返却
                    return Mono.just(NewsAdminSupport.redirectErr(
                            "/mypage/admin/news/article/create",
                            messageSource.getMessage(
                                    "news.article.ctrl.err.category.invalid_id",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }

                // ハッシュタグを取得
                List<String> hashTagIdsStr = form.getOrDefault("hashtagIds", List.of());
                List<UUID> hashTagIds = new java.util.ArrayList<>();

                // ハッシュタグを全件ループ処理
                for (String hashTag : hashTagIdsStr) {
                    try {
                        // ハッシュタグ一覧に追加
                        hashTagIds.add(UUID.fromString(hashTag));
                    } catch (Exception ignore) {
                        // 採用しない
                    }
                }

                // ハッシュタグ一覧が取得できなかった場合
                if (hashTagIds.isEmpty()) {
                    // エラーメッセージを返却
                    return Mono.error(new AppMessageException("news.hashtag.err.required", 1));
                }

                // ハッシュタグ一覧が上限数（5件）を超える場合
                if (hashTagIds.size() > 5) {
                    // エラーメッセージを返却
                    return Mono.error(new AppMessageException("news.hashtag.err.too_many", 5));
                }

                // 選択されたハッシュタグ識別子が「全て、登録済みかつ有効」であることを検証
                return hashtagAdminService.validateEnabledHashtagIds(hashTagIds)

                        .flatMap(validIds -> {
                                Mono<NewsEntity> write =
                                        adminNewsService.createNews(categoryId, title.trim(), body.trim())
                                                .flatMap(created -> newsHashtagPort.replaceNewsHashtags(created.getId(), validIds)
                                                        .thenReturn(created));

                                // DB側で「news_content 必須」「タグ数 1〜5」「enabled=true のみ紐付け」を
                                // DEFERRABLE 制約（コミット時検証）で強制しているため、
                                // news 保存と news_hashtags 更新を同一トランザクションで確定させる。
                                TransactionalOperator tx = txProvider.getIfAvailable();
                                if (tx != null) {
                                    write = tx.transactional(write);
                                }
                                return write;
                            })

                        // 成功した場合、成功メッセージを付与して、ニュース作成管理画面へリダイレクトを返却
                        .thenReturn(NewsAdminSupport.redirectOk(
                                "/mypage/admin/news/article/create",
                                messageSource.getMessage(
                                        "news.article.create.msg.created",
                                        null,
                                        exchange.getLocaleContext().getLocale()
                                ))
                        )

                        // 例外が発生した場合、エラーメッセージを付与して、ニュース作成管理画面へリダイレクトを返却
                        .onErrorResume(ex ->
                                Mono.just(NewsAdminSupport.redirectErr(
                                        "/mypage/admin/news/article/create",
                                        uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale()
                                        )
                                ))
                        );
            });
    }

    /**
     * ニュース記事有効フラグ管理ページを表示
     */
    @PreAuthorize("hasAuthority('NEWS_ARTICLE_DISABLE')")
    @GetMapping("/mypage/admin/news/article/disable")
    public Mono<Rendering> newsArticleDisablePage(
        @RequestParam(name = "msg", required = false) String msg,
        @RequestParam(name = "err", required = false) String err,
        Authentication auth,
        ServerWebExchange exchange
    ) {
        // ログインユーザー名を取得
        String username = auth.getName();

        // ログインユーザー情報を取得
        Mono<AppUserEntity> meMono = userAccountService.getUser(username)
                // 取得結果が空の場合は既定値を返す
                .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true));

        // カテゴリ一覧を取得
        Mono<List<CategoryEntity>> categoriesMono = adminNewsService.listCategoriesForAdmin().collectList();

        // ニュース一覧を取得
        Mono<List<NewsEntity>> newsMono = adminNewsService.listNewsForAdmin().collectList();

        // ニュース情報をまとめる
        return Mono.zip(meMono, categoriesMono, newsMono)
            .map(tuple -> {

                // ユーザー情報一覧を取得
                AppUserEntity me = tuple.getT1();
                // カテゴリ一覧を取得
                List<CategoryEntity> categories = tuple.getT2();
                // ニュース一覧を取得
                List<NewsEntity> news = tuple.getT3();

                // カテゴリ名 ID を取得
                Map<UUID, String> categoryNameById = NewsAdminSupport.toCategoryNameMap(categories);

                // ニュースの各種情報をリスト化
                List<NewsRow> rows = news.stream()

                        // null を除外
                        .filter(Objects::nonNull)

                        // ニュースの各種情報を取得
                        .map(newsEntity -> new NewsRow(

                                // ニュース ID を取得
                                newsEntity.getId(),

                                // ニュース公開時刻を取得
                                NewsAdminSupport.fmtDateTime(newsEntity.getPublishedAt()),

                                // カテゴリ名 ID を取得
                                categoryNameById.getOrDefault(
                                        newsEntity.getCategoryId(),
                                        messageSource.getMessage(
                                                "news.ui.unknown",
                                                null,
                                                exchange.getLocaleContext().getLocale()
                                        )
                                ),

                                // ニュースタイトルを取得
                                newsEntity.getTitle(),

                                // ニュースが有効かどうかを判定
                                Boolean.TRUE.equals(newsEntity.getEnabled())
                                )
                        ).collect(Collectors.toList());

                // 各情報を付与して、ニュース記事有効フラグ管理画面を返却
                return Rendering.view("mypage/admin_news_article_disable")
                        .modelAttribute("msg", msg)
                        .modelAttribute("err", err)
                        .modelAttribute("me", me)
                        .modelAttribute("rows", rows)
                        .build();
            })
                // 例外が起きた場合
                .onErrorResume(ex -> {

                    // ログに記録する
                    log.error(Messages.getMsg("log.news.article.disable.unexpected"), ex);

                    // 各情報を付与して、ニュース記事有効フラグ管理画面を返却
                    return meMono.map(me ->
                            Rendering.view("mypage/admin_news_article_disable")
                                    .modelAttribute("msg", msg)
                                    .modelAttribute("err", (err != null ? err : uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale())))
                                    .modelAttribute("me", me)
                                    .modelAttribute("rows", java.util.List.of())
                                    .build()
                    );
                });
    }

    /**
     * ニュース有効フラグを切替
     */
    @PreAuthorize("hasAuthority('NEWS_ARTICLE_DISABLE')")
    @PostMapping("/mypage/admin/news/article/disable/{id}")
    public Mono<Rendering> toggleNewsEnabled(@PathVariable("id") UUID newsId, ServerWebExchange exchange) {

        // ニュース一覧を取得
        return adminNewsService.listNewsForAdmin()

                // ニュース ID が一致する場合
                .filter(newsEntity -> newsId.equals(newsEntity.getId()))

                // 要素が1件ならその要素で返却。0件なら空で返却。2件以上ならエラーで返却。
                .singleOrEmpty()

                // ニュース情報が取得できなかった場合、指定メッセージコードで例外発生
                .switchIfEmpty(Mono.error(new AppMessageException("news.article.disable.err.not_found")))

                // ニュース情報を使って、有効フラグを設定
                .flatMap(newsEntity ->
                        adminNewsService.setNewsEnabled(
                                newsId,
                                !Boolean.TRUE.equals(newsEntity.getEnabled()
                                )
                        )
                )

                // 成功した場合、成功メッセージを付与して、ニュース有効フラグ管理画面へリダイレクトを返却
                .thenReturn(NewsAdminSupport.redirectOk(
                        "/mypage/admin/news/article/disable",
                        messageSource.getMessage(
                                "news.article.disable.msg.updated",
                                null,
                                exchange.getLocaleContext().getLocale()
                        )
                ))

                // 例外が発生した場合、エラーメッセージを付与して、ニュース有効フラグ管理画面へリダイレクトを返却
                .onErrorResume(ex ->
                        Mono.just(NewsAdminSupport.redirectErr(
                                "/mypage/admin/news/article/disable",
                                uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale()
                                )
                        ))
                );
    }
}
