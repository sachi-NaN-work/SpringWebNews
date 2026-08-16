package com.news.recapp.data.kafka.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.recapp.application.port.out.FeedbackEventPublisherPort;
import com.news.recapp.config.FeedbackWeightProperties;
import com.news.recapp.util.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * フィードバックイベントを Kafka に送信するアダプタ
 */
@Component
public class KafkaFeedbackEventPublisherAdapter implements FeedbackEventPublisherPort {

    // ロガーを保持
    private static final Logger log =
            LoggerFactory.getLogger(KafkaFeedbackEventPublisherAdapter.class);

    // Kafka 送信用テンプレートを保持
    private final KafkaTemplate<String, String> kafkaTemplate;

    // JSON 変換用オブジェクトマッパーを保持
    private final ObjectMapper objectMapper;

    // フィードバック重み設定を保持
    private final FeedbackWeightProperties weightProps;

    // コンストラクタ
    public KafkaFeedbackEventPublisherAdapter(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            FeedbackWeightProperties weightProps
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.weightProps = weightProps;
    }

    /**
     * フィードバックイベントを Kafka に送信
     */
    @Override
    public Mono<Void> publishKafka(
            String userId,
            String newsId,
            String categoryId,
            String type,
            String source,
            String eventId,
            String requestId
    ) {

        // Kafka 送信処理を実行する Mono を返却
        return Mono.fromRunnable(() -> {

            try {
                // 出典に応じたフィードバック重みを算出
                double weight = weightProps.weightOf(source);

                // 送信ペイロードを JSON へ変換
                String payload = objectMapper.writeValueAsString(
                        Map.of(
                                // 送信ペイロードにユーザーIDを設定
                                "userId", userId,

                                // 送信ペイロードにニュースIDを設定
                                "newsId", newsId,

                                // 送信ペイロードにカテゴリIDを設定
                                "categoryId", categoryId,

                                // 送信ペイロードにフィードバック種別を設定
                                "type", type,

                                // 送信ペイロードに出典を設定
                                "source", source,

                                // 送信ペイロードに重みを設定
                                "weight", weight,

                                // 送信ペイロードにイベントIDを設定
                                "eventId", (eventId == null)
                                        ? ""
                                        : eventId,

                                // 送信ペイロードにリクエストIDを設定
                                "requestId", (requestId == null)
                                        ? ""
                                        : requestId,

                                // 送信ペイロードに発生時刻を設定
                                "ts", OffsetDateTime.now().toString()
                        )
                );

                // フィードバックイベントを Kafka に送信
                kafkaTemplate.send(
                                "feedback",
                                userId,
                                payload
                        )
                        // 非同期送信結果を確認
                        .whenComplete((result, ex) -> {

                            // 非同期送信に失敗した場合
                            if (ex != null) {

                                // 送信失敗を警告ログへ記録
                                log.warn(
                                        Messages.getMsg(
                                                "log.kafka.feedback.publish_failed"
                                        ),
                                        ex.toString()
                                );
                            }
                        });

                // 送信開始処理で例外が発生した場合
            } catch (Exception ex) {

                // 送信失敗を警告ログへ記録
                log.warn(
                        Messages.getMsg(
                                "log.kafka.feedback.publish_failed"
                        ),
                        ex.toString()
                );
            }
        });
    }
}