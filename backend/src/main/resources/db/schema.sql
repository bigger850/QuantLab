-- ============================================================
-- QuantLab 数据库结构
-- MySQL 8.0 / utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS quantlab
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE quantlab;

-- ----------------------------
-- 交易标的（个股 / ETF / 指数）
-- ----------------------------
DROP TABLE IF EXISTS stock;
CREATE TABLE stock
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(10)  NOT NULL COMMENT '证券代码，如 600519',
    market      TINYINT      NOT NULL COMMENT '市场：0=深 1=沪',
    name        VARCHAR(32)  NOT NULL COMMENT '名称',
    type        VARCHAR(8)   NOT NULL DEFAULT 'STOCK' COMMENT 'STOCK/ETF/INDEX',
    industry    VARCHAR(32)           DEFAULT NULL COMMENT '所属行业',
    latest_price DECIMAL(12, 3)       DEFAULT NULL COMMENT '最新价（同步时刷新）',
    pct_chg     DECIMAL(10, 3)        DEFAULT NULL COMMENT '最新涨跌幅 %',
    turnover    DECIMAL(20, 2)        DEFAULT NULL COMMENT '最新成交额',
    bar_count   INT                   DEFAULT 0 COMMENT '已入库日线根数',
    last_bar_date DATE                DEFAULT NULL COMMENT '已入库的最新交易日',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=退市/停牌',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_type (type),
    KEY idx_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='交易标的';

-- ----------------------------
-- 日线行情
-- 主键设计为 (code, trade_date)，按标的聚集存储，
-- 单标的区间查询为顺序扫描，避免二级索引回表。
-- 规模量级：5900 标的 x 平均 3000 根 ≈ 1800 万行。
-- ----------------------------
DROP TABLE IF EXISTS daily_bar;
CREATE TABLE daily_bar
(
    code       VARCHAR(10)    NOT NULL COMMENT '证券代码',
    trade_date DATE           NOT NULL COMMENT '交易日',
    open       DECIMAL(12, 3) NOT NULL COMMENT '开盘价',
    close      DECIMAL(12, 3) NOT NULL COMMENT '收盘价',
    high       DECIMAL(12, 3) NOT NULL COMMENT '最高价',
    low        DECIMAL(12, 3) NOT NULL COMMENT '最低价',
    volume     BIGINT         NOT NULL DEFAULT 0 COMMENT '成交量（手）',
    amount     DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '成交额（元）',
    amplitude  DECIMAL(10, 3)          DEFAULT NULL COMMENT '振幅 %',
    pct_chg    DECIMAL(10, 3)          DEFAULT NULL COMMENT '涨跌幅 %',
    turnover   DECIMAL(10, 3)          DEFAULT NULL COMMENT '换手率 %',
    PRIMARY KEY (code, trade_date),
    KEY idx_trade_date (trade_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='日线行情（前复权）';

-- ----------------------------
-- 回测记录
-- ----------------------------
DROP TABLE IF EXISTS backtest_record;
CREATE TABLE backtest_record
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(10)  NOT NULL,
    name          VARCHAR(32)           DEFAULT NULL,
    strategy      VARCHAR(32)  NOT NULL COMMENT '策略标识',
    strategy_name VARCHAR(64)           DEFAULT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    init_cash     DECIMAL(18, 2) NOT NULL,
    final_equity  DECIMAL(18, 2) NOT NULL,
    total_return  DECIMAL(12, 4) NOT NULL COMMENT '总收益率 %',
    annual_return DECIMAL(12, 4) NOT NULL COMMENT '年化收益率 %',
    max_drawdown  DECIMAL(12, 4) NOT NULL COMMENT '最大回撤 %',
    sharpe        DECIMAL(12, 4)         DEFAULT NULL COMMENT '夏普比率',
    win_rate      DECIMAL(12, 4)         DEFAULT NULL COMMENT '胜率 %',
    profit_factor DECIMAL(12, 4)         DEFAULT NULL COMMENT '盈亏比',
    trade_count   INT          NOT NULL DEFAULT 0,
    benchmark_return DECIMAL(12, 4)      DEFAULT NULL COMMENT '同期买入持有收益 %',
    params_json   VARCHAR(1024)          DEFAULT NULL COMMENT '策略参数',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_strategy (strategy),
    KEY idx_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='回测记录';

-- ----------------------------
-- 自选股
-- ----------------------------
DROP TABLE IF EXISTS watchlist;
CREATE TABLE watchlist
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(10) NOT NULL,
    group_name VARCHAR(32) NOT NULL DEFAULT '默认',
    sort_no    INT         NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_group (code, group_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='自选股';

-- ----------------------------
-- 数据同步任务日志
-- ----------------------------
DROP TABLE IF EXISTS sync_task;
CREATE TABLE sync_task
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    task_type   VARCHAR(32) NOT NULL COMMENT 'STOCK_LIST/DAILY_BAR',
    status      VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    total       INT         NOT NULL DEFAULT 0,
    success     INT         NOT NULL DEFAULT 0,
    failed      INT         NOT NULL DEFAULT 0,
    cost_ms     BIGINT      NOT NULL DEFAULT 0,
    message     VARCHAR(512)         DEFAULT NULL,
    started_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME             DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_type_time (task_type, started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='同步任务日志';

-- ----------------------------
-- 策略参数模板（前端下拉用）
-- ----------------------------
DROP TABLE IF EXISTS strategy_meta;
CREATE TABLE strategy_meta
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    code         VARCHAR(32) NOT NULL COMMENT '策略标识',
    name         VARCHAR(64) NOT NULL,
    description  VARCHAR(512)         DEFAULT NULL,
    param_schema VARCHAR(2048)        DEFAULT NULL COMMENT '参数定义 JSON',
    sort_no      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='策略元数据';

INSERT INTO strategy_meta (code, name, description, param_schema, sort_no)
VALUES ('ma_cross', '双均线交叉', '快线上穿慢线买入，下穿卖出', '[{"key":"fast","label":"快线周期","type":"int","default":5,"min":2,"max":120},{"key":"slow","label":"慢线周期","type":"int","default":20,"min":3,"max":250}]', 1),
       ('momentum', '动量轮动', '过去N日涨幅超过阈值买入，跌破止损卖出', '[{"key":"lookback","label":"回看天数","type":"int","default":20,"min":5,"max":250},{"key":"threshold","label":"买入阈值%","type":"double","default":5,"min":-20,"max":50},{"key":"stopLoss","label":"止损%","type":"double","default":8,"min":1,"max":50}]', 2),
       ('rsi', 'RSI 超买超卖', 'RSI 低于下轨买入，高于上轨卖出', '[{"key":"period","label":"RSI周期","type":"int","default":14,"min":2,"max":60},{"key":"oversold","label":"超卖线","type":"int","default":30,"min":5,"max":45},{"key":"overbought","label":"超买线","type":"int","default":70,"min":55,"max":95}]', 3),
       ('boll', '布林带突破', '突破中轨买入，跌破下轨止损', '[{"key":"period","label":"周期","type":"int","default":20,"min":5,"max":120},{"key":"dev","label":"标准差倍数","type":"double","default":2,"min":1,"max":4}]', 4),
       ('grid', '网格交易', '以基准价为中心，按固定间距分批买卖', '[{"key":"stepPct","label":"网格间距%","type":"double","default":3,"min":0.5,"max":20},{"key":"grids","label":"网格层数","type":"int","default":5,"min":1,"max":20},{"key":"positionPct","label":"单格仓位%","type":"double","default":10,"min":1,"max":50}]', 5);
