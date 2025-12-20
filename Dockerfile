FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app

COPY pom.xml ./pom.xml
RUN mvn -q dependency:resolve

COPY src ./src
COPY samples ./samples
COPY tests ./tests

RUN mvn -q package -DskipTests

# ここ重要：デフォルトで何も実行しない（compose側で常駐させる）
CMD ["bash"]
