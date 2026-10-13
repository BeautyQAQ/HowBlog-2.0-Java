# syntax=docker/dockerfile:1
# 三个业务服务共用的镜像构建文件。
# 构建上下文必须是仓库根目录；模块通过 SERVICE 参数选择，端口通过 SERVICE_PORT 参数设置。
# 由 docker-compose.yml 调用，也可单独构建：
#   docker build -f docker/service.Dockerfile --build-arg SERVICE=how_article --build-arg SERVICE_PORT=9004 .

FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
# 依赖缓存挂在 BuildKit 缓存上，重复构建不需要重新下载依赖。
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl "${SERVICE}" -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre
ARG SERVICE
ARG SERVICE_PORT=9000
ENV SERVICE_NAME=${SERVICE} \
    SERVICE_PORT=${SERVICE_PORT} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --create-home --uid 10001 --shell /usr/sbin/nologin appuser
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/*.jar /app/app.jar
USER appuser
EXPOSE ${SERVICE_PORT}
# 健康检查只读取 /actuator/health 的状态，不打印细节。
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVICE_PORT}/actuator/health" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]