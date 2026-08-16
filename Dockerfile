# ビルド用イメージ（Gradle 8 + JDK 21 が入ったイメージ）
FROM gradle:8-jdk21 AS build
# ビルド用の作業場所
WORKDIR /home/gradle/project
# ローカルのプロジェクト一式をコンテナ内の作業場所にコピー
COPY . .
# Spring Boot の実行用 JAR 作成（テストをスキップ、Docker ビルド向けに Gradle デーモンを使わない）
RUN gradle bootJar --no-daemon -x test

# 実行用イメージ（Java 21 の実行環境が入ったイメージ）
FROM eclipse-temurin:21-jre-noble
# 実行用の作業場所
WORKDIR /app

# Python + venv を入れる
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3.12 python3.12-venv libgomp1 \
 && ln -sf /usr/bin/python3.12 /usr/bin/python3 \
 && rm -rf /var/lib/apt/lists/*

# venv の作成
RUN python3 -m venv /opt/venv
# PATH の設定
ENV PATH="/opt/venv/bin:$PATH"

# アプリを配置
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
# training を配置
COPY --from=build /home/gradle/project/training /app/training
# requirements.txt を配置
COPY --from=build /home/gradle/project/requirements.txt /app/requirements.txt

# venvに各パッケージをインストール
RUN pip install --no-cache-dir -r /app/requirements.txt

# ポート番号
EXPOSE 8080
# コピー済みの Spring Boot アプリを起動
ENTRYPOINT ["java","-jar","/app/app.jar"]
