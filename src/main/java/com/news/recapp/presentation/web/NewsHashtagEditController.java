package com.news.recapp.presentation.web;

import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.exception.AppMessageException;
import com.news.recapp.application.port.out.NewsHashtagPort;
import com.news.recapp.application.service.AdminNewsService;
import com.news.recapp.application.service.HashtagAdminService;
import com.news.recapp.application.service.NewsService;
import com.news.recapp.application.service.UserAccountService;
import com.news.recapp.persistence.entity.AppUserEntity;
import com.news.recapp.persistence.entity.HashtagEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ニュース記事のハッシュタグ編集を扱うコントローラ
 * - ハッシュタグ編集管理画面: GET /mypage/admin/news/hashtag/edit , GET /mypage/admin/news/{id}/hashtag/edit
 * - 更新: POST /mypage/admin/news/{id}/hashtag/edit（フォーム送信）
 * ニュースとハッシュタグの関連（紐づけ）を更新し、結果メッセージを画面に返す。
 */
@Controller
public class NewsHashtagEditController {

    // ログインユーザーサービスを保持
    private final UserAccountService userAccountService;
    // 管理者向けニュースサービスを保持
    private final AdminNewsService adminNewsService;
    // ニュースサービスを保持
    private final NewsService newsService;
    // ハッシュタグ管理サービスを保持
    private final HashtagAdminService hashtagAdminService;
    // ニュースとハッシュタグ関連のポートを保持
    private final NewsHashtagPort newsHashtagPort;
    // 例外メッセージサービスを保持
    private final UiMessageResolver uiMessageResolver;
    // メッセージサービスを保持
    private final MessageSource messageSource;

    // コンストラクタ
    public NewsHashtagEditController(
            UserAccountService userAccountService,
            AdminNewsService adminNewsService,
            NewsService newsService,
            HashtagAdminService hashtagAdminService,
            NewsHashtagPort newsHashtagPort,
            UiMessageResolver uiMessageResolver,
            MessageSource messageSource
    ) {
        this.userAccountService = userAccountService;
        this.adminNewsService = adminNewsService;
        this.newsService = newsService;
        this.hashtagAdminService = hashtagAdminService;
        this.newsHashtagPort = newsHashtagPort;
        this.uiMessageResolver = uiMessageResolver;
        this.messageSource = messageSource;
    }

