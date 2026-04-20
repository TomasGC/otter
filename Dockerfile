# Android Build Environment
FROM eclipse-temurin:17-jdk-jammy

# Install dependencies with retry logic for mirror sync issues
RUN apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    # Retry apt-get update up to 3 times with 5s delay
    (apt-get update --fix-missing || \
     (sleep 5 && apt-get update --fix-missing) || \
     (sleep 10 && apt-get update --fix-missing)) && \
    apt-get install -y --no-install-recommends \
        wget \
        unzip \
        git \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Set environment variables
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# Install Android SDK Command Line Tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept licenses and install SDK components
RUN yes | sdkmanager --licenses
RUN sdkmanager "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "cmdline-tools;latest"

# Set working directory
WORKDIR /workspace

# Copy Gradle wrapper files first (for caching)
COPY gradle gradle
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./

# Make gradlew executable
RUN chmod +x gradlew

# Copy app build file for dependency resolution (better caching)
COPY app/build.gradle.kts app/

# Download Gradle wrapper and verify installation
RUN ./gradlew --version --no-daemon

# Pre-download all project dependencies (cache layer)
# This layer only rebuilds when build.gradle.kts changes
RUN ./gradlew dependencies --no-daemon || echo "Warning: Dependency resolution incomplete"

# Default command
CMD ["./gradlew", "tasks"]
