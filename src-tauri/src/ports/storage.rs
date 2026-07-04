use crate::error::AppError;
use crate::models::{
    Account, Category, Ledger, PendingConfirmation, PeriodicBill, Rule, Transaction,
};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct TransactionFilter {
    pub category_id: Option<String>,
    pub parent_category_id: Option<String>,
    pub account_id: Option<String>,
    pub tag: Option<String>,
    pub start_date: Option<i64>,
    pub end_date: Option<i64>,
    pub keyword: Option<String>,
    pub min_amount: Option<f64>,
    pub max_amount: Option<f64>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SearchResult {
    pub transactions: Vec<Transaction>,
    pub total_income: f64,
    pub total_expense: f64,
}

pub trait LedgerStore: Send + Sync {
    // ---- Ledgers ----
    fn create_ledger(&self, ledger: &Ledger) -> Result<(), AppError>;
    fn list_ledgers(&self) -> Result<Vec<Ledger>, AppError>;
    fn update_ledger(&self, ledger: &Ledger) -> Result<(), AppError>;
    fn delete_ledger(&self, id: &str) -> Result<(), AppError>;

    // ---- Accounts ----
    fn create_account(&self, account: &Account) -> Result<(), AppError>;
    fn list_accounts(&self) -> Result<Vec<Account>, AppError>;
    fn update_account(&self, account: &Account) -> Result<(), AppError>;
    fn delete_account(&self, id: &str) -> Result<(), AppError>;

    // ---- Categories ----
    fn create_category(&self, category: &Category) -> Result<(), AppError>;
    fn list_categories(&self) -> Result<Vec<Category>, AppError>;
    fn update_category(&self, category: &Category) -> Result<(), AppError>;
    fn delete_category(&self, id: &str) -> Result<(), AppError>;

    // ---- Transactions ----
    fn insert_transaction(&self, transaction: &Transaction) -> Result<(), AppError>;
    fn insert_transactions(&self, transactions: &[Transaction]) -> Result<(), AppError>;
    fn get_transaction(&self, id: &str) -> Result<Option<Transaction>, AppError>;
    fn list_transactions(&self, ledger_id: &str) -> Result<Vec<Transaction>, AppError>;
    fn search_transactions(
        &self,
        ledger_id: &str,
        filter: &TransactionFilter,
    ) -> Result<SearchResult, AppError>;
    fn update_transaction(&self, transaction: &Transaction) -> Result<(), AppError>;
    fn delete_transaction(&self, id: &str) -> Result<(), AppError>;

    // ---- Rules ----
    fn create_rule(&self, rule: &Rule) -> Result<(), AppError>;
    fn list_rules(&self) -> Result<Vec<Rule>, AppError>;
    fn update_rule(&self, rule: &Rule) -> Result<(), AppError>;
    fn delete_rule(&self, id: &str) -> Result<(), AppError>;

    // ---- Periodic Bills ----
    fn create_periodic_bill(&self, bill: &PeriodicBill) -> Result<(), AppError>;
    fn list_periodic_bills(&self) -> Result<Vec<PeriodicBill>, AppError>;
    fn update_periodic_bill(&self, bill: &PeriodicBill) -> Result<(), AppError>;
    fn delete_periodic_bill(&self, id: &str) -> Result<(), AppError>;

    // ---- Pending Confirmations ----
    fn create_pending_confirmation(&self, pending: &PendingConfirmation) -> Result<(), AppError>;
    fn list_pending_confirmations(&self) -> Result<Vec<PendingConfirmation>, AppError>;
    fn update_pending_confirmation(&self, pending: &PendingConfirmation) -> Result<(), AppError>;
    fn delete_pending_confirmation(&self, id: &str) -> Result<(), AppError>;
}