    /**
     * ニュース選択一覧を表示
     */
    @PreAuthorize("hasAuthority('NEWS_ARTICLE_HASHTAG_EDIT')")
    @GetMapping("/mypage/admin/news/hashtag/edit")
    public Mono<Rendering> listPage(
            @RequestParam(name = "msg", required = false) String msg,
            @RequestParam(name = "err", required = false) String err,
            Authentication auth
    ) {

        // ログインユーザー名を取得
        String username = auth.getName();

        // ユーザー情報を取得
        Mono<AppUserEntity> meMono = userAccountService.getUser(username)
                // 取得できなかった場合、最低限のダミー情報で補完
                .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true));

        // ニュース一覧を取得
        Mono<List<NewsEntity>> newsListMono = adminNewsService.listNewsForAdmin()
                .collectList();

        // 各情報を付与して、ハッシュタグ編集管理画面（ニュース選択一覧）を返却
        return Mono.zip(meMono, newsListMono)
                .map(tuple ->
                        Rendering.view("mypage/admin_news_hashtag_edit_list")
                                .modelAttribute("msg", msg)
                                .modelAttribute("err", err)
                                .modelAttribute("me", tuple.getT1())
                                .modelAttribute("newsList", tuple.getT2())
                                .build()
                )
                // 例外が発生した場合、エラーメッセージを付与して、ハッシュタグ編集管理画面（ニュース選択一覧）へリダイレクトを返却
                .onErrorResume(ex ->
                        Mono.just(
                                NewsAdminSupport.redirectErr(
                                        "/mypage/admin/news/hashtag/edit",
                                        uiMessageResolver.resolve(ex, java.util.Locale.JAPAN)
                                )
                        )
                );
    }

    /**
     * 特定ニュースのハッシュタグ編集管理画面を表示
     */
    @PreAuthorize("hasAuthority('NEWS_ARTICLE_HASHTAG_EDIT')")
    @GetMapping("/mypage/admin/news/{id}/hashtag/edit")
    public Mono<Rendering> editPage(
            @PathVariable("id") UUID newsId,
            @RequestParam(name = "msg", required = false) String msg,
            @RequestParam(name = "err", required = false) String err,
            Authentication auth
    ) {

        // ログインユーザー名を取得
        String username = auth.getName();

        // ユーザー情報を取得
        Mono<AppUserEntity> meMono = userAccountService.getUser(username)
                // 取得できなかった場合、最低限のダミー情報で補完
                .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true));

        // 対象ニュースを取得
        Mono<NewsEntity> newsMono = newsService.getNewsEntity(newsId)
                // 取得できない場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.error(new AppMessageException("news.hashtag.edit.err.news_not_found")));

        // 有効なハッシュタグ一覧を取得
        Mono<List<HashtagEntity>> allEnabledTagsMono = hashtagAdminService.listEnabled()
                .collectList();

        // 既に紐づいているハッシュタグ ID 一覧を取得
        Mono<Set<UUID>> selectedIdsMono = newsHashtagPort.findEnabledHashtags(newsId)
                .map(HashtagEntity::getId)
                .collect(Collectors.toSet());

        // 各情報をまとめて取得し付与して、ハッシュタグ編集管理画面を返却
        return Mono.zip(meMono, newsMono, allEnabledTagsMono, selectedIdsMono)
                .map(tuple -> {

                    // ハッシュタグ一覧を取得
                    List<ApiDtos.Hashtag> allTags = tuple.getT3()
                            .stream()
                            .map(hashtag -> new ApiDtos.Hashtag(hashtag.getId(), hashtag.getName()))
                            .toList();

                    // 選択済み ID 一覧を取得
                    Set<UUID> selectedIds = tuple.getT4();

                    // 各情報を付与して、ハッシュタグ編集管理画面を返却
                    return Rendering.view("mypage/admin_news_hashtag_edit")
                            .modelAttribute("msg", msg)
                            .modelAttribute("err", err)
                            .modelAttribute("me", tuple.getT1())
                            .modelAttribute("news", tuple.getT2())
                            .modelAttribute("allTags", allTags)
                            .modelAttribute("selectedIds", selectedIds)
                            .build();
                })
                // 例外が発生した場合、エラーメッセージを付与して、ハッシュタグ編集管理画面（ニュース選択一覧）へリダイレクトを返却
                .onErrorResume(ex ->
                        Mono.just(
                                NewsAdminSupport.redirectErr(
                                        "/mypage/admin/news/hashtag/edit",
                                        uiMessageResolver.resolve(ex, java.util.Locale.JAPAN)
                                )
                        )
                );
    }

    /**
     * 特定ニュースのタグを更新
     */
    @PreAuthorize("hasAuthority('NEWS_ARTICLE_HASHTAG_EDIT')")
    @PostMapping(
            value = "/mypage/admin/news/{id}/hashtag/edit",
            consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE }
    )
    public Mono<Rendering> save(
            @PathVariable("id") UUID newsId,
            ServerWebExchange exchange
    ) {

        // フォームパラメータを読取
        return NewsAdminSupport.readParams(exchange)
                .flatMap(form -> {

                    // 選択されたハッシュタグ ID（文字列）を取得
                    List<String> rawIds = form.getOrDefault("hashtagIds", List.of());

                    // 文字列の UUID を UUID 型に変換（変換できない値は除外）
                    List<UUID> ids = new ArrayList<>();
                    for (String text : rawIds) {
                        try {
                            ids.add(UUID.fromString(text));
                        } catch (Exception ignore) {
                            // 変換できない内容は採用しない
                        }
                    }

                    // ID が1件も指定されていない場合
                    if (ids.isEmpty()) {
                        return Mono.error(new AppMessageException("news.hashtag.err.required", 1));
                    }

                    // ID の指定数が 5 件を超える場合
                    if (ids.size() > 5) {
                        return Mono.error(new AppMessageException("news.hashtag.err.too_many", 5));
                    }

                    // 入力された ID が有効なハッシュタグかを検証し、ニュースとの関連を置き換え
                    return hashtagAdminService.validateEnabledHashtagIds(ids)
                            .flatMap(validIds -> newsHashtagPort.replaceNewsHashtags(newsId, validIds))

                            // 成功した場合、成功メッセージを付与して、ハッシュタグ編集管理画面へリダイレクトを返却
                            .thenReturn(
                                    NewsAdminSupport.redirectOk(
                                            "/mypage/admin/news/" + newsId + "/hashtag/edit",
                                            messageSource.getMessage(
                                                    "news.hashtag.edit.msg.updated",
                                                    null,
                                                    exchange.getLocaleContext().getLocale()
                                            )
                                    )
                            )

                            // 例外が発生した場合、エラーメッセージを付与して、ハッシュタグ編集管理画面へリダイレクトを返却
                            .onErrorResume(ex ->
                                    Mono.just(
                                            NewsAdminSupport.redirectErr(
                                                    "/mypage/admin/news/" + newsId + "/hashtag/edit",
                                                    uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale())
                                            )
                                    )
                            );
                });
    }
}
