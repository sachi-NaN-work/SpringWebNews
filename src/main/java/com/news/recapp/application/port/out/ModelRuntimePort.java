package com.news.recapp.application.port.out;

/**
 * 学習モデルのポート
 */
public interface ModelRuntimePort {

    /**
     * 学習モデルの有効フラグ判定
     */
    boolean isEnabled();

    /**
     * 現在の学習モデルのファイルパスを取得
     */
    String getCurrentModelPath();

    /**
     * 学習モデルを再読込
     */
    void reload();

    /**
     * 学習モデルを指定パスで再読込
     */
    void reload(String newModelPath);

    /**
     * 特徴量から学習モデルのスコアを取得
     */
    double score(float[] features);

}
