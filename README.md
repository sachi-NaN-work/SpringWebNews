# SpringWebNews - ニュースサイト

Spring Boot 3.5 / Java 21 / WebFlux で構成した、ニュースサイトのデモアプリです。


---


## ● 目次

(※) 優先度の基準

|    評価     | 優先度 | 補足                          |
|:---------:|:---:|-----------------------------|
| **（ 5 ）** | 最優先 | 優先してご確認いただきたい資料です。          |
| **（ 4 ）** |  高  | 可能であれば、ご確認いただければ幸いです。       |
| **（ 3 ）** |  中  | お時間に余裕がある場合に、ご確認いただければ幸いです。 |
| **（ 2 ）** |  低  | 必要に応じて、ご確認いただければ幸いです。       |
|     －     | 参考  | 補足資料のため、ご確認は任意です。           |

> 本表の優先度は、限られた時間の中で事前にご確認いただく際の閲覧順を示しています。


―――――――――――――――――――――――――――――――


<br>


### ＜ README ファイル一式 ＞

| No | ファイル                                            | 内容               | 人事担当者向け<br/>優先度 | 技術担当者向け<br/>優先度 |
|:--:|-------------------------------------------------|------------------|:---------------:|:---------------:|
| 1  | [01_README.md](document/00_README/01_README.md) | 開発背景・経緯          |    **（ 2 ）**    |    **（ 4 ）**    |
| 2  | [02_README.md](document/00_README/02_README.md) | アプリ機能概要          |    **（ 2 ）**    |    **（ 4 ）**    |
| 3  | [03_README.md](document/00_README/03_README.md) | 技術スタック           |        -        |        -        |
| 4  | [04_README.md](document/00_README/04_README.md) | リポジトリの主要ディレクトリ   |        -        |        -        |
| 5  | [05_README.md](document/00_README/05_README.md) | 前提               |        -        |        -        |
| 6  | [06_README.md](document/00_README/06_README.md) | 起動 / 停止手順        |        -        |        -        |
| 7  | [07_README.md](document/00_README/07_README.md) | アクセス先            |        -        |        -        |
| 8  | [08_README.md](document/00_README/08_README.md) | ローカルでアプリだけ起動する   |        -        |        -        |
| 9  | [09_README.md](document/00_README/09_README.md) | 初期データ            |        -        |        -        |
| 10 | [10_README.md](document/00_README/10_README.md) | 管理画面操作と対応ロール     |        -        |        -        |
| 11 | [11_README.md](document/00_README/11_README.md) | モデルファイルの扱い       |        -        |        -        |
| 12 | [12_README.md](document/00_README/12_README.md) | 主な API / エンドポイント |        -        |        -        |
| 13 | [13_README.md](document/00_README/13_README.md) | 外部設定             |        -        |        -        |
| 14 | [14_README.md](document/00_README/14_README.md) | 学習モデル            |        -        |        -        |
| 15 | [15_README.md](document/00_README/15_README.md) | 今後の改善・追加予定       |        -        |    **（ 2 ）**    |
| 16 | [16_README.md](document/00_README/16_README.md) | 本開発による経験         |        -        |    **（ 4 ）**    |


<br>


### ＜ フロー図 ファイル一式 ＞

| No | ファイル                                              | 内容         | 人事担当者向け<br/>優先度 | 技術担当者向け<br/>優先度 |
|:--:|---------------------------------------------------|------------|:---------------:|:---------------:|
| 1  | [01_処理フロー.md](document/01_フロー図/01_処理フロー.md)       | 処理フロー      |        -        |        -        |
| 2  | [02_画面遷移フロー.md](document/01_フロー図/02_画面遷移フロー.md)   | 画面遷移フロー    |        -        |        -        |
| 3  | [03_推薦フロー.md](document/01_フロー図/03_推薦フロー.md)       | 推薦処理フロー    |        -        |        -        |
| 4  | [04_学習モデルフロー.md](document/01_フロー図/04_学習モデルフロー.md) | 学習モデル処理フロー |        -        |        -        |


<br>


### ＜ 設計 ファイル一式 ＞

| No | ファイル                                                    | 内容                | 人事担当者向け<br/>優先度 | 技術担当者向け<br/>優先度 |
|:--:|---------------------------------------------------------|-------------------|:---------------:|:---------------:|
| 1  | [01_要件定義.md](document/02_設計/01_要件定義.md)                 | 要件定義              |        -        |        -        |
| 2  | [02_基本設計.md](document/02_設計/02_基本設計.md)                 | 基本設計              |        -        |        -        |
| 3  | [03_詳細設計.md](document/02_設計/03_詳細設計.md)                 | 詳細設計              |        -        |        -        |
| 4  | [04_PostgreSQL設計.md](document/02_設計/04_PostgreSQL設計.md) | DB 設計（PostgreSQL） |        -        |        -        |
| 5  | [05_Redis設計.md](document/02_設計/05_Redis設計.md)           | DB 設計（Redis）      |        -        |        -        |
| 6  | [06_画面設計.md](document/02_設計/06_画面設計.md)                 | 画面設計              |        -        |        -        |
| 7  | [07_推薦設計.md](document/02_設計/07_推薦設計.md)                 | 推薦値の仕様            |        -        |        -        |
| 8  | [08_学習モデル設計.md](document/02_設計/08_学習モデル設計.md)           | 学習モデルの仕様          |        -        |        -        |


<br>


---


## ● 変更履歴

| No | 変更内容 |    更新日     |
|:--:|:-----|:----------:|
| 1  | 新規作成 | 2026/08/05 |


<br>


---


## ● 注意事項・免責事項（Notes and Disclaimer）

本 WEB アプリは学習・検証・デモ用途を目的としたサンプルアプリケーションです。  
実運用環境での利用を前提としたものではありません。

本アプリの利用、改変、実行、または本アプリを参考にした開発によって発生した不具合、データ損失、セキュリティ上の問題、  
その他いかなる損害についても、作者は一切の責任を負いません。

実運用で利用する場合は、認証・認可、入力値検証、ログ管理、バックアップ、監視、脆弱性対策、各種設定値などを  
十分に確認し、利用者自身の責任で対応してください。


> This application is a sample application intended for learning, verification, and demonstration purposes.  
> It is not intended for use in a production environment.
> The author assumes no responsibility for any defects, data loss, security issues,
> or any other damages arising from the use, modification, execution, or development based on this application.
> If you use this application in a production environment, you must thoroughly review authentication, authorization,
> input validation, logging, backups, monitoring, vulnerability countermeasures, configuration values,
> and other relevant aspects, and handle them at your own responsibility.


<br>

