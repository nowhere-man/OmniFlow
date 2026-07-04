use super::{BillParser, RawTransaction};
use crate::error::AppError;
use crate::models::TransactionType;
use chrono::NaiveDateTime;
use encoding_rs::GB18030;
use std::fs;
use std::io::Cursor;

pub struct AlipayParser;

impl AlipayParser {
    pub fn new() -> Self {
        Self
    }
}

impl BillParser for AlipayParser {
    fn source_name(&self) -> &'static str {
        "alipay"
    }

    fn probe(&self, file_path: &str) -> Result<bool, AppError> {
        let content = fs::read(file_path).map_err(|e| AppError::IoError(e.to_string()))?;
        let (decoded, _, _) = GB18030.decode(&content);
        Ok(decoded.contains("支付宝")
            && decoded.contains("交易订单号")
            && decoded.contains("收/付款方式"))
    }

    fn parse(&self, file_path: &str) -> Result<Vec<RawTransaction>, AppError> {
        let content = fs::read(file_path).map_err(|e| AppError::IoError(e.to_string()))?;
        let (decoded, _, _) = GB18030.decode(&content);
        let decoded_str = decoded.into_owned();

        let mut lines = decoded_str.lines().peekable();

        // Skip metadata until we find the header line or separator
        while let Some(&line) = lines.peek() {
            if line.contains("交易时间") && line.contains("交易分类") && line.contains("金额")
            {
                break;
            }
            if line.contains("--------------------------------") {
                lines.next();
                if let Some(&next_line) = lines.peek() {
                    if next_line.contains("交易时间") {
                        break;
                    }
                }
            }
            lines.next();
        }

        let csv_content = lines.collect::<Vec<_>>().join("\n");
        let mut reader = csv::ReaderBuilder::new()
            .has_headers(true)
            .trim(csv::Trim::All)
            .from_reader(Cursor::new(csv_content));

        let mut transactions = Vec::new();

        for result in reader.records() {
            let record = match result {
                Ok(r) => r,
                Err(_) => continue, // Skip malformed rows
            };

            // Expected columns:
            // 0: 交易时间
            // 1: 交易分类
            // 2: 交易对方
            // 3: 对方账号
            // 4: 商品说明
            // 5: 收/支
            // 6: 金额
            // 7: 收/付款方式
            // 8: 交易状态
            // 9: 交易订单号
            // 10: 商家订单号
            // 11: 备注

            if record.len() < 10 {
                continue; // Skip footer or empty lines
            }

            let date_str = record.get(0).unwrap_or("").trim();
            if date_str.is_empty() || !date_str.starts_with("20") {
                continue; // Skip footer summary lines
            }

            let dt = NaiveDateTime::parse_from_str(date_str, "%Y-%m-%d %H:%M:%S")
                .map_err(|e| AppError::ParseError(format!("Invalid date format: {}", e)))?;
            let transaction_date = dt.and_utc().timestamp();

            let category_hint = record.get(1).map(|s| s.trim().to_string());
            let merchant = record.get(2).map(|s| s.trim().to_string());
            let notes = record.get(4).map(|s| s.trim().to_string());

            let direction_str = record.get(5).unwrap_or("").trim();
            let amount_str = record.get(6).unwrap_or("0").trim().replace(",", "");
            let amount = amount_str.parse::<f64>().unwrap_or(0.0);

            let external_id = record.get(9).map(|s| s.trim().to_string());

            let mut is_excluded = false;
            let transaction_type = match direction_str {
                "收入" => TransactionType::Income,
                "支出" => TransactionType::Expense,
                "不计收支" => {
                    is_excluded = true;
                    if amount >= 0.0 {
                        TransactionType::Income
                    } else {
                        TransactionType::Expense
                    }
                }
                _ => {
                    // Fallback
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
                merchant,
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
    fn parses_alipay_fixture_records_and_core_fields() {
        let parser = AlipayParser::new();
        let path = fixture_path("支付宝.csv");
        let path = path.to_str().expect("fixture path should be valid UTF-8");

        assert!(parser.probe(path).expect("fixture should be probed"));

        let transactions = parser.parse(path).expect("fixture should parse");
        assert_eq!(transactions.len(), 107);

        let first = &transactions[0];
        assert_eq!(first.amount, 259.64);
        assert_eq!(first.transaction_type, TransactionType::Expense);
        assert_eq!(first.category_hint.as_deref(), Some("服饰装扮"));
        assert_eq!(first.merchant.as_deref(), Some("马克**店"));
        assert_eq!(
            first.external_id.as_deref(),
            Some("2026063023001119511448988856")
        );
        assert!(!first.is_excluded);
        assert!(first.tags.is_empty());
        assert!(!first.should_skip);
    }
}
