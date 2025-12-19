FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app

# 依存関係だけ先に解決（キャッシュ効く）
COPY pom.xml ./pom.xml
RUN mvn -q dependency:resolve

# ソースとサンプルをコピー
COPY src ./src
COPY samples ./samples
COPY tests ./tests

# ビルド
RUN mvn -q package -DskipTests

# デフォルト実行
CMD ["mvn", "-q", "exec:java","-Dexec.mainClass=io.DemoLoadEval","-Dexec.args=samples/expr/add_mul.json"]
