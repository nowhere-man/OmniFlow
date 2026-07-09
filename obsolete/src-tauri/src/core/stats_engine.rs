use crate::error::AppError;
use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};
use std::sync::Mutex;

#[derive(Debug, Serialize, Deserialize)]
pub struct TrendDataPoint {
    pub date: String,
    pub income: f64,
    pub expense: f64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct CategoryBreakdown {
    pub category_name: String,
    pub amount: f64,
    pub percent: f64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct AssetData {
    pub account_type: String,
    pub balance: f64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ComparisonData {
    pub current_income: f64,
    pub current_expense: f64,
    pub previous_income: f64,
    pub previous_expense: f64,
    pub income_change_percent: f64,
    pub expense_change_percent: f64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TagStat {
    pub tag: String,
    pub income: f64,
    pub expense: f64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct RankedTransaction {
    pub id: String,
    pub amount: f64,
    pub merchant: Option<String>,
    pub notes: Option<String>,
    pub transaction_date: i64,
    pub transaction_type: String,
    pub category_name: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DashboardSummary {
    pub income: f64,
    pub expense: f64,
    pub net_cash_flow: f64,
    pub net_assets: f64,
    pub transaction_count: i64,
}

pub struct StatsEngine<'a> {
    conn: &'a Mutex<Connection>,
}

impl<'a> StatsEngine<'a> {
    pub fn new(conn: &'a Mutex<Connection>) -> Self {
        Self { conn }
    }

    /// Trend with configurable granularity: "day", "week", "month", "year"
    pub fn get_trend(
        &self,
        ledger_id: &str,
        start_ts: i64,
        end_ts: i64,
        granularity: &str,
    ) -> Result<Vec<TrendDataPoint>, AppError> {
        let conn = self.conn.lock().unwrap();
        let date_format = match granularity {
            "day" => "%Y-%m-%d",
            "week" => "%Y-W%W",
            "month" => "%Y-%m",
            "year" => "%Y",
            _ => "%Y-%m",
        };
        let query = format!(
            "SELECT 
                strftime('{}', transaction_date, 'unixepoch') as period,
                SUM(CASE WHEN type = 'income' AND is_excluded = 0 THEN amount ELSE 0 END) as income,
                SUM(CASE WHEN type = 'expense' AND is_excluded = 0 THEN amount ELSE 0 END) as expense
            FROM transactions
            WHERE ledger_id = ?1 AND deleted_at IS NULL AND transaction_date BETWEEN ?2 AND ?3
            GROUP BY period
            ORDER BY period ASC",
            date_format
        );
        let mut stmt = conn.prepare(&query)?;
        let rows = stmt
            .query_map(params![ledger_id, start_ts, end_ts], |row| {
                Ok(TrendDataPoint {
                    date: row.get(0)?,
                    income: row.get(1)?,
                    expense: row.get(2)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;

        Ok(rows)
    }

    /// Monthly trend (backward compatible)
    pub fn get_monthly_trend(
        &self,
        ledger_id: &str,
        start_ts: i64,
        end_ts: i64,
    ) -> Result<Vec<TrendDataPoint>, AppError> {
        self.get_trend(ledger_id, start_ts, end_ts, "month")
    }

    /// Category breakdown
    pub fn get_category_breakdown(
        &self,
        ledger_id: &str,
        start_ts: i64,
        end_ts: i64,
        tx_type: &str,
    ) -> Result<Vec<CategoryBreakdown>, AppError> {
        let conn = self.conn.lock().unwrap();
        let query = "
            SELECT 
                COALESCE(c.name, '未分类') as category_name,
                SUM(t.amount) as amount
            FROM transactions t
            LEFT JOIN categories c ON t.category_id = c.id
            WHERE t.ledger_id = ?1 AND t.type = ?2 AND t.is_excluded = 0 AND t.deleted_at IS NULL AND t.transaction_date BETWEEN ?3 AND ?4
            GROUP BY category_name
            ORDER BY amount DESC
        ";
        let mut stmt = conn.prepare(query)?;
        let mut total = 0.0;
        let mut results = Vec::new();

        let rows = stmt.query_map(params![ledger_id, tx_type, start_ts, end_ts], |row| {
            let amount: f64 = row.get(1)?;
            Ok((row.get::<_, String>(0)?, amount))
        })?;

        for row in rows {
            let (name, amount) = row?;
            total += amount;
            results.push(CategoryBreakdown {
                category_name: name,
                amount,
                percent: 0.0,
            });
        }

        if total > 0.0 {
            for r in &mut results {
                r.percent = (r.amount / total * 100.0 * 10.0).round() / 10.0;
            }
        }

        Ok(results)
    }

    /// Assets overview
    pub fn get_assets_overview(&self) -> Result<Vec<AssetData>, AppError> {
        let conn = self.conn.lock().unwrap();
        let query = "
            SELECT account_type, SUM(balance) as balance
            FROM accounts
            WHERE deleted_at IS NULL
            GROUP BY account_type
            ORDER BY balance DESC
        ";
        let mut stmt = conn.prepare(query)?;
        let rows = stmt
            .query_map([], |row| {
                Ok(AssetData {
                    account_type: row.get(0)?,
                    balance: row.get(1)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;

        Ok(rows)
    }

    pub fn get_dashboard_summary(
        &self,
        ledger_id: &str,
        start_ts: i64,
        end_ts: i64,
    ) -> Result<DashboardSummary, AppError> {
        let conn = self.conn.lock().unwrap();
        let (income, expense, transaction_count): (f64, f64, i64) = conn.query_row(
            "
            SELECT
                COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0),
                COUNT(*)
            FROM transactions
            WHERE ledger_id = ?1 AND is_excluded = 0 AND deleted_at IS NULL
              AND transaction_date BETWEEN ?2 AND ?3
            ",
            params![ledger_id, start_ts, end_ts],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
        )?;
        let net_assets: f64 = conn.query_row(
            "SELECT COALESCE(SUM(balance), 0) FROM accounts WHERE deleted_at IS NULL",
            [],
            |row| row.get(0),
        )?;

        Ok(DashboardSummary {
            income,
            expense,
            net_cash_flow: income - expense,
            net_assets,
            transaction_count,
        })
    }

    /// Comparison analysis: current period vs previous period
    /// period_type: "month" or "year"
    pub fn get_comparison(
        &self,
        ledger_id: &str,
        current_start: i64,
        current_end: i64,
        previous_start: i64,
        previous_end: i64,
    ) -> Result<ComparisonData, AppError> {
        let conn = self.conn.lock().unwrap();
        let query = "
            SELECT 
                SUM(CASE WHEN type = 'income' AND is_excluded = 0 THEN amount ELSE 0 END) as income,
                SUM(CASE WHEN type = 'expense' AND is_excluded = 0 THEN amount ELSE 0 END) as expense
            FROM transactions
            WHERE ledger_id = ?1 AND deleted_at IS NULL AND transaction_date BETWEEN ?2 AND ?3
        ";

        let mut stmt = conn.prepare(query)?;
        let (current_income, current_expense): (f64, f64) = stmt
            .query_row(params![ledger_id, current_start, current_end], |row| {
                Ok((
                    row.get::<_, f64>(0).unwrap_or(0.0),
                    row.get::<_, f64>(1).unwrap_or(0.0),
                ))
            })
            .unwrap_or((0.0, 0.0));

        let (previous_income, previous_expense): (f64, f64) = stmt
            .query_row(params![ledger_id, previous_start, previous_end], |row| {
                Ok((
                    row.get::<_, f64>(0).unwrap_or(0.0),
                    row.get::<_, f64>(1).unwrap_or(0.0),
                ))
            })
            .unwrap_or((0.0, 0.0));

        let income_change = if previous_income > 0.0 {
            ((current_income - previous_income) / previous_income * 100.0 * 10.0).round() / 10.0
        } else {
            0.0
        };

        let expense_change = if previous_expense > 0.0 {
            ((current_expense - previous_expense) / previous_expense * 100.0 * 10.0).round() / 10.0
        } else {
            0.0
        };

        Ok(ComparisonData {
            current_income,
            current_expense,
            previous_income,
            previous_expense,
            income_change_percent: income_change,
            expense_change_percent: expense_change,
        })
    }

    /// Tag analysis: aggregate income/expense by tag
    pub fn get_tag_stats(
        &self,
        ledger_id: &str,
        start_ts: i64,
        end_ts: i64,
    ) -> Result<Vec<TagStat>, AppError> {
        let conn = self.conn.lock().unwrap();
        // Since tags are stored as JSON arrays, we need to query all transactions and aggregate in Rust
        let query = "
            SELECT tags, amount, type
            FROM transactions
            WHERE ledger_id = ?1 AND is_excluded = 0 AND deleted_at IS NULL AND transaction_date BETWEEN ?2 AND ?3
        ";
        let mut stmt = conn.prepare(query)?;
        let mut tag_stats: std::collections::HashMap<String, (f64, f64)> =
            std::collections::HashMap::new();

        let rows = stmt.query_map(params![ledger_id, start_ts, end_ts], |row| {
            let tags_str: String = row.get(0)?;
            let amount: f64 = row.get(1)?;
            let tx_type: String = row.get(2)?;
            Ok((tags_str, amount, tx_type))
        })?;

        for row in rows {
            let (tags_str, amount, tx_type) = row?;
            let tags: Vec<String> = serde_json::from_str(&tags_str).unwrap_or_default();
            for tag in tags {
                if tag.starts_with("__") {
                    continue;
                } // Skip internal tags
                let entry = tag_stats.entry(tag).or_insert((0.0, 0.0));
                if tx_type == "income" {
                    entry.0 += amount;
                } else {
                    entry.1 += amount;
                }
            }
        }

        let mut results: Vec<TagStat> = tag_stats
            .into_iter()
            .map(|(tag, (income, expense))| TagStat {
                tag,
                income,
                expense,
            })
            .collect();
        results.sort_by(|a, b| {
            (b.expense + b.income)
                .partial_cmp(&(a.expense + a.income))
                .unwrap_or(std::cmp::Ordering::Equal)
        });

        Ok(results)
    }

    /// Top N transactions by amount
    pub fn get_top_transactions(
        &self,
        ledger_id: &str,
        start_ts: i64,
        end_ts: i64,
        tx_type: &str,
        limit: i64,
    ) -> Result<Vec<RankedTransaction>, AppError> {
        let conn = self.conn.lock().unwrap();
        let query = "
            SELECT t.id, t.amount, t.merchant, t.notes, t.transaction_date, t.type,
                   COALESCE(c.name, '未分类') as category_name
            FROM transactions t
            LEFT JOIN categories c ON t.category_id = c.id
            WHERE t.ledger_id = ?1 AND t.type = ?2 AND t.is_excluded = 0 AND t.deleted_at IS NULL
              AND t.transaction_date BETWEEN ?3 AND ?4
            ORDER BY t.amount DESC
            LIMIT ?5
        ";
        let mut stmt = conn.prepare(query)?;
        let rows = stmt
            .query_map(
                params![ledger_id, tx_type, start_ts, end_ts, limit],
                |row| {
                    Ok(RankedTransaction {
                        id: row.get(0)?,
                        amount: row.get(1)?,
                        merchant: row.get(2)?,
                        notes: row.get(3)?,
                        transaction_date: row.get(4)?,
                        transaction_type: row.get(5)?,
                        category_name: row.get(6)?,
                    })
                },
            )?
            .collect::<Result<Vec<_>, _>>()?;

        Ok(rows)
    }
}
