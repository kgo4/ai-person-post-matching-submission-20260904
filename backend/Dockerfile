# ============================================
# 后端多阶段 Docker 构建
# 阶段1: Maven 编译
# 阶段2: JRE 运行
# ============================================

# 生产镜像仅运行已通过本机构建校验的 JAR，避免部署服务器重新解析 Maven 依赖。
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建非 root 用户
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser && \
    mkdir -p /app/uploads && \
    chown -R appuser:appgroup /app

# 复制构建产物
COPY target/app.jar app.jar

# 切换到非 root 用户
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar", \
    "--spring.profiles.active=docker"]
