-- OmniFlow 001_init.sql
-- SQLite DDL Definition

-- 1. 账本 (Ledgers)
CREATE TABLE IF NOT EXISTS ledgers (
    id TEXT PRIMARY KEY, -- ULID
    name TEXT NOT NULL,
    budget REAL DEFAULT 0.0,
    cover TEXT DEFAULT NULL,
    description TEXT DEFAULT NULL,
    is_default BOOLEAN DEFAULT 0,
    created_at INTEGER NOT NULL, -- Unix timestamp
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER DEFAULT NULL
);

-- 2. 账户 (Accounts)
CREATE TABLE IF NOT EXISTS accounts (
    id TEXT PRIMARY KEY, -- ULID
    name TEXT NOT NULL,
    account_type TEXT NOT NULL, -- cash, debit, credit, wallet, other
    balance REAL DEFAULT 0.0,
    credit_limit REAL DEFAULT 0.0,
    cover TEXT DEFAULT NULL,
    description TEXT DEFAULT NULL,
    is_default BOOLEAN DEFAULT 0,
    bill_day INTEGER DEFAULT NULL,
    repay_day INTEGER DEFAULT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER DEFAULT NULL
);

-- 3. 分类 (Categories)
CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY, -- ULID
    name TEXT NOT NULL,
    type TEXT NOT NULL, -- expense, income
    parent_id TEXT DEFAULT NULL, -- ULID of parent category (if any)
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY(parent_id) REFERENCES categories(id)
);

-- 4. 交易明细 (Transactions)
CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY, -- ULID
    ledger_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    category_id TEXT DEFAULT NULL,
    transaction_date INTEGER NOT NULL, -- Unix timestamp (秒/毫秒)
    amount REAL NOT NULL,
    type TEXT NOT NULL, -- expense, income
    merchant TEXT DEFAULT NULL,
    notes TEXT DEFAULT NULL,
    tags TEXT DEFAULT '[]', -- JSON array of strings
    is_excluded BOOLEAN DEFAULT 0,
    external_source TEXT DEFAULT NULL, -- e.g., 'alipay', 'wechat', 'ccb'
    external_id TEXT DEFAULT NULL, -- order number from external source
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY(ledger_id) REFERENCES ledgers(id),
    FOREIGN KEY(account_id) REFERENCES accounts(id),
    FOREIGN KEY(category_id) REFERENCES categories(id)
);

-- 为去重和查询建立索引
CREATE INDEX IF NOT EXISTS idx_txn_ledger_date ON transactions(ledger_id, transaction_date);
CREATE INDEX IF NOT EXISTS idx_txn_external ON transactions(external_source, external_id);

-- 5. 规则 (Rules)
CREATE TABLE IF NOT EXISTS rules (
    id TEXT PRIMARY KEY, -- ULID
    name TEXT NOT NULL,
    priority INTEGER NOT NULL,
    match_condition TEXT NOT NULL, -- JSON definition of match condition
    action TEXT NOT NULL, -- JSON definition of execution action
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER DEFAULT NULL
);

-- 6. 周期账单 (Periodic Bills)
CREATE TABLE IF NOT EXISTS periodic_bills (
    id TEXT PRIMARY KEY, -- ULID
    name TEXT NOT NULL,
    amount REAL NOT NULL,
    type TEXT NOT NULL, -- expense, income
    category_id TEXT DEFAULT NULL,
    account_id TEXT NOT NULL,
    cron_expression TEXT NOT NULL,
    next_date INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY(account_id) REFERENCES accounts(id),
    FOREIGN KEY(category_id) REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS pending_confirmations (
    id TEXT PRIMARY KEY,
    periodic_bill_id TEXT NOT NULL,
    transaction_id TEXT DEFAULT NULL,
    due_date INTEGER NOT NULL,
    amount REAL NOT NULL,
    type TEXT NOT NULL,
    category_id TEXT DEFAULT NULL,
    account_id TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY(periodic_bill_id) REFERENCES periodic_bills(id),
    FOREIGN KEY(transaction_id) REFERENCES transactions(id),
    FOREIGN KEY(account_id) REFERENCES accounts(id),
    FOREIGN KEY(category_id) REFERENCES categories(id)
);

-- 7. 同步操作日志 (Sync Logs)
CREATE TABLE IF NOT EXISTS sync_logs (
    id TEXT PRIMARY KEY, -- ULID
    table_name TEXT NOT NULL,
    record_id TEXT NOT NULL,
    operation TEXT NOT NULL, -- INSERT, UPDATE, DELETE
    payload TEXT NOT NULL, -- JSON string of the record
    created_at INTEGER NOT NULL
);

-- 8. 应用配置 (App Config)
CREATE TABLE IF NOT EXISTS app_config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
