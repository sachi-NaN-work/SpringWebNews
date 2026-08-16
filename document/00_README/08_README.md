# SpringWebNews - ニュースサイト

[\[ <u>目次へ戻る</u> \]](../../README.md#-readme-ファイル一式-)


---

<br>

## ● ローカルでアプリだけ起動する

[＜ 1 ＞ 起動方法](#-1--起動方法)


<br>

―――――――――――――――――――――――――――――――

<br>

### ＜ 1 ＞ 起動方法

依存サービスは Docker で起動し、Spring Boot をホストで立ち上げる構成です。

```bash
docker compose up -d postgres redis kafka
./gradlew bootRun
```
`config/` 配下の外部設定が読まれるため、起動前に `config/application.yml` と
`config/application-prod.yml` または `config/application-dev.yml` を用意してください。


<br>


---

[\[ <u>▲ ページ上部へ</u> \]](#springwebnews---ニュースサイト)

[\[ <u>目次へ戻る</u> \]](../../README.md#-readme-ファイル一式-)
