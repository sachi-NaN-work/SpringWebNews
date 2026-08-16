package com.news.recapp.presentation.web;

import com.news.recapp.presentation.openapi.ApiOperationSummaries;
import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ニュース関連のAPIを提供するコントローラ
 * - ベースパスは /api
 * - JSONでカテゴリ一覧・最新ニュース一覧・ニュース詳細を返す
 */
@RestController
@RequestMapping("/api")
public class NewsApiController {

    // ニュースサービスを保持
    private final NewsService newsService;

    // コンストラクタ
    public NewsApiController(NewsService newsService) {
        this.newsService = newsService;
    }

    /**
     * カテゴリ一覧を取得
     * GET /api/categories
     */
    @Operation(summary = ApiOperationSummaries.NEWS_API_CATEGORIES)
    @GetMapping("/categories")
    public Flux<ApiDtos.Category> categories() {
        return newsService.listCategories();
    }

    /**
     * 最新ニュース一覧を取得
     * GET /api/news/latest
     */
    @Operation(summary = ApiOperationSummaries.NEWS_API_LATEST)
    @GetMapping("/news/latest")
    public Flux<ApiDtos.NewsSummary> latest() {
        return newsService.latestNews();
    }

    /**
     * ニュース詳細を取得
     * GET /api/news/{id}
     */
    @Operation(summary = ApiOperationSummaries.NEWS_API_GET)
    @GetMapping("/news/{id}")
    public Mono<ApiDtos.NewsDetail> get(@PathVariable("id") UUID id) {
        return newsService.getNews(id);
    }
}
