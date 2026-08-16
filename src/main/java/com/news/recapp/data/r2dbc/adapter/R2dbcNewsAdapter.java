package com.news.recapp.data.r2dbc.adapter;

import com.news.recapp.application.exception.AppMessageException;
import com.news.recapp.application.port.out.NewsPort;
import com.news.recapp.persistence.entity.NewsEntity;
import com.news.recapp.persistence.repo.NewsRepository;
import com.news.recapp.persistence.repo.NewsWriteRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ニュース情報の永続化連携を仲介
 */
@Component
public class R2dbcNewsAdapter implements NewsPort {

    // ニュースリポジトリを保持
    private final NewsRepository repo;

    // ニュース保存リポジトリを保持
    private final NewsWriteRepository writeRepo;

    // コンストラクタ
    public R2dbcNewsAdapter(
            NewsRepository repo,
            NewsWriteRepository writeRepo
    ) {
        this.repo = repo;
        this.writeRepo = writeRepo;
    }

    /**
     * 最新の公開ニュース一覧を取得
     */
    @Override
    public Flux<NewsEntity> findLatestVisible(long limit) {
        // 最新の公開ニュース一覧を返却
        return repo.findLatestVisible(limit);
    }

    /**
     * カテゴリ別の公開ニュース一覧を取得
     */
    @Override
    public Flux<NewsEntity> findAllVisibleByCategory(UUID categoryId) {
        // カテゴリ別の公開ニュース一覧を返却
        return repo.findAllVisibleByCategory(categoryId);
    }

    /**
     * 公開ニュース一覧を取得
     */
    @Override
    public Flux<NewsEntity> findAllVisible() {
        // 公開ニュース一覧を返却
        return repo.findAllVisible();
    }

    /**
     * 公開ニュースをランダムに取得
     */
    @Override
    public Flux<NewsEntity> findVisibleRandom(long limit) {
        // 公開ニュースをランダムに返却
        return repo.findVisibleRandom(limit);
    }

    /**
     * カテゴリ別の公開ニュースをランダムに取得
     */
    @Override
    public Flux<NewsEntity> findVisibleRandomByCategory(UUID categoryId, long limit) {
        // カテゴリ別の公開ニュースをランダムに返却
        return repo.findVisibleRandomByCategory(categoryId, limit);
    }

    /**
     * 全ニュース一覧を取得（非公開を含む）
     */
    @Override
    public Flux<NewsEntity> findAllNews() {
        // 全ニュース一覧を返却
        return repo.findAllNews();
    }

    /**
     * ニュース ID からニュースを取得
     */
    @Override
    public Mono<NewsEntity> findByNewsId(UUID id) {
        // ニュース ID 一致のニュースを返却
        return repo.findById(id);
    }

    /**
     * ニュースを保存
     */
    @Override
    public Mono<NewsEntity> saveNews(NewsEntity entity) {

        // ニュースが未指定、またはニュース ID が未指定の場合
        if (entity == null || entity.getId() == null) {
            // ニュース ID 不足としてエラーを返却
            return Mono.error(new AppMessageException("news.adapter.err.news_id_missing"));
        }

        // ニュース ID を取得
        UUID newsId = entity.getId();

        // ニュースを保存
        return writeRepo.upsert(entity)
                // 保存後にニュースを再取得
                .then(repo.findById(newsId))
                // 取得できない場合は入力ニュースを返却
                .switchIfEmpty(Mono.just(entity));
    }

}
