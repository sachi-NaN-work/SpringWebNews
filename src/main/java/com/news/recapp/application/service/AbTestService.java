package com.news.recapp.application.service;

import com.news.recapp.config.RecProperties;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * A/B テストの割当ロジックを共通化
 *
 * 推薦（RecService）とフィードバック起点のシグナル更新（RecSignalService）で
 * グループ判定がズレないようにする。
 */
@Service
public class AbTestService {

    // リアルタイム推薦の設定を保持
    private final RecProperties props;

    // コンストラクタ
    public AbTestService(RecProperties props) {
        this.props = props;
    }

    /**
     * A/B テストグループを決定
     *  - userId に "GLOBAL" が含まれる: A（GLOBAL寄り）
     *  - userId に "PERSONAL" が含まれる: B（PERSONAL寄り）
     *  - それ以外: 固定salt付きハッシュで B を一定割合に割当
     */
    public String resolveAb(String userId, String requestedAb) {

        // userId が取得できる場合は強制ルールを優先
        if (userId != null && !userId.isBlank()) {

            // userId に Global が含まれる場合は A
            if (userId.contains("Global")) {
                // A グループを返却
                return "A";
            }

            // userId に Personal が含まれる場合は B
            if (userId.contains("Personal")) {
                // B グループを返却
                return "B";
            }
        }

        // リクエストで A/B が指定されている場合はそれを優先（デバッグ用途）
        if (requestedAb != null && !requestedAb.isBlank()) {
            // 前後空白除去して大文字化
            String normalizedRequestedAb = requestedAb.trim().toUpperCase(Locale.ROOT);

            // A または B の場合
            if ("A".equals(normalizedRequestedAb) || "B".equals(normalizedRequestedAb)) {
                // 指定グループを返却
                return normalizedRequestedAb;
            }
        }

        // 固定salt付きハッシュでグループを割当
        String salt = (props.getRtRec().getAbSalt() == null) ? "" : props.getRtRec().getAbSalt();
        int bPercent = props.getRtRec().getAbBPercent();

        // グループ割当の鍵を生成
        String key = salt + ":" + (userId == null ? "" : userId);

        // 0〜99 のバケット値を算出
        int bucket = stableBucket100(key);

        // B を一定割合に割当（例: 10%）
        return (bucket < bPercent) ? "B" : "A";
    }

    /**
     * 0〜99 の安定バケットを算出
     */
    private int stableBucket100(String key) {

        // key が未指定の場合
        if (key == null) {
            // 0 を返却
            return 0;
        }

        try {

            // SHA-256 で安定したハッシュを生成
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));

            // 先頭4バイトを int に変換して 0〜99 に丸める
            int hashValue = (ByteBuffer.wrap(digest, 0, 4).getInt() & 0x7fffffff);
            return hashValue % 100;

        } catch (NoSuchAlgorithmException algorithmException) {
            // フォールバック：hashCode で 0〜99 を生成
            int hashValue = (key.hashCode() & 0x7fffffff);
            return hashValue % 100;
        }
    }
}
