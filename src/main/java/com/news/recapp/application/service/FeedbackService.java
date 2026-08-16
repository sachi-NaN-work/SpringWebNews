package com.news.recapp.application.service;

import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.port.out.FeedbackEventPublisherPort;
import com.news.recapp.application.port.out.FeedbackPort;
import com.news.recapp.application.port.out.NewsHashtagPort;
import com.news.recapp.util.Messages;
import com.news.recapp.persistence.entity.FeedbackEntity;
import com.news.recapp.persistence.entity.HashtagEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * フィードバックの業務処理を提供
 */
@Service
public class FeedbackService {

    // ロガーを保持
    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    // ニュースサービスを保持
    private final NewsService newsService;
    // フィードバックを保持
    private final FeedbackPort feedbackPort;
    // ユーザープロフィールサービスを保持
    private final UserProfileService userProfileService;
    // レコメンド信号サービスを保持
    private final RecSignalService recSignalService;
    // ニュース記事ハッシュタグを保持
    private final NewsHashtagPort newsHashtagPort;
    // イベント送信サービスを保持
    private final FeedbackEventPublisherPort publisher;
    // 重複判定サービスを保持
    private final FeedbackDedupeService dedupeService;

    // コンストラクタ
    public FeedbackService(
        NewsService newsService,
        FeedbackPort feedbackPort,
        UserProfileService userProfileService,
        RecSignalService recSignalService,
        NewsHashtagPort newsHashtagPort,
        FeedbackEventPublisherPort publisher,
        FeedbackDedupeService dedupeService
    ) {
        this.newsService = newsService;
        this.feedbackPort = feedbackPort;
        this.userProfileService = userProfileService;
        this.recSignalService = recSignalService;
        this.newsHashtagPort = newsHashtagPort;
        this.publisher = publisher;
        this.dedupeService = dedupeService;
    }

