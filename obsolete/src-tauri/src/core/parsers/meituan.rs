use super::{BillParser, RawTransaction};
use crate::error::AppError;
use crate::models::TransactionType;
use chrono::NaiveDateTime;
use std::fs;
use std::io::Cursor;

pub struct MeituanParser;

impl MeituanParser {
    pub fn new() -> Self {
        Self
    }
}

impl BillParser for MeituanParser {
    fn source_name(&self) -> &'static str {
        "meituan"
    }

    fn probe(&self, file_path: &str) -> Result<bool, AppError> {
        let content = fs::read_to_string(file_path).unwrap_or_default();
        Ok(content.contains("美团") && content.contains("交易单号"))
    }

    fn parse(&self, file_path: &str) -> Result<Vec<RawTransaction>, AppError> {
        let content =
            fs::read_to_string(file_path).map_err(|e| AppError::IoError(e.to_string()))?;
        let content = content.strip_prefix('\u{feff}').unwrap_or(&content);

        let mut lines = content.lines().peekable();

        while let Some(&line) = lines.peek() {
            if line.contains("交易成功时间") && line.contains("交易单号") {
                break;
            }
            lines.next();
        }

        let csv_content = lines.collect::<Vec<_>>().join("\n");
        let mut reader = csv::ReaderBuilder::new()
            .has_headers(true)
            .flexible(true)
            .trim(csv::Trim::All)
            .from_reader(Cursor::new(csv_content));

        let mut transactions = Vec::new();

        for result in reader.records() {
            let record = match result {
                Ok(r) => r,
                Err(_) => continue,
            };

            // Expected Meituan columns:
            // 0: 交易创建时间
            // 1: 交易成功时间
            // 2: 交易类型
            // 3: 订单标题
            // 4: 收/支
            // 5: 支付方式
            // 6: 订单金额
            // 7: 实付金额
            // 8: 交易单号

            if record.len() < 9 {
                continue;
            }

            let date_str = record.get(1).unwrap_or("").trim();
            if !date_str.starts_with("20") {
                continue;
            }

            let dt = NaiveDateTime::parse_from_str(date_str, "%Y-%m-%d %H:%M:%S")
                .map_err(|e| AppError::ParseError(format!("Invalid date format: {}", e)))?;
            let transaction_date = dt.and_utc().timestamp();

            let notes = record.get(3).map(|s| s.trim().to_string());
            let direction_str = record.get(4).unwrap_or("").trim();

            // Use actual paid amount if available, else use order amount
            let amount_str = record
                .get(7)
                .unwrap_or("0")
                .trim()
                .replace("¥", "")
                .replace(",", "");
            let amount = amount_str.parse::<f64>().unwrap_or(0.0);

            let external_id = record.get(8).map(|s| s.trim().to_string());

            // Meituan may not have category
            let category_hint = None;

            let mut is_excluded = false;
            let transaction_type = match direction_str {
                "收入" => TransactionType::Income,
                "支出" => TransactionType::Expense,
                "不计收支" | "退款" => {
                    is_excluded = true;
                    if amount >= 0.0 {
                        TransactionType::Income
                    } else {
                        TransactionType::Expense
                    }
                }
                _ => {
                    if amount >= 0.0 {
                        TransactionType::Income
                    } else {
                        TransactionType::Expense
                    }
                }
            };

            transactions.push(RawTransaction {
                transaction_date,
                transaction_type,
                amount: amount.abs(),
                merchant: Some("美团".to_string()),
                notes,
                is_excluded,
                external_id,
                category_hint,
                tags: Vec::new(),
                should_skip: false,
            });
        }

        Ok(transactions)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn fixture_path(file_name: &str) -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("..")
            .join("examples")
            .join(file_name)
    }

    #[test]
    fn parses_meituan_fixture_records_and_core_fields() {
        let parser = MeituanParser::new();
        let path = fixture_path("美团.csv");
        let path = path.to_str().expect("fixture path should be valid UTF-8");

        assert!(parser.probe(path).expect("fixture should be probed"));

        let transactions = parser.parse(path).expect("fixture should parse");
        assert_eq!(transactions.len(), 18);

        let first = &transactions[0];
        assert_eq!(first.amount, 14.0);
        assert_eq!(first.transaction_type, TransactionType::Expense);
        assert_eq!(first.merchant.as_deref(), Some("美团"));
        assert_eq!(first.notes.as_deref(), Some("线下支付-被扫-典雅造型"));
        assert_eq!(
            first.external_id.as_deref(),
            Some("26062911200701670001655049754811")
        );
        assert!(!first.is_excluded);
        assert!(first.category_hint.is_none());
    }
}
