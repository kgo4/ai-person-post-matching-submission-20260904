# ============================================
# 后端多阶段 Docker 构建
# 阶段1: Maven 编译
# 阶段2: JRE 运行
# ============================================
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw.cmd pom.xml ./
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建非 root 用户
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser && \
    mkdir -p /app/uploads && \
    chown -R appuser:appgroup /app

# 复制容器内刚刚构建的产物
COPY --from=build /build/target/app.jar app.jar

# 切换到非 root 用户
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar", \
    "--spring.profiles.active=docker"]
