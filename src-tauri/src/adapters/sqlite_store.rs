use rusqlite::{params, Connection, OptionalExtension};
use std::sync::Mutex;

use crate::error::AppError;
use crate::models::{
    Account, AccountType, Category, Ledger, PendingConfirmation, PeriodicBill, Rule, Transaction,
    TransactionType,
};
use crate::ports::storage::{LedgerStore, SearchResult, TransactionFilter, TransactionPage};
use std::str::FromStr;

pub struct SqliteStore {
    conn: Mutex<Connection>,
}

impl SqliteStore {
    pub fn new(db_path: &str) -> Result<Self, AppError> {
        let conn = Connection::open(db_path)?;
        conn.execute("PRAGMA foreign_keys = ON;", [])?;
        Ok(Self {
            conn: Mutex::new(conn),
        })
    }

    pub fn get_conn(&self) -> &Mutex<Connection> {
        &self.conn
    }

    pub fn run_migrations(&self) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let init_sql = include_str!("../../migrations/001_init.sql");
        conn.execute_batch(init_sql)?;
        Ok(())
    }

    pub fn init_defaults(&self) -> Result<(), AppError> {
        use ulid::Ulid;
        let now = chrono::Utc::now().timestamp();

        let default_ledger = Ledger {
            id: Ulid::new().to_string(),
            name: "默认账本".to_string(),
            budget: 0.0,
            created_at: now,
            updated_at: now,
            deleted_at: None,
        };

        let cash_account = Account {
            id: Ulid::new().to_string(),
            name: "现金".to_string(),
            account_type: AccountType::Cash,
            balance: 0.0,
            credit_limit: 0.0,
            bill_day: None,
            repay_day: None,
            created_at: now,
            updated_at: now,
            deleted_at: None,
        };

        self.create_ledger(&default_ledger)?;
        self.create_account(&cash_account)?;

        // Default Expense Categories
        let expenses = vec![
            "餐饮美食",
            "交通出行",
            "日用百货",
            "居住服务",
            "休闲娱乐",
            "医疗健康",
            "教育培训",
            "充值缴费",
            "其他支出",
        ];
        for name in expenses {
            self.create_category(&Category {
                id: Ulid::new().to_string(),
                name: name.to_string(),
                category_type: TransactionType::Expense,
                parent_id: None,
                icon: None,
                created_at: now,
                updated_at: now,
                deleted_at: None,
            })?;
        }

        // Default Income Categories
        let incomes = vec!["工资", "投资理财", "兼职副业", "奖金红包", "其他收入"];
        for name in incomes {
            self.create_category(&Category {
                id: Ulid::new().to_string(),
                name: name.to_string(),
                category_type: TransactionType::Income,
                parent_id: None,
                icon: None,
                created_at: now,
                updated_at: now,
                deleted_at: None,
            })?;
        }

        Ok(())
    }

    fn row_to_transaction(row: &rusqlite::Row) -> rusqlite::Result<Transaction> {
        let type_str: String = row.get(6)?;
        let transaction_type =
            TransactionType::from_str(&type_str).unwrap_or(TransactionType::Expense);
        let tags_str: String = row.get(9)?;
        let tags: Vec<String> = serde_json::from_str(&tags_str).unwrap_or_default();

        Ok(Transaction {
            id: row.get(0)?,
            ledger_id: row.get(1)?,
            account_id: row.get(2)?,
            category_id: row.get(3)?,
            transaction_date: row.get(4)?,
            amount: row.get(5)?,
            transaction_type,
            merchant: row.get(7)?,
            notes: row.get(8)?,
            tags,
            is_excluded: row.get(10)?,
            external_source: row.get(11)?,
            external_id: row.get(12)?,
            created_at: row.get(13)?,
            updated_at: row.get(14)?,
            deleted_at: row.get(15)?,
        })
    }
}

