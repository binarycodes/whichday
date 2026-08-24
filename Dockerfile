ARG JAVA_VERSION="21"

# Pinned to the builder's own architecture: the jar is Java bytecode and the same
# for every target, so without this a multi-arch build compiles it once per
# platform and does the arm64 pass under QEMU emulation for no gain. Only the
# runtime layers below need to be per-architecture. Requires BuildKit, which both
# `docker buildx bake` and a modern `docker build` use.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
# .git is excluded from the build context (.dockerignore), so the deployed commit
# can't be read here — the caller passes it in (CI uses github.sha).
ARG GIT_SHA
RUN test -n "$GIT_SHA" || (echo "GIT_SHA build arg is required (the deployed commit SHA)" && false)
WORKDIR /app
COPY . .
# Tests are not run here. The integration tier drives a real browser through
# Playwright, which downloads and launches Chromium — not something a docker
# build should be doing. CI runs the whole suite before it builds this image, so
# the jar going in has already been verified; building the image yourself
# verifies nothing, so run ./run.sh verify alongside it.
RUN mvn --batch-mode --no-transfer-progress \
        -Dbuild.commit=${GIT_SHA} -DskipTests clean package


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

# The database is a file the application writes, and /app belongs to root while the
# application runs as demo — so the one directory it writes to is created and handed
# over here, before USER drops the privilege to do it. It has to happen before VOLUME
# too: a build step writing to a declared volume's path is discarded, and this is the
# step that lets a fresh named volume come out owned by demo with nothing for the
# operator to chown.
#
# VOLUME so the database cannot land in the image layer whatever the operator does. A
# forgotten mount then costs them a dangling anonymous volume to find rather than the
# data itself, which is the trade every database image makes.
ENV WHICHDAY_DATA_DIR=/app/data
RUN mkdir -p "$WHICHDAY_DATA_DIR" && chown demo:demo "$WHICHDAY_DATA_DIR"
VOLUME /app/data

USER demo:demo
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75"
ENTRYPOINT ["java","-jar","/app/app.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=5 \
    CMD curl -fsS -o /dev/null "http://127.0.0.1:${PORT:-8080}/" || exit 1