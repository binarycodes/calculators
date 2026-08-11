ARG JAVA_VERSION="21"

FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
# .git is excluded from the build context (.dockerignore), so the deployed commit
# can't be read here — the caller passes it in (CI uses github.sha).
ARG GIT_SHA
RUN test -n "$GIT_SHA" || (echo "GIT_SHA build arg is required (the deployed commit SHA)" && false)
WORKDIR /app
COPY . .
RUN --mount=type=secret,id=vaadin_license,required=true \
    mvn --batch-mode --no-transfer-progress \
        -Dvaadin.offlineKey="$(cat /run/secrets/vaadin_license)" \
        -Dbuild.commit=${GIT_SHA} clean package


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

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=5 \
    CMD curl -fsS -o /dev/null "http://127.0.0.1:${PORT:-8080}/" || exit 1