impl LedgerStore for SqliteStore {
    // ---- Ledgers ----
    fn create_ledger(&self, ledger: &Ledger) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO ledgers (id, name, budget, created_at, updated_at, deleted_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![ledger.id, ledger.name, ledger.budget, ledger.created_at, ledger.updated_at, ledger.deleted_at],
        )?;
        Ok(())
    }

    fn list_ledgers(&self) -> Result<Vec<Ledger>, AppError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, name, budget, created_at, updated_at, deleted_at FROM ledgers WHERE deleted_at IS NULL")?;
        let ledgers = stmt
            .query_map([], |row| {
                Ok(Ledger {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    budget: row.get(2)?,
                    created_at: row.get(3)?,
                    updated_at: row.get(4)?,
                    deleted_at: row.get(5)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(ledgers)
    }

    fn update_ledger(&self, ledger: &Ledger) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE ledgers SET name = ?1, budget = ?2, updated_at = ?3, deleted_at = ?4 WHERE id = ?5",
            params![ledger.name, ledger.budget, ledger.updated_at, ledger.deleted_at, ledger.id],
        )?;
        Ok(())
    }

    fn delete_ledger(&self, id: &str) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().timestamp();
        conn.execute(
            "UPDATE ledgers SET deleted_at = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    // ---- Accounts ----
    fn create_account(&self, account: &Account) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO accounts (id, name, account_type, balance, credit_limit, bill_day, repay_day, created_at, updated_at, deleted_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
            params![
                account.id,
                account.name,
                account.account_type.to_string(),
                account.balance,
                account.credit_limit,
                account.bill_day,
                account.repay_day,
                account.created_at,
                account.updated_at,
                account.deleted_at
            ],
        )?;
        Ok(())
    }

    fn list_accounts(&self) -> Result<Vec<Account>, AppError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, name, account_type, balance, credit_limit, bill_day, repay_day, created_at, updated_at, deleted_at FROM accounts WHERE deleted_at IS NULL")?;
        let accounts = stmt
            .query_map([], |row| {
                let type_str: String = row.get(2)?;
                let account_type = AccountType::from_str(&type_str).unwrap_or(AccountType::Other);
                Ok(Account {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    account_type,
                    balance: row.get(3)?,
                    credit_limit: row.get(4)?,
                    bill_day: row.get(5)?,
                    repay_day: row.get(6)?,
                    created_at: row.get(7)?,
                    updated_at: row.get(8)?,
                    deleted_at: row.get(9)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(accounts)
    }

    fn update_account(&self, account: &Account) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE accounts SET name = ?1, account_type = ?2, balance = ?3, credit_limit = ?4, bill_day = ?5, repay_day = ?6, updated_at = ?7, deleted_at = ?8 WHERE id = ?9",
            params![
                account.name,
                account.account_type.to_string(),
                account.balance,
                account.credit_limit,
                account.bill_day,
                account.repay_day,
                account.updated_at,
                account.deleted_at,
                account.id
            ],
        )?;
        Ok(())
    }

    fn delete_account(&self, id: &str) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().timestamp();
        conn.execute(
            "UPDATE accounts SET deleted_at = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    // ---- Categories ----
    fn create_category(&self, category: &Category) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO categories (id, name, type, parent_id, icon, created_at, updated_at, deleted_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                category.id,
                category.name,
                category.category_type.to_string(),
                category.parent_id,
                category.icon,
                category.created_at,
                category.updated_at,
                category.deleted_at
            ],
        )?;
        Ok(())
    }

    fn list_categories(&self) -> Result<Vec<Category>, AppError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, name, type, parent_id, icon, created_at, updated_at, deleted_at FROM categories WHERE deleted_at IS NULL")?;
        let categories = stmt
            .query_map([], |row| {
                let type_str: String = row.get(2)?;
                let category_type =
                    TransactionType::from_str(&type_str).unwrap_or(TransactionType::Expense);
                Ok(Category {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    category_type,
                    parent_id: row.get(3)?,
                    icon: row.get(4)?,
                    created_at: row.get(5)?,
                    updated_at: row.get(6)?,
                    deleted_at: row.get(7)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(categories)
    }

    fn update_category(&self, category: &Category) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE categories SET name = ?1, type = ?2, parent_id = ?3, icon = ?4, updated_at = ?5, deleted_at = ?6 WHERE id = ?7",
            params![
                category.name,
                category.category_type.to_string(),
                category.parent_id,
                category.icon,
                category.updated_at,
                category.deleted_at,
                category.id
            ],
        )?;
        Ok(())
    }

    fn delete_category(&self, id: &str) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().timestamp();
        conn.execute(
            "UPDATE categories SET deleted_at = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    // ---- Transactions ----
    fn insert_transaction(&self, tx: &Transaction) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let tags_json = serde_json::to_string(&tx.tags).unwrap_or_else(|_| "[]".to_string());
        conn.execute(
            "INSERT INTO transactions (id, ledger_id, account_id, category_id, transaction_date, amount, type, merchant, notes, tags, is_excluded, external_source, external_id, created_at, updated_at, deleted_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
            params![
                tx.id,
                tx.ledger_id,
                tx.account_id,
                tx.category_id,
                tx.transaction_date,
                tx.amount,
                tx.transaction_type.to_string(),
                tx.merchant,
                tx.notes,
                tags_json,
                tx.is_excluded,
                tx.external_source,
                tx.external_id,
                tx.created_at,
                tx.updated_at,
                tx.deleted_at
            ],
        )?;
        Ok(())
    }

    fn insert_transactions(&self, transactions: &[Transaction]) -> Result<(), AppError> {
        let mut conn = self.conn.lock().unwrap();
        let tx = conn.transaction()?;
        for t in transactions {
            let tags_json = serde_json::to_string(&t.tags).unwrap_or_else(|_| "[]".to_string());
            tx.execute(
                "INSERT INTO transactions (id, ledger_id, account_id, category_id, transaction_date, amount, type, merchant, notes, tags, is_excluded, external_source, external_id, created_at, updated_at, deleted_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
                params![
                    t.id, t.ledger_id, t.account_id, t.category_id, t.transaction_date, t.amount,
                    t.transaction_type.to_string(), t.merchant, t.notes, tags_json, t.is_excluded,
                    t.external_source, t.external_id, t.created_at, t.updated_at, t.deleted_at
                ],
            )?;
        }
        tx.commit()?;
        Ok(())
    }

    fn get_transaction(&self, id: &str) -> Result<Option<Transaction>, AppError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, ledger_id, account_id, category_id, transaction_date, amount, type, merchant, notes, tags, is_excluded, external_source, external_id, created_at, updated_at, deleted_at FROM transactions WHERE id = ?1 AND deleted_at IS NULL")?;
        let result = stmt
            .query_row(params![id], Self::row_to_transaction)
            .optional()?;
        Ok(result)
    }

    fn list_transactions(&self, ledger_id: &str) -> Result<Vec<Transaction>, AppError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, ledger_id, account_id, category_id, transaction_date, amount, type, merchant, notes, tags, is_excluded, external_source, external_id, created_at, updated_at, deleted_at FROM transactions WHERE ledger_id = ?1 AND deleted_at IS NULL ORDER BY transaction_date DESC")?;
        let transactions = stmt
            .query_map(params![ledger_id], Self::row_to_transaction)?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(transactions)
    }

    fn list_transactions_page(
        &self,
        ledger_id: &str,
        offset: i64,
        limit: i64,
    ) -> Result<TransactionPage, AppError> {
        let conn = self.conn.lock().unwrap();
        let total: i64 = conn.query_row(
            "SELECT COUNT(*) FROM transactions WHERE ledger_id = ?1 AND deleted_at IS NULL",
            params![ledger_id],
            |row| row.get(0),
        )?;
        let safe_offset = offset.max(0);
        let safe_limit = limit.clamp(1, 300);
        let mut stmt = conn.prepare(
            "SELECT id, ledger_id, account_id, category_id, transaction_date, amount, type, merchant, notes, tags, is_excluded, external_source, external_id, created_at, updated_at, deleted_at
             FROM transactions
             WHERE ledger_id = ?1 AND deleted_at IS NULL
             ORDER BY transaction_date DESC
             LIMIT ?2 OFFSET ?3",
        )?;
        let transactions = stmt
            .query_map(params![ledger_id, safe_limit, safe_offset], Self::row_to_transaction)?
            .collect::<Result<Vec<_>, _>>()?;

        Ok(TransactionPage {
            transactions,
            total,
        })
    }

    fn search_transactions(
        &self,
        ledger_id: &str,
        filter: &TransactionFilter,
    ) -> Result<SearchResult, AppError> {
        let conn = self.conn.lock().unwrap();
        let mut sql = String::from(
            "SELECT id, ledger_id, account_id, category_id, transaction_date, amount, type, merchant, notes, tags, is_excluded, external_source, external_id, created_at, updated_at, deleted_at FROM transactions WHERE ledger_id = ?1 AND deleted_at IS NULL AND is_excluded = 0"
        );
        let mut count_params_idx = 2u32;
        let mut param_values: Vec<Box<dyn rusqlite::types::ToSql>> =
            vec![Box::new(ledger_id.to_string())];

        if let Some(ref cat_id) = filter.category_id {
            sql.push_str(&format!(" AND category_id = ?{}", count_params_idx));
            param_values.push(Box::new(cat_id.clone()));
            count_params_idx += 1;
        }
        if let Some(ref parent_id) = filter.parent_category_id {
            sql.push_str(&format!(
                " AND (category_id = ?{idx} OR category_id IN (SELECT id FROM categories WHERE parent_id = ?{child_idx} AND deleted_at IS NULL))",
                idx = count_params_idx,
                child_idx = count_params_idx + 1
            ));
            param_values.push(Box::new(parent_id.clone()));
            param_values.push(Box::new(parent_id.clone()));
            count_params_idx += 2;
        }
        if let Some(ref acc_id) = filter.account_id {
            sql.push_str(&format!(" AND account_id = ?{}", count_params_idx));
            param_values.push(Box::new(acc_id.clone()));
            count_params_idx += 1;
        }
        if let Some(ref tag) = filter.tag {
            sql.push_str(&format!(" AND tags LIKE ?{}", count_params_idx));
            param_values.push(Box::new(format!("%\"{}\"%%", tag)));
            count_params_idx += 1;
        }
        if let Some(start) = filter.start_date {
            sql.push_str(&format!(" AND transaction_date >= ?{}", count_params_idx));
            param_values.push(Box::new(start));
            count_params_idx += 1;
        }
        if let Some(end) = filter.end_date {
            sql.push_str(&format!(" AND transaction_date <= ?{}", count_params_idx));
            param_values.push(Box::new(end));
            count_params_idx += 1;
        }
        if let Some(ref kw) = filter.keyword {
            sql.push_str(&format!(
                " AND (merchant LIKE ?{idx} OR notes LIKE ?{idx})",
                idx = count_params_idx
            ));
            param_values.push(Box::new(format!("%{}%", kw)));
            count_params_idx += 1;
        }
        if let Some(min_a) = filter.min_amount {
            sql.push_str(&format!(" AND amount >= ?{}", count_params_idx));
            param_values.push(Box::new(min_a));
            count_params_idx += 1;
        }
        if let Some(max_a) = filter.max_amount {
            sql.push_str(&format!(" AND amount <= ?{}", count_params_idx));
            param_values.push(Box::new(max_a));
        }

        sql.push_str(" ORDER BY transaction_date DESC");

        let params_refs: Vec<&dyn rusqlite::types::ToSql> =
            param_values.iter().map(|b| b.as_ref()).collect();
        let mut stmt = conn.prepare(&sql)?;
        let transactions: Vec<Transaction> = stmt
            .query_map(params_refs.as_slice(), Self::row_to_transaction)?
            .collect::<Result<Vec<_>, _>>()?;

        let mut total_income = 0.0;
        let mut total_expense = 0.0;
        for t in &transactions {
            if t.is_excluded {
                continue;
            }
            match t.transaction_type {
                TransactionType::Income => total_income += t.amount,
                TransactionType::Expense => total_expense += t.amount,
            }
        }

        Ok(SearchResult {
            transactions,
            total_income,
            total_expense,
        })
    }

    fn update_transaction(&self, tx: &Transaction) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let tags_json = serde_json::to_string(&tx.tags).unwrap_or_else(|_| "[]".to_string());
        conn.execute(
            "UPDATE transactions SET account_id = ?1, category_id = ?2, transaction_date = ?3, amount = ?4, type = ?5, merchant = ?6, notes = ?7, tags = ?8, is_excluded = ?9, updated_at = ?10, deleted_at = ?11 WHERE id = ?12",
            params![
                tx.account_id,
                tx.category_id,
                tx.transaction_date,
                tx.amount,
                tx.transaction_type.to_string(),
                tx.merchant,
                tx.notes,
                tags_json,
                tx.is_excluded,
                tx.updated_at,
                tx.deleted_at,
                tx.id
            ],
        )?;
        Ok(())
    }

    fn delete_transaction(&self, id: &str) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().timestamp();
        conn.execute(
            "UPDATE transactions SET deleted_at = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    // ---- Rules ----
    fn create_rule(&self, rule: &Rule) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO rules (id, name, priority, match_condition, action, created_at, updated_at, deleted_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![rule.id, rule.name, rule.priority, rule.match_condition, rule.action, rule.created_at, rule.updated_at, rule.deleted_at],
        )?;
        Ok(())
    }

    fn list_rules(&self) -> Result<Vec<Rule>, AppError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, name, priority, match_condition, action, created_at, updated_at, deleted_at FROM rules WHERE deleted_at IS NULL ORDER BY priority DESC")?;
        let rules = stmt
            .query_map([], |row| {
                Ok(Rule {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    priority: row.get(2)?,
                    match_condition: row.get(3)?,
                    action: row.get(4)?,
                    created_at: row.get(5)?,
                    updated_at: row.get(6)?,
                    deleted_at: row.get(7)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rules)
    }

    fn update_rule(&self, rule: &Rule) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE rules SET name = ?1, priority = ?2, match_condition = ?3, action = ?4, updated_at = ?5, deleted_at = ?6 WHERE id = ?7",
            params![rule.name, rule.priority, rule.match_condition, rule.action, rule.updated_at, rule.deleted_at, rule.id],
        )?;
        Ok(())
    }

    fn delete_rule(&self, id: &str) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().timestamp();
        conn.execute(
            "UPDATE rules SET deleted_at = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    // ---- Periodic Bills ----
    fn create_periodic_bill(&self, bill: &PeriodicBill) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO periodic_bills (id, name, amount, type, category_id, account_id, cron_expression, next_date, created_at, updated_at, deleted_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
            params![
                bill.id, bill.name, bill.amount, bill.bill_type.to_string(),
                bill.category_id, bill.account_id, bill.cron_expression,
                bill.next_date, bill.created_at, bill.updated_at, bill.deleted_at
            ],
        )?;
        Ok(())
    }

    fn list_periodic_bills(&self) -> Result<Vec<PeriodicBill>, AppError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, name, amount, type, category_id, account_id, cron_expression, next_date, created_at, updated_at, deleted_at FROM periodic_bills WHERE deleted_at IS NULL")?;
        let bills = stmt
            .query_map([], |row| {
                let type_str: String = row.get(3)?;
                let bill_type =
                    TransactionType::from_str(&type_str).unwrap_or(TransactionType::Expense);
                Ok(PeriodicBill {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    amount: row.get(2)?,
                    bill_type,
                    category_id: row.get(4)?,
                    account_id: row.get(5)?,
                    cron_expression: row.get(6)?,
                    next_date: row.get(7)?,
                    created_at: row.get(8)?,
                    updated_at: row.get(9)?,
                    deleted_at: row.get(10)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(bills)
    }

    fn update_periodic_bill(&self, bill: &PeriodicBill) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE periodic_bills SET name = ?1, amount = ?2, type = ?3, category_id = ?4, account_id = ?5, cron_expression = ?6, next_date = ?7, updated_at = ?8, deleted_at = ?9 WHERE id = ?10",
            params![
                bill.name, bill.amount, bill.bill_type.to_string(),
                bill.category_id, bill.account_id, bill.cron_expression,
                bill.next_date, bill.updated_at, bill.deleted_at, bill.id
            ],
        )?;
        Ok(())
    }

    fn delete_periodic_bill(&self, id: &str) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().timestamp();
        conn.execute(
            "UPDATE periodic_bills SET deleted_at = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    // ---- Pending Confirmations ----
    fn create_pending_confirmation(&self, pending: &PendingConfirmation) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO pending_confirmations (id, periodic_bill_id, transaction_id, due_date, amount, type, category_id, account_id, status, created_at, updated_at, deleted_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)",
            params![
                pending.id,
                pending.periodic_bill_id,
                pending.transaction_id,
                pending.due_date,
                pending.amount,
                pending.bill_type.to_string(),
                pending.category_id,
                pending.account_id,
                pending.status,
                pending.created_at,
                pending.updated_at,
                pending.deleted_at
            ],
        )?;
        Ok(())
    }

    fn list_pending_confirmations(&self) -> Result<Vec<PendingConfirmation>, AppError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, periodic_bill_id, transaction_id, due_date, amount, type, category_id, account_id, status, created_at, updated_at, deleted_at FROM pending_confirmations WHERE deleted_at IS NULL ORDER BY due_date ASC")?;
        let pending = stmt
            .query_map([], |row| {
                let type_str: String = row.get(5)?;
                let bill_type =
                    TransactionType::from_str(&type_str).unwrap_or(TransactionType::Expense);
                Ok(PendingConfirmation {
                    id: row.get(0)?,
                    periodic_bill_id: row.get(1)?,
                    transaction_id: row.get(2)?,
                    due_date: row.get(3)?,
                    amount: row.get(4)?,
                    bill_type,
                    category_id: row.get(6)?,
                    account_id: row.get(7)?,
                    status: row.get(8)?,
                    created_at: row.get(9)?,
                    updated_at: row.get(10)?,
                    deleted_at: row.get(11)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(pending)
    }

    fn update_pending_confirmation(&self, pending: &PendingConfirmation) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE pending_confirmations SET transaction_id = ?1, due_date = ?2, amount = ?3, type = ?4, category_id = ?5, account_id = ?6, status = ?7, updated_at = ?8, deleted_at = ?9 WHERE id = ?10",
            params![
                pending.transaction_id,
                pending.due_date,
                pending.amount,
                pending.bill_type.to_string(),
                pending.category_id,
                pending.account_id,
                pending.status,
                pending.updated_at,
                pending.deleted_at,
                pending.id
            ],
        )?;
        Ok(())
    }

    fn delete_pending_confirmation(&self, id: &str) -> Result<(), AppError> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().timestamp();
        conn.execute(
            "UPDATE pending_confirmations SET deleted_at = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::core::stats_engine::StatsEngine;

    fn transaction(
        id: &str,
        ledger_id: &str,
        account_id: &str,
        amount: f64,
        transaction_type: TransactionType,
        is_excluded: bool,
    ) -> Transaction {
        Transaction {
            id: id.to_string(),
            ledger_id: ledger_id.to_string(),
            account_id: account_id.to_string(),
            category_id: None,
            transaction_date: 1_700_000_000,
            amount,
            transaction_type,
            merchant: Some("测试商户".to_string()),
            notes: None,
            tags: vec![],
            is_excluded,
            external_source: None,
            external_id: None,
            created_at: 1_700_000_000,
            updated_at: 1_700_000_000,
            deleted_at: None,
        }
    }

    #[test]
    fn search_and_stats_ignore_excluded_transactions() {
        let store = SqliteStore::new(":memory:").expect("store should open");
        store.run_migrations().expect("migrations should run");

        let ledger = Ledger {
            id: "ledger".to_string(),
            name: "默认账本".to_string(),
            budget: 0.0,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let account = Account {
            id: "account".to_string(),
            name: "现金".to_string(),
            account_type: AccountType::Cash,
            balance: 0.0,
            credit_limit: 0.0,
            bill_day: None,
            repay_day: None,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };

        store
            .create_ledger(&ledger)
            .expect("ledger should be saved");
        store
            .create_account(&account)
            .expect("account should be saved");
        store
            .insert_transactions(&[
                transaction(
                    "expense",
                    &ledger.id,
                    &account.id,
                    100.0,
                    TransactionType::Expense,
                    false,
                ),
                transaction(
                    "excluded-expense",
                    &ledger.id,
                    &account.id,
                    900.0,
                    TransactionType::Expense,
                    true,
                ),
                transaction(
                    "income",
                    &ledger.id,
                    &account.id,
                    300.0,
                    TransactionType::Income,
                    false,
                ),
                transaction(
                    "excluded-income",
                    &ledger.id,
                    &account.id,
                    700.0,
                    TransactionType::Income,
                    true,
                ),
            ])
            .expect("transactions should be saved");

        let search = store
            .search_transactions(&ledger.id, &TransactionFilter::default())
            .expect("search should succeed");
        let mut ids: Vec<&str> = search
            .transactions
            .iter()
            .map(|tx| tx.id.as_str())
            .collect();
        ids.sort_unstable();
        assert_eq!(ids, vec!["expense", "income"]);
        assert_eq!(search.total_expense, 100.0);
        assert_eq!(search.total_income, 300.0);

        let stats = StatsEngine::new(store.get_conn())
            .get_monthly_trend(&ledger.id, 1_600_000_000, 1_800_000_000)
            .expect("stats should succeed");
        assert_eq!(stats.len(), 1);
        assert_eq!(stats[0].expense, 100.0);
        assert_eq!(stats[0].income, 300.0);

        let summary = StatsEngine::new(store.get_conn())
            .get_dashboard_summary(&ledger.id, 1_600_000_000, 1_800_000_000)
            .expect("dashboard summary should succeed");
        assert_eq!(summary.expense, 100.0);
        assert_eq!(summary.income, 300.0);
        assert_eq!(summary.net_cash_flow, 200.0);
        assert_eq!(summary.transaction_count, 2);
    }

    #[test]
    fn search_combines_parent_category_tag_account_keyword_amount_and_date_filters() {
        let store = SqliteStore::new(":memory:").expect("store should open");
        store.run_migrations().expect("migrations should run");

        let ledger = Ledger {
            id: "ledger".to_string(),
            name: "默认账本".to_string(),
            budget: 0.0,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let account = Account {
            id: "wallet".to_string(),
            name: "钱包".to_string(),
            account_type: AccountType::Wallet,
            balance: 0.0,
            credit_limit: 0.0,
            bill_day: None,
            repay_day: None,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let other_account = Account {
            id: "cash".to_string(),
            name: "现金".to_string(),
            account_type: AccountType::Cash,
            balance: 0.0,
            credit_limit: 0.0,
            bill_day: None,
            repay_day: None,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let parent = Category {
            id: "food".to_string(),
            name: "餐饮".to_string(),
            category_type: TransactionType::Expense,
            parent_id: None,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let child = Category {
            id: "coffee".to_string(),
            name: "咖啡".to_string(),
            category_type: TransactionType::Expense,
            parent_id: Some(parent.id.clone()),
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let transport = Category {
            id: "transport".to_string(),
            name: "交通".to_string(),
            category_type: TransactionType::Expense,
            parent_id: None,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };

        store
            .create_ledger(&ledger)
            .expect("ledger should be saved");
        store
            .create_account(&account)
            .expect("account should be saved");
        store
            .create_account(&other_account)
            .expect("other account should be saved");
        store
            .create_category(&parent)
            .expect("parent should be saved");
        store
            .create_category(&child)
            .expect("child should be saved");
        store
            .create_category(&transport)
            .expect("category should be saved");

        let mut matched = transaction(
            "matched",
            &ledger.id,
            &account.id,
            48.0,
            TransactionType::Expense,
            false,
        );
        matched.category_id = Some(child.id.clone());
        matched.transaction_date = 1_700_000_100;
        matched.merchant = Some("蓝瓶咖啡".to_string());
        matched.tags = vec!["约会".to_string()];

        let mut wrong_category = transaction(
            "wrong-category",
            &ledger.id,
            &account.id,
            48.0,
            TransactionType::Expense,
            false,
        );
        wrong_category.category_id = Some(transport.id.clone());
        wrong_category.transaction_date = 1_700_000_100;
        wrong_category.merchant = Some("蓝瓶咖啡".to_string());
        wrong_category.tags = vec!["约会".to_string()];

        let mut wrong_account = matched.clone();
        wrong_account.id = "wrong-account".to_string();
        wrong_account.account_id = other_account.id.clone();

        store
            .insert_transactions(&[matched, wrong_category, wrong_account])
            .expect("transactions should be saved");

        let result = store
            .search_transactions(
                &ledger.id,
                &TransactionFilter {
                    parent_category_id: Some(parent.id),
                    account_id: Some(account.id),
                    tag: Some("约会".to_string()),
                    keyword: Some("咖啡".to_string()),
                    min_amount: Some(40.0),
                    max_amount: Some(60.0),
                    start_date: Some(1_700_000_000),
                    end_date: Some(1_700_000_200),
                    ..TransactionFilter::default()
                },
            )
            .expect("search should succeed");

        assert_eq!(result.transactions.len(), 1);
        assert_eq!(result.transactions[0].id, "matched");
        assert_eq!(result.total_expense, 48.0);
        assert_eq!(result.total_income, 0.0);
    }
}
