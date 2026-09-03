# ============================================
# 前端多阶段 Docker 构建
# 阶段1: Node 编译
# 阶段2: Nginx 服务
# ============================================

# ---- 编译阶段 ----
FROM node:20-alpine AS build
WORKDIR /app

# 先复制依赖文件，利用 Docker 缓存
COPY package*.json ./
# echarts-wordcloud currently declares an ECharts 5 peer range while this
# application uses ECharts 6. The tested lockfile intentionally resolves it.
RUN npm ci --legacy-peer-deps --silent

# 复制源码并编译
COPY . .
ARG VITE_APP_BASE_API=/api
ENV VITE_APP_BASE_API=${VITE_APP_BASE_API}
RUN npm run build

# ---- 运行阶段 ----
FROM nginx:alpine

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 复制构建产物
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
