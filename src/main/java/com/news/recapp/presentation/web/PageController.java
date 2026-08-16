package com.news.recapp.presentation.web;

import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.port.out.FeedbackPort;
import com.news.recapp.application.service.FavoriteService;
import com.news.recapp.application.service.NewsService;
import com.news.recapp.application.service.RecService;
import com.news.recapp.persistence.entity.CategoryEntity;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ページ表示（HTMLレンダリング）を扱うコントローラ
 * - トップページ（/）や記事詳細（/news/{id}）を表示する
 * - ページ内の「おすすめ」表示は {@link com.news.recapp.application.service.RecService} を利用する
 * ※ 推薦スコアの詳細（特徴量/計算式/ONNX 推論の扱い）は {@link com.news.recapp.presentation.web.RecController} のクラスコメントも参照。
 */
@Controller
public class PageController {

    // ニュースサービスを保持
    private final NewsService newsService;
    // 推薦サービスを保持
    private final RecService recService;
    // お気に入りサービスを保持
    private final FavoriteService favoriteService;
    // フィードバック参照ポートを保持
    private final FeedbackPort feedbackPort;
    // メッセージサービスを保持
    private final MessageSource messageSource;

    // コンストラクタ
    public PageController(
            NewsService newsService,
            RecService recService,
            FavoriteService favoriteService,
            FeedbackPort feedbackPort,
            MessageSource messageSource
    ) {
        this.newsService = newsService;
        this.recService = recService;
        this.favoriteService = favoriteService;
        this.feedbackPort = feedbackPort;
        this.messageSource = messageSource;
    }

