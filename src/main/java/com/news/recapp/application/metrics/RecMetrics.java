package com.news.recapp.application.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 推薦処理の遅延時間を測る
 */
@Component("recMetricsApplication")
public class RecMetrics {

    // 遅延時間計測タイマーを保持
    private final Timer latency;

    // コンストラクタ
    public RecMetrics(
        MeterRegistry registry
    ) {
        this.latency = Timer.builder("rec.latency")
                                .description("Latency of /rec")
                                .publishPercentileHistogram(true)
                                .register(registry);
    }

    /**
     * 遅延時間を計測して処理を実行
     */
    public <T> T record(java.util.concurrent.Callable<T> callable) throws Exception {
        return latency.recordCallable(callable);
    }

    /**
     * 遅延時間計測値を記録
     */
    public void recordNanos(long nanos) {
        latency.record(nanos, TimeUnit.NANOSECONDS);
    }
}
