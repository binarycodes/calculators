ARG JAVA_VERSION="21"
ARG VAADIN_SERVER_LICENSE=""

FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
ARG VAADIN_SERVER_LICENSE
# Commit SHA supplied by the caller (CI passes github.sha). The build context
# excludes .git (see .dockerignore), so the SHA must come in as a build arg
# rather than being derived from a repository inside the image.
ARG GIT_SHA="unknown"
WORKDIR /app
COPY . .
RUN mvn --batch-mode --no-transfer-progress -Dvaadin.offlineKey=${VAADIN_SERVER_LICENSE} -Dbuild.commit=${GIT_SHA} clean package


FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine

ARG APP_NAME
ARG APP_VERSION

RUN test -n "$APP_NAME" || (echo "APP_NAME  not set" && false) \
    && test -n "$APP_VERSION" || (echo "APP_VERSION  not set" && false)

RUN apk add --no-cache curl \
    && addgroup -S -g 10001 demo \
    && adduser -S -D -H -u 10001 -G demo demo

WORKDIR /app
COPY --from=build --chown=demo:demo /app/target/${APP_NAME}-${APP_VERSION}.jar /app/app.jar

USER demo:demo
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75"
ENTRYPOINT ["java","-jar","/app/app.jar"]

HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=5 \
    CMD ["curl", "-fsS", "http://127.0.0.1:8088/actuator/health/readiness"]