    /**
     * トップページ（/）を表示
     * - ログイン状態から userId を決定し、おすすめ記事（/rec 相当）を取得して画面モデルに詰める
     * - 中央のニュース一覧は、hashtag / category / all（全件）を切り替えて表示する
     */
    @GetMapping("/")
    public Mono<Rendering> index(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "hashtag", required = false) String hashtag,
            @RequestParam(name = "ab", required = false) String abTest,
            Principal principal,
            ServerWebExchange exchange
    ) {

        // ログインユーザー名を取得
        // 未指定の場合、"anonymous" として扱う
        String userId = (principal == null) ? "anonymous" : principal.getName();

        // カテゴリ一覧を取得
        Mono<List<ApiDtos.Category>> categoriesMono = newsService.listCategories().collectList();

        // URL パラメータの揺れ（前後空白・先頭 #/＃）を吸収
        final String normalizedHashtag = normalizeHashtagName(hashtag);

        // 表示対象の特定
        boolean isHashtag = (normalizedHashtag != null);
        boolean isAll = "all".equalsIgnoreCase(category);

        // 変数定義
        Mono<List<ApiDtos.NewsSummary>> centerNewsMono;
        Mono<String> centerTitleMono;
        String centerSource;
        String selectedCategory;

        // 表示対象（中央）を決定（hashtag > category > all）
        if (isHashtag) {

            // ハッシュタグに紐づくニュースを取得（全件）
            centerNewsMono = newsService.allNewsByHashtagName(normalizedHashtag).collectList();

            // 表示タイトルを生成
            centerTitleMono = Mono.just("#" + normalizedHashtag);

            // 表示ソース（画面判定用）
            centerSource = "hashtag";

            // 選択カテゴリ（未選択）
            selectedCategory = "";

        } else {

            // カテゴリの特定
            boolean isCategoryMissing = (category == null || category.isBlank());

            // トップページ（カテゴリ未指定）の場合
            if (isCategoryMissing) {

                // 最新ニュース一覧を取得
                centerNewsMono = newsService.latestNews().collectList();

                // トップページ（カテゴリ未指定）は「最新ニュース」として表示
                centerTitleMono = Mono.just(messageSource.getMessage(
                        "ui.index.001",
                        null,
                        exchange.getLocaleContext().getLocale()
                ));

                // 表示ソース（画面判定用）
                centerSource = "all";

                // 選択カテゴリ（未選択）
                selectedCategory = "";

            // category が all の場合
            } else if (isAll) {

                // 全件ニュース一覧を取得
                centerNewsMono = newsService.allNews().collectList();

                // 表示タイトル（全件）
                centerTitleMono =
                        Mono.just(
                                messageSource.getMessage(
                                        "page.center.all",
                                        null,
                                        exchange.getLocaleContext().getLocale()
                                )
                        );

                // 表示ソース（画面判定用）
                centerSource = "all";

                // 選択カテゴリ（all）
                selectedCategory = "all";

            // それ以外（カテゴリ指定）の場合
            } else {

                // カテゴリに紐づく最新ニュース一覧を取得
                centerNewsMono = newsService.allNewsByCategorySlug(category).collectList();

                // カテゴリ名を取得して表示タイトルに使用
                centerTitleMono = newsService.getCategoryBySlug(category)
                        .map(CategoryEntity::getName)
                        // 取得できない場合、エラーメッセージを付与
                        .switchIfEmpty(Mono.just(messageSource.getMessage(
                                "page.center.unknown_category",
                                null,
                                exchange.getLocaleContext().getLocale()
                        )));

                // 表示ソース（画面判定用）
                centerSource = "category";

                // 選択カテゴリ
                selectedCategory = category;
            }
        }

        // おすすめ記事を取得（表示件数は application.yml の rec.default-k で制御）
        Mono<ApiDtos.RecResponse> recMono = recService.recommend(userId, abTest, null);

        // お気に入り一覧を取得（ID 一覧 → 概要取得）
        Mono<List<ApiDtos.NewsSummary>> favoritesMono = favoriteService.listFavoriteNewsIds(userId)
                .concatMap(newsService::getNewsSummary)
                .collectList();

        // 各情報をまとめて取得し付与して、トップページを返却
        return Mono.zip(categoriesMono, centerNewsMono, centerTitleMono, recMono, favoritesMono)
                .map(tuple -> {

                    // カテゴリ一覧を取得
                    List<ApiDtos.Category> categories = tuple.getT1();

                    // カテゴリ ID をキーにしてカテゴリ名を対応付けた Map を生成
                    Map<String, String> categoryNameById = categories.stream()
                            .collect(Collectors.toMap(
                                    categoryDto -> categoryDto.id().toString(),
                                    ApiDtos.Category::name
                            ));

                    // 各情報を付与して、トップページを返却
                    return Rendering.view("index")
                            .modelAttribute("categories", categories)
                            .modelAttribute("categoryNameById", categoryNameById)
                            .modelAttribute("centerNews", tuple.getT2())
                            .modelAttribute("centerTitle", tuple.getT3())
                            .modelAttribute("centerSource", centerSource)
                            .modelAttribute("selectedCategory", selectedCategory)
                            .modelAttribute("rec", tuple.getT4().items())
                            .modelAttribute("favorites", tuple.getT5())
                            .build();
                });
    }

    /**
     * ニュース記事詳細ページ（/news/{id}）を表示
     * - 対象記事の取得に加え、おすすめ記事・お気に入り情報などを取得して画面モデルに詰める
     */
    @GetMapping("/news/{id}")
    public Mono<Rendering> news(
            @PathVariable("id") UUID id,
            @RequestParam(name = "ab", required = false) String abTest,
            Principal principal
    ) {

        // ログインユーザー名を取得
        // 未指定の場合、"anonymous" として扱う
        String userId = (principal == null) ? "anonymous" : principal.getName();

        // ログイン状態を判定
        // userId が "anonymous" の場合、未ログインとして扱う
        boolean isLoggedIn = principal != null && userId != null && !userId.isBlank() && !"anonymous".equalsIgnoreCase(userId);

        // ログイン中のみ、対象ニュースへのリアクション種別を取得
        Mono<String> userReactionMono = isLoggedIn
                ? feedbackPort.findReactionType(userId, id)
                        // 取得できなかった場合、空文字として補完
                        .defaultIfEmpty("")
                : Mono.just("");

        // カテゴリ一覧を取得
        Mono<List<ApiDtos.Category>> categoriesMono = newsService.listCategories()
                .collectList();

        // ニュース詳細を取得
        Mono<ApiDtos.NewsDetail> newsMono = newsService.getNews(id);

        // おすすめ記事を取得（表示件数は application.yml の rec.default-k で制御）
        Mono<ApiDtos.RecResponse> recMono = recService.recommend(userId, abTest, null);

        // お気に入り状態を取得
        Mono<Boolean> isFavMono = favoriteService.isFavorite(userId, id);

        // お気に入り一覧を取得（ID 一覧 → 概要取得）
        Mono<List<ApiDtos.NewsSummary>> favoritesMono = favoriteService.listFavoriteNewsIds(userId)
                .concatMap(newsService::getNewsSummary)
                .collectList();

        // 各情報をまとめて取得し付与して、ニュース記事詳細ページを返却
        return Mono.zip(categoriesMono, newsMono, recMono, isFavMono, favoritesMono, userReactionMono)
                .map(tuple -> {

                    // カテゴリ一覧を取得
                    List<ApiDtos.Category> categories = tuple.getT1();

                    // カテゴリ ID をキーにしてカテゴリ名を対応付けた Map を生成
                    Map<String, String> categoryNameById = categories.stream()
                            .collect(Collectors.toMap(
                                    categoryDto -> categoryDto.id().toString(),
                                    ApiDtos.Category::name
                            ));

                    // 各情報を付与して、ニュース記事詳細ページを返却
                    return Rendering.view("news")
                            .modelAttribute("categories", categories)
                            .modelAttribute("categoryNameById", categoryNameById)
                            .modelAttribute("favorites", tuple.getT5())
                            .modelAttribute("userReaction", tuple.getT6())
                            .modelAttribute("selectedCategory", "")
                            .modelAttribute("news", tuple.getT2())
                            .modelAttribute("rec", tuple.getT3().items())
                            .modelAttribute("isFavorite", tuple.getT4())
                            .modelAttribute("isLoggedIn", isLoggedIn)
                            .build();
                });
    }

    /**
     * ハッシュタグ名を正規化（前後空白除去・先頭の #/＃ を除去）
     */
    private static String normalizeHashtagName(String hashtag) {

        // ハッシュタグ名が null の場合
        if (hashtag == null) {
            // null の返却
            return null;
        }

        // ハッシュタグ名をトリム
        String name = hashtag.trim();

        // ハッシュタグ名が空の場合
        if (name.isEmpty()) {
            // null の返却
            return null;
        }

        // 先頭が # / ＃ の場合
        while (!name.isEmpty() && (name.charAt(0) == '#' || name.charAt(0) == '＃')) {
            // 先頭の # / ＃ をトリム
            name = name.substring(1).trim();
        }

        // ハッシュタグ名の返却
        return name.isEmpty() ? null : name;
    }
}
