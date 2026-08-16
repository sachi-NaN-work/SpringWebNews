package com.news.recapp.application.service;

import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.metrics.RecMetrics;
import com.news.recapp.config.RecProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 推薦の業務処理を提供
 */
@Service
public class RecService {

    // 推薦候補の生成サービスを保持
    private final CandidateService candidateService;
    // 推薦順位の調整サービスを保持
    private final RerankService rerankService;
    // レコメンド信号サービスを保持
    private final RecSignalService recSignalService;
    // レコメンドメトリクスを保持
    private final RecMetrics recMetrics;
    // リアルタイム推薦の設定を保持
    private final RecProperties props;

    // A/B テスト判定を保持
    private final AbTestService abTestService;

    // コンストラクタ
    public RecService(
        CandidateService candidateService,
        RerankService rerankService,
        RecSignalService recSignalService,
        RecMetrics recMetrics,
        RecProperties props,
        AbTestService abTestService
    ) {
        this.candidateService = candidateService;
        this.rerankService = rerankService;
        this.recSignalService = recSignalService;
        this.recMetrics = recMetrics;
        this.props = props;
        this.abTestService = abTestService;
    }

    /**
     * ニュース推薦結果を取得
     */
    public Mono<ApiDtos.RecResponse> recommend(String userId, String ab, Integer requestedTopK) {

        // A/B テストグループを決定（優先順：userId強制 → リクエスト指定 → ハッシュ割当）
        String resolvedAb = abTestService.resolveAb(userId, ab);

        // 推薦上位件数
        final int topK;

        // 要求上位件数が未指定または 0 以下か判定
        boolean isKCondition = (requestedTopK == null || requestedTopK <= 0);

        // 要求上位件数が未指定または 0 以下の場合
        if (isKCondition) {
            // 推薦上位件数をデフォルト値に設定
            topK = props.getDefaultK();
        // 要求上位件数が指定されている場合
        } else {
            // 推薦上位件数に要求値を設定
            topK = requestedTopK;
        }

        // 処理開始時間
        final long start = System.nanoTime();

        // 個別/全体のシグナルをまとめて取得
        return recSignalService.loadSnapshot(userId)
                // 個別/全体の候補プールを取得
                .flatMap(snap -> candidateService.generatePools(userId, snap)
                        // リアルタイム推薦の調整
                        .flatMap(pools -> rerankService.rerank(userId, resolvedAb, topK, pools, snap)))
                // 取得したデータを整形
                .map(items -> {
                    // 推薦上位件数分のアイテムを抽出
                    List<ApiDtos.RecItem> top = items.stream().limit(topK).toList();
                    // 処理時間（ミリ秒）を算出
                    long tookMs = (System.nanoTime() - start) / 1_000_000L;
                    // 計測時間（ナノ秒）をメトリクスへ記録
                    recMetrics.recordNanos(System.nanoTime() - start);

                    // ニュース推薦結果を返却
                    return new ApiDtos.RecResponse(userId, resolvedAb, topK, tookMs, top);
                });
    }
}
