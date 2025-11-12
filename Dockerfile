FROM eclipse-temurin:17-jdk

# Install nodejs for log processing
ARG NODE_VERSION=20.10.0
ARG TARGETPLATFORM
RUN if [ "$TARGETPLATFORM" = "linux/arm64" ]; then ARCH="arm64"; else ARCH="x64"; fi \
 && curl https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-$ARCH.tar.gz | tar -xz -C /usr/local --strip-components 1
 
ARG GRADLE_VERSION=8.9

RUN apt-get update \
 && apt-get install -y --no-install-recommends bzip2 curl unzip \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -o /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt/gradle \
 && ln -s /opt/gradle/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle \
 && rm /tmp/gradle.zip

ENV GRADLE_HOME=/opt/gradle/gradle-${GRADLE_VERSION}

WORKDIR /usr/src/parser
COPY . /usr/src/parser
RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar

CMD ["java", "-jar", "/usr/src/parser/build/libs/parser-0.1.0.jar"]
