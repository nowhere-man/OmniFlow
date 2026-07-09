use serde::{Deserialize, Serialize};

/// 账本模型
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Ledger {
    pub id: String,
    pub name: String,
    pub budget: f64,
    pub cover: Option<String>,
    pub description: Option<String>,
    pub is_default: bool,
    pub created_at: i64,
    pub updated_at: i64,
    pub deleted_at: Option<i64>,
}

/// 账户类型
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum AccountType {
    Cash,
    Debit,
    Credit,
    Wallet,
    Other,
}

impl std::str::FromStr for AccountType {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "cash" => Ok(AccountType::Cash),
            "debit" => Ok(AccountType::Debit),
            "credit" => Ok(AccountType::Credit),
            "wallet" => Ok(AccountType::Wallet),
            "other" => Ok(AccountType::Other),
            _ => Err(format!("Unknown account type: {}", s)),
        }
    }
}

impl std::fmt::Display for AccountType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let s = match self {
            AccountType::Cash => "cash",
            AccountType::Debit => "debit",
            AccountType::Credit => "credit",
            AccountType::Wallet => "wallet",
            AccountType::Other => "other",
        };
        write!(f, "{}", s)
    }
}

/// 账户模型
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Account {
    pub id: String,
    pub name: String,
    pub account_type: AccountType,
    pub balance: f64,
    pub credit_limit: f64,
    pub cover: Option<String>,
    pub description: Option<String>,
    pub is_default: bool,
    pub bill_day: Option<i64>,
    pub repay_day: Option<i64>,
    pub created_at: i64,
    pub updated_at: i64,
    pub deleted_at: Option<i64>,
}

/// 交易/分类类型
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum TransactionType {
    Expense,
    Income,
}

impl std::str::FromStr for TransactionType {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "expense" => Ok(TransactionType::Expense),
            "income" => Ok(TransactionType::Income),
            _ => Err(format!("Unknown transaction type: {}", s)),
        }
    }
}

impl std::fmt::Display for TransactionType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let s = match self {
            TransactionType::Expense => "expense",
            TransactionType::Income => "income",
        };
        write!(f, "{}", s)
    }
}

/// 分类模型
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Category {
    pub id: String,
    pub name: String,
    pub category_type: TransactionType,
    pub parent_id: Option<String>,
    pub icon: Option<String>,
    pub created_at: i64,
    pub updated_at: i64,
    pub deleted_at: Option<i64>,
}

/// 交易明细模型
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Transaction {
    pub id: String,
    pub ledger_id: String,
    pub account_id: String,
    pub category_id: Option<String>,
    pub transaction_date: i64,
    pub amount: f64,
    pub transaction_type: TransactionType,
    pub merchant: Option<String>,
    pub notes: Option<String>,
    pub tags: Vec<String>, // Parsed JSON array
    pub is_excluded: bool,
    pub external_source: Option<String>,
    pub external_id: Option<String>,
    pub created_at: i64,
    pub updated_at: i64,
    pub deleted_at: Option<i64>,
}

/// 规则模型
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    pub id: String,
    pub name: String,
    pub priority: i64,
    pub match_condition: String, // JSON definition
    pub action: String,          // JSON definition
    pub created_at: i64,
    pub updated_at: i64,
    pub deleted_at: Option<i64>,
}

/// 周期账单模型
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PeriodicBill {
    pub id: String,
    pub name: String,
    pub amount: f64,
    pub bill_type: TransactionType,
    pub category_id: Option<String>,
    pub account_id: String,
    pub cron_expression: String,
    pub next_date: i64,
    pub created_at: i64,
    pub updated_at: i64,
    pub deleted_at: Option<i64>,
}

/// 待确认周期账单
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PendingConfirmation {
    pub id: String,
    pub periodic_bill_id: String,
    pub transaction_id: Option<String>,
    pub due_date: i64,
    pub amount: f64,
    pub bill_type: TransactionType,
    pub category_id: Option<String>,
    pub account_id: String,
    pub status: String,
    pub created_at: i64,
    pub updated_at: i64,
    pub deleted_at: Option<i64>,
}

/// 同步日志模型
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncLog {
    pub id: String,
    pub table_name: String,
    pub record_id: String,
    pub operation: String,
    pub payload: String,
    pub created_at: i64,
}
