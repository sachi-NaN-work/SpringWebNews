package com.news.recapp.presentation.web;

import com.news.recapp.presentation.openapi.ApiOperationSummaries;
import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.service.RecService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * 推薦 API コントローラ
 * - エンドポイント: GET /rec
 * - ログイン状態に応じて userId を決定し、{@link com.news.recapp.application.service.RecService} に処理を委譲する
 *
 * 【推薦の流れ（初心者向け）】
 * 1) userId を決定（ログイン: Principal#getName / 未ログイン: "anonymous"）
 * 2) ユーザーの行動ログ（閲覧/お気に入り/Good/Bad など）をスナップショットとして読み込む
 * 3) 候補記事を生成（personal 候補 + global 候補）
 * 4) 候補ごとに特徴量（数値）を作り、スコア順に並べて上位K件を返す
 *
 * 【特徴量（9個・固定）】
 * - exposure
 * - cat_viewShare, cat_favShare, cat_goodShare, cat_badShare
 * - strongest_tag_viewShare, strongest_tag_favShare, strongest_tag_goodShare, strongest_tag_badShare
 *   ※ share は part / max(total, 1) の比率。
 *   ※ strongest_tag_* は、記事に紐づく有効タグのうち、嗜好スコアが最も高いタグの share 値。
 *
 * 【A/Bブースト】
 * - 学習対象にしない（特徴量にも教師信号にも混ぜない）
 * - 推論の最後に add / multiply のどちらかで適用する
 *
 * 【スコア計算（学習モデルあり/なし）】
 * - モデル有効時（ONNX 推論）:
 *   scoreModel = f([9特徴量])
 * - モデル無効/失敗時（固定係数）:
 *   scoreModel = dot(w, [9特徴量]) + b
 * - 最後にブーストを適用:
 *   score = applyBoost(scoreModel, abBoost) + epsilon
 */
@RestController
public class RecController {

    // 推薦サービスを保持
    private final RecService recService;

    // コンストラクタ
    public RecController(RecService recService) {
        this.recService = recService;
    }

    /**
     * 推薦を実行して上位K件を返却
     *
     * @param principal ログインユーザー。null の場合、"anonymous" として扱う。
     * @param topK 取得件数（k）。未指定/0以下の場合、設定値（defaultK）を使用する。
     * @param ab A/B テストのグループ（例: "A" / "B"）。B の場合、加点（abBoost）の対象。
     * @return 推薦結果（スコア順）
     */
    @Operation(summary = ApiOperationSummaries.REC_REC)
    @GetMapping("/rec")
    public Mono<ApiDtos.RecResponse> rec(
            Principal principal,
            @RequestParam(name = "k", required = false) Integer topK,
            @RequestParam(name = "ab", required = false) String ab
    ) {

        // ログインユーザー名を取得（未指定なら "anonymous"）
        String userId = (principal == null) ? "anonymous" : principal.getName();

        // 推薦結果を返却
        return recService.recommend(userId, ab, topK);
    }
}
