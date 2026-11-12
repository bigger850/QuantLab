#!/usr/bin/env bash
# QuantLab 一键启动（本地开发）
# 用法：./start.sh
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND_PORT=8080
FRONTEND_PORT=5173

echo "==> 检查依赖"
command -v java >/dev/null || { echo "缺少 JDK 21"; exit 1; }
command -v node >/dev/null || { echo "缺少 Node 20+"; exit 1; }

echo "==> 检查 MySQL 与 Redis"
if ! (echo > /dev/tcp/127.0.0.1/3306) 2>/dev/null; then
  echo "MySQL 未在 3306 监听，请先启动"
  exit 1
fi
if ! (echo > /dev/tcp/127.0.0.1/6379) 2>/dev/null; then
  echo "Redis 未在 6379 监听，请先启动"
  exit 1
fi

echo "==> 初始化数据库（幂等，已存在则跳过建表）"
mysql -h127.0.0.1 -uroot -p"${MYSQL_PASSWORD:-123456}" < "$ROOT/backend/src/main/resources/db/schema.sql" 2>/dev/null \
  && echo "    数据库就绪" \
  || echo "    跳过（可能已初始化，或 mysql 客户端不在 PATH）"

echo "==> 打包后端"
cd "$ROOT/backend"
[ -f target/quantlab-backend-1.0.0.jar ] || mvn -B clean package -DskipTests -q

echo "==> 启动后端 :$BACKEND_PORT"
nohup java -Dfile.encoding=UTF-8 -jar target/quantlab-backend-1.0.0.jar \
  --server.port=$BACKEND_PORT > "$ROOT/backend.log" 2>&1 &
echo "    PID $!"

echo "==> 启动前端 :$FRONTEND_PORT"
cd "$ROOT/frontend"
[ -d node_modules ] || npm install --registry=https://registry.npmmirror.com
nohup npm run dev > "$ROOT/frontend.log" 2>&1 &
echo "    PID $!"

sleep 5
echo ""
echo "前端  http://127.0.0.1:$FRONTEND_PORT"
echo "后端  http://127.0.0.1:$BACKEND_PORT/api/dashboard/overview"
echo "日志  backend.log / frontend.log"
