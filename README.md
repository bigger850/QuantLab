# QuantLab · A股量化研究工作台

自建的 A 股行情与策略研究平台。行情数据本地落库，指标与回测在服务端计算，前端用 ECharts 展示。

> 定位是**研究工具**：展示行情、计算指标、跑历史回测、按因子筛股。
> 不提供投资建议，不接交易通道，不涉及资金往来。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21 · Spring Boot 3.3 · MyBatis-Plus 3.5 |
| 存储 | MySQL 8.0（行情、回测记录）· Redis 6（缓存、同步锁、进度） |
| 前端 | Vue 3 · Vite 5 · ECharts 5 |
| 数据源 | 新浪财经（标的清单）· 腾讯财经（日线、实时） |
| 部署 | Docker Compose（MySQL + Redis + 后端 + Nginx 前端） |

## 功能

### 已实现

- **市场概览** — 涨跌家数、涨幅/跌幅榜、成交额排行、同步状态
- **行情列表** — 个股 / ETF 分类浏览，代码与名称搜索，分页
- **个股详情** — 日 K 线（前复权）、MA5/10/20/60、MACD、RSI、BOLL、成交量，5 档时间范围
- **策略回测** — 5 个内置策略，参数可调，输出净值曲线、回撤、绩效指标、逐笔成交
- **选股筛选** — 成交额、区间涨幅、波动率、回撤、均线位置多条件组合
- **数据同步** — 全量/增量同步，Redis 分布式锁防并发，进度实时可查，失败个股可补跑

### 内置策略

| 标识 | 名称 | 逻辑 |
|---|---|---|
| `ma_cross` | 双均线交叉 | 快线在慢线上方持仓，下方清仓 |
| `momentum` | 动量轮动 | N 日涨幅达标买入，回撤超止损离场 |
| `rsi` | RSI 超买超卖 | RSI 跌破超卖线建仓，升破超买线离场 |
| `boll` | 布林带突破 | 站上中轨建仓，跌破下轨止损 |
| `grid` | 网格交易 | 按价格分档加减仓 |

### 回测规则

- 第 `i-1` 根收盘产生信号，第 `i` 根**开盘价**成交，杜绝未来函数
- 佣金万 2.5（最低 5 元），卖出加收万 5 印花税
- 买入按 100 股整手，清仓时零碎股一并卖出
- 未平仓部分按最后收盘价估值，不强制平仓
- 绩效口径：年化按 252 交易日折算，夏普取无风险利率 2%

## 快速开始

### 前置

- JDK 21、Maven 3.9+
- Node 20+
- MySQL 8.0（默认 `127.0.0.1:3306`，root/123456）
- Redis 6+（默认 `127.0.0.1:6379`，无密码）

### 本地启动

```bash
# 1. 初始化数据库
mysql -uroot -p < backend/src/main/resources/db/schema.sql

# 2. 后端
cd backend
mvn clean package -DskipTests
java -jar target/quantlab-backend-1.0.0.jar --server.port=8080

# 3. 前端
cd frontend
npm install
npm run dev
```

访问 http://127.0.0.1:5173

### 首次灌数据

打开首页点「同步标的清单」，约 30 秒拉取 6500 只标的。
再点「同步热门行情」，按成交额取前 300 只拉 2018 年至今的日线。

也可以直接调接口：

```bash
curl -X POST http://127.0.0.1:8080/api/sync/symbols
# top=300 只同步热门；top=0 表示全市场（会触发限流，不建议）
curl -X POST "http://127.0.0.1:8080/api/sync/daily?incremental=false&top=300"
curl http://127.0.0.1:8080/api/sync/progress
```

未预同步的标的，在打开个股详情页时会自动回源拉取并落库，无需手动操作。

> **限流提醒**：免费行情源对批量抓取有 IP 级封禁。实测 8 并发 + 30ms 间隔下，
> 连续请求约 2000 次后腾讯返回 501，封禁持续数十分钟到数小时。
> 当前配置为 2 并发 + 800ms 间隔，可长时间稳定运行。不要随意调高。

### Docker 部署

```bash
cd deploy
docker compose up -d --build
```

前端 80 端口，后端 8080。

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/dashboard/overview` | 市场概览 |
| GET | `/api/stocks/page` | 标的分页（type/keyword/page/size） |
| GET | `/api/stocks/search` | 代码或名称搜索 |
| GET | `/api/stocks/{code}/kline` | K 线 + 指标（limit/indicators） |
| GET | `/api/quote/realtime` | 批量实时行情（codes 逗号分隔） |
| GET | `/api/strategy/list` | 策略列表与参数定义 |
| POST | `/api/backtest/run` | 执行回测 |
| GET | `/api/backtest/recent` | 最近回测记录 |
| POST | `/api/screener/run` | 执行选股 |
| POST | `/api/sync/symbols` | 同步标的清单 |
| POST | `/api/sync/daily` | 触发日线同步（incremental） |
| GET | `/api/sync/progress` | 同步进度 |

返回体统一为 `{code, message, data, timestamp}`，`code=0` 为成功。

## 数据表

| 表 | 说明 |
|---|---|
| `stock` | 标的清单，含最新价、涨跌幅、已入库K线根数 |
| `daily_bar` | 日线行情，主键 `(code, trade_date)` 按标的聚集 |
| `backtest_record` | 回测结果与绩效 |
| `watchlist` | 自选股 |
| `sync_task` | 同步任务日志 |
| `strategy_meta` | 策略元数据与参数定义 |

`daily_bar` 用复合主键而非自增 ID：单标的区间查询走顺序扫描，省掉二级索引回表。
全市场 6500 只、8 年日线约 1000 万行，单表可承载；再往上按 `code` 哈希分表或按 `trade_date` 分区。

## 数据源说明

| 用途 | 源 | 备注 |
|---|---|---|
| 标的清单 | 新浪 `Market_Center.getHQNodeData` | 需 Referer，100 条/页 |
| 日线 | 腾讯 `fqkline/get` | 单次上限约 640 根，按 2 年分段 |
| 实时 | 腾讯 `qt.gtimg.cn` | GBK 编码，需按字节解码 |

东方财富接口在本机网络下连接被重置，已从数据源层移除。
`MarketDataProvider` 是抽象层，换源只需再加一个实现。

日线成交额腾讯不直接提供，按 `成交量(手) × 100 × (开盘+收盘)/2` 估算，仅用于排序参考。

## 已知限制

- 只做日线级别，无分钟级数据
- 不做复权因子单独存储，直接用前复权序列
- 回测为单标的、单仓位，暂不支持组合与调仓
- 无用户体系，自选股未做账号隔离
- 同步触发限流时部分标的会失败，重跑增量即可补齐