    /**
     * フィードバックを記録
     */
    public Mono<ApiDtos.FeedbackResponse> recordFeedback(
            String userId,
            ApiDtos.FeedbackRequest req
    ) {

        // ユーザー ID が取得できなかった場合
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId)) {
            // エラーメッセージを付与して返却
            return Mono.error(
                    new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "login required"
                    )
            );
        }

        // リクエストのフィードバック種別
        String type = (req.feedbackType() == null || req.feedbackType().isBlank())
                ? "click"
                : req.feedbackType().toLowerCase();

        // リクエストのリクエスト元
        String source = (req.source() == null || req.source().isBlank())
                ? "unknown"
                : req.source().toLowerCase();

        // フィードバック種別を判定
        boolean isReaction = "good".equals(type) || "bad".equals(type) || "reaction_clear".equals(type);

        // フィードバックのキー重複判定
        Mono<Boolean> dedupeResultMono = isReaction
                ? Mono.just(true)
                : dedupeService.shouldProcess(userId, req.newsId(), type, source, req.eventId());

        // フィードバックのキー重複判定に応じて返却
        return dedupeResultMono.flatMap(shouldProcess -> {

            // キー重複の場合
            if (!shouldProcess) {
                // 重複レスポンスを返却
                return Mono.just(new ApiDtos.FeedbackResponse("dup"));
            }

            // ニュース ID からニュース記事を取得
            return newsService.getNewsEntity(req.newsId())
                    // 値が取得できなかった場合、エラーメッセージを付与して返却
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("news not found")))
                    // フィードバックを保存
                    .flatMap(news ->
                            saveFeedback(
                                    userId,
                                    news,
                                    type,
                                    source,
                                    req.eventId(),
                                    req.requestId()
                            )
                    )
                    // フィードバックレスポンス成功を返却
                    .thenReturn(new ApiDtos.FeedbackResponse("ok"));
        });
    }

    /**
     * フィードバックを保存
     */
    private Mono<Void> saveFeedback(
            String userId,
            NewsEntity news,
            String type,
            String source,
            String eventId,
            String requestId
    ) {

        // ニュース ID を取得
        UUID newsId = news.getId();
        // ニュース ID が取得できなかった場合
        if (newsId == null) {
            // エラーメッセージを付与して返却
            return Mono.error(new IllegalStateException("news id is null"));
        }

        // リクエストのフィードバック種別
        String baseType = (type == null || type.isBlank())
                ? "click"
                : type.toLowerCase();

        // リクエストのリクエスト元
        String src = (source == null || source.isBlank())
                ? "unknown"
                : source.toLowerCase();

        // 増減値を算出
        long delta = "unfavorite".equals(baseType) ? -1L : 1L;

        // リクエストのフィードバック種別
        String feedbackType = ("good".equals(baseType) || "bad".equals(baseType) || "reaction_clear".equals(baseType))
                ? baseType
                : baseType + ":" + src;

        // フィードバック情報のオブジェクト
        FeedbackEntity feedbackEntity = new FeedbackEntity(
                null,
                userId,
                newsId,
                news.getCategoryId(),
                feedbackType,
                OffsetDateTime.now()
        );

        // ニュース ID に紐づく有効なハッシュタグ一覧を取得
        Mono<Void> recSignalsMono =
                newsHashtagPort.findEnabledHashtags(newsId)
                        // ハッシュタグ ID を取得
                        .map(HashtagEntity::getId)
                        // 一覧へ集約
                        .collectList()
                        // 取得結果をもとに処理を実行
                        .flatMap(tagIds -> {

                            // 有効閲覧(view) の場合はリアルタイム推薦用のシグナルを更新
                            if ("view".equals(baseType)) {
                                return recSignalService.recordView(userId, news, tagIds);
                            }

                            // リクエストのフィードバック種別によって、処理を分岐して返却
                            return switch (baseType) {

                                // フィードバック種別が お気に入り の場合
                                case "favorite" ->
                                        recSignalService.recordFavoriteToggle(
                                                userId,
                                                news,
                                                tagIds,
                                                true
                                        );

                                // フィードバック種別が お気に入り解除 の場合
                                case "unfavorite" ->
                                        recSignalService.recordFavoriteToggle(
                                                userId,
                                                news,
                                                tagIds,
                                                false
                                        );

                                // フィードバック種別が 高評価 または 低評価 の場合
                                case "good", "bad" ->
                                        feedbackPort.findReactionType(userId, newsId)
                                                // 値なしの場合、デフォルト値で補完
                                                .defaultIfEmpty("")
                                                // 取得結果をもとに処理を実行
                                                .flatMap(prev ->
                                                        recSignalService.recordReactionChange(
                                                                userId,
                                                                news,
                                                                tagIds,
                                                                prev,
                                                                baseType
                                                        )
                                                );

                                // フィードバック種別が 反応解除 の場合
                                case "reaction_clear" ->
                                        feedbackPort.findReactionType(userId, newsId)
                                                // 値なしの場合、デフォルト値で補完
                                                .defaultIfEmpty("")
                                                // 取得結果をもとに処理を実行
                                                .flatMap(prev ->
                                                        recSignalService.recordReactionChange(
                                                                userId,
                                                                news,
                                                                tagIds,
                                                                prev,
                                                                ""
                                                        )
                                                );

                                // フィードバック種別が 上記以外 の場合
                                default ->
                                        // 値なしで返却
                                        Mono.empty();

                            };
                        })
                        // 例外が発生した場合、警告ログを出力
                        .doOnError(ex ->
                                log.warn(
                                        Messages.getMsg("log.feedback.update_rec_signals_failed"),
                                        ex.toString()
                                )
                        )
                        // 例外が発生した場合、スキップ
                        .onErrorResume(ex -> Mono.empty());

        // カテゴリ別カウントを加算
        Mono<Void> profileUpdateMono =
                userProfileService.incrementCategory(userId, news.getCategoryId(), delta)
                        // 例外が発生した場合、警告ログを出力
                        .doOnError(ex ->
                                log.warn(
                                        Messages.getMsg("log.feedback.update_profile_failed"),
                                        ex.toString()
                                )
                        )
                        // 例外が発生した場合、スキップ
                        .onErrorResume(ex -> Mono.empty())
                        // 値なしで返却
                        .then();

        // Kafka へフィードバックイベントを送信
        Mono<Void> kafkaMono =
                publisher.publishKafka(
                        userId,
                        newsId.toString(),
                        (news.getCategoryId() == null) ? "" : news.getCategoryId().toString(),
                        baseType,
                        src,
                        eventId,
                        requestId
                )
                        // 例外が発生した場合、警告ログを出力
                        .doOnError(ex ->
                                log.warn(
                                        Messages.getMsg("log.kafka.feedback.publish_failed"),
                                        ex.toString()
                                )
                        )
                        // 例外が発生した場合、スキップ
                        .onErrorResume(ex -> Mono.empty());

        // フィードバック情報を保存
        Mono<Void> saveFeedbackMono =
                feedbackPort.save(feedbackEntity)
                        // 例外発生時に警告ログを出力
                        .doOnError(ex ->
                                log.warn(
                                        Messages.getMsg("log.feedback.save_failed"),
                                        ex.toString()
                                )
                        )
                        // 値なしで返却
                        .then();

        // 各フィードバック情報を処理して返却
        return recSignalsMono
                // ユーザープロファイルを更新
                .then(profileUpdateMono)
                // Kafka へイベントを送信
                .then(kafkaMono)
                // フィードバック情報を保存
                .then(saveFeedbackMono);
    }
}
