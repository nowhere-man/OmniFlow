use crate::error::AppError;
use crate::models::TransactionType;
use serde::{Deserialize, Serialize};

/// The unified raw transaction model produced by all parsers
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RawTransaction {
    /// 交易时间 (Unix timestamp in seconds)
    pub transaction_date: i64,
    /// 交易类型
    pub transaction_type: TransactionType,
    /// 交易金额 (绝对值)
    pub amount: f64,
    /// 交易对方/商户
    pub merchant: Option<String>,
    /// 商品说明/摘要/备注
    pub notes: Option<String>,
    /// 是否不计收支 (从原始数据带入，或匹配特定关键字)
    pub is_excluded: bool,
    /// 第三方订单号
    pub external_id: Option<String>,
    /// 平台自带的分类建议 (用于初始化规则引擎建议)
    pub category_hint: Option<String>,
    /// 标签 (规则引擎可添加)
    #[serde(default)]
    pub tags: Vec<String>,
    /// 是否跳过不入账 (规则引擎设置)
    #[serde(default)]
    pub should_skip: bool,
}

pub trait BillParser: Send + Sync {
    /// 平台标识符，如 "alipay", "wechat"
    fn source_name(&self) -> &'static str;

    /// 校验文件格式是否匹配当前解析器 (比如检查表头)
    fn probe(&self, file_path: &str) -> Result<bool, AppError>;

    /// 解析文件并返回标准化的 RawTransaction 列表
    fn parse(&self, file_path: &str) -> Result<Vec<RawTransaction>, AppError>;
}

pub mod alipay;
pub mod ccb;
pub mod jd;
pub mod meituan;
pub mod qingzi;
pub mod wechat;
