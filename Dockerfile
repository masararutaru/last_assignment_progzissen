FROM maven:3.9.9-eclipse-temurin-17

# GUIアプリ用にX11関連のパッケージとVNCサーバーをインストール
RUN apt-get update && apt-get install -y \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libfontconfig1 \
    xvfb \
    x11vnc \
    fluxbox \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pom.xml ./pom.xml
RUN mvn -q dependency:resolve

COPY src ./src
COPY samples ./samples
COPY tests ./tests
COPY assets ./assets
COPY run.sh ./run.sh
COPY run-gui.sh ./run-gui.sh

RUN chmod +x ./run.sh ./run-gui.sh

RUN mvn -q package -DskipTests

# ここ重要：デフォルトで何も実行しない（compose側で常駐させる）
CMD ["bash"]
