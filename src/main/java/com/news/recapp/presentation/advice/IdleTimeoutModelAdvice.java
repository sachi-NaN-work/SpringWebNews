package com.news.recapp.presentation.advice;

import com.news.recapp.config.IdleTimeoutProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 無操作タイムアウト設定を画面共通モデルへ設定
 */
@ControllerAdvice
public class IdleTimeoutModelAdvice {

    // 無操作タイムアウト設定を保持
    private final IdleTimeoutProperties idleTimeoutProperties;

    /**
     * 無操作タイムアウト設定を受け取る
     */
    public IdleTimeoutModelAdvice(IdleTimeoutProperties idleTimeoutProperties) {
        // 無操作タイムアウト設定を保持
        this.idleTimeoutProperties = idleTimeoutProperties;
    }

    /**
     * 無操作タイムアウト時間をミリ秒で画面へ公開
     */
    @ModelAttribute("idleTimeoutMillis")
    public long idleTimeoutMillis() {
        // 無操作タイムアウト時間をミリ秒で返却
        return idleTimeoutProperties.getDurationMillis();
    }
}
