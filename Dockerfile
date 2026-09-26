FROM gradle:8.7-jdk17-jammy

USER root
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip ca-certificates python3 \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/android-sdk/cmdline-tools \
 && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -o /tmp/android-cli.zip \
 && unzip -q /tmp/android-cli.zip -d /tmp/android-cli \
 && mv /tmp/android-cli/cmdline-tools /opt/android-sdk/cmdline-tools/latest \
 && rm -rf /tmp/android-cli /tmp/android-cli.zip

ENV PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/build-tools/35.0.0:$PATH

RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true \
 && sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

WORKDIR /workspace
COPY . .

RUN mkdir -p app/src/main/assets \
 && curl -fL "https://raw.githubusercontent.com/Bwarhness/jarvis-assistant/17edf69264ae421d68a103cde1bc2c1dcab2f673/app/src/main/assets/melspectrogram.onnx" -o app/src/main/assets/melspectrogram.onnx \
 && curl -fL "https://raw.githubusercontent.com/Bwarhness/jarvis-assistant/17edf69264ae421d68a103cde1bc2c1dcab2f673/app/src/main/assets/embedding_model.onnx" -o app/src/main/assets/embedding_model.onnx \
 && curl -fL "https://raw.githubusercontent.com/jakes1345/ShadowCypher/827829b09399d6c02ba108607e70aa05bf7485a5/android/assistant/src/main/assets/hey_shadow.onnx" -o app/src/main/assets/hey_shadow.onnx \
 && test -s app/src/main/assets/melspectrogram.onnx \
 && test -s app/src/main/assets/embedding_model.onnx \
 && test -s app/src/main/assets/hey_shadow.onnx

RUN gradle --no-daemon :app:assembleRelease --stacktrace \
 && test -f app/build/outputs/apk/release/app-release.apk \
 && apksigner verify --verbose app/build/outputs/apk/release/app-release.apk \
 && sha256sum app/build/outputs/apk/release/app-release.apk > app/build/outputs/apk/release/apk.sha256

CMD ["python3","-m","http.server","8080","--directory","/workspace/app/build/outputs/apk/release"]
