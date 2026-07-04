use super::{BillParser, RawTransaction};
use crate::error::AppError;
use crate::models::TransactionType;
use calamine::{open_workbook_auto, Reader};
use chrono::NaiveDate;

pub struct CcbParser;

impl CcbParser {
    pub fn new() -> Self {
        Self
    }
}

impl BillParser for CcbParser {
    fn source_name(&self) -> &'static str {
        "ccb"
    }

    fn probe(&self, file_path: &str) -> Result<bool, AppError> {
        let mut workbook = match open_workbook_auto(file_path) {
            Ok(wb) => wb,
            Err(_) => return Ok(false),
        };

        if let Some(Ok(range)) = workbook.worksheet_range_at(0) {
            for row in range.rows().take(5) {
                let row_str: Vec<String> = row.iter().map(|c| c.to_string()).collect();
                let joined = row_str.join(",");
                if joined.contains("中国建设银行") || joined.contains("交易金额") {
                    return Ok(true);
                }
            }
        }
        Ok(false)
    }

    fn parse(&self, file_path: &str) -> Result<Vec<RawTransaction>, AppError> {
        let mut workbook =
            open_workbook_auto(file_path).map_err(|e| AppError::IoError(e.to_string()))?;
        let range = workbook
            .worksheet_range_at(0)
            .ok_or_else(|| AppError::ParseError("No worksheet found".to_string()))?
            .map_err(|e| AppError::ParseError(e.to_string()))?;

        let mut transactions = Vec::new();
        let mut data_started = false;

        for row in range.rows() {
            let row_strs: Vec<String> = row
                .iter()
                .map(|c| c.to_string().trim().to_string())
                .collect();
            if row_strs.is_empty() {
                continue;
            }

            if !data_started {
                if row_strs.join(",").contains("交易日期")
                    && row_strs.join(",").contains("交易金额")
                {
                    data_started = true;
                }
                continue;
            }

            if row_strs.len() < 8 {
                continue;
            }

            // CCB Columns: 0:序号, 1:摘要, 2:币别, 3:钞汇, 4:交易日期, 5:交易金额, 6:账户余额, 7:交易地点/附言, 8:对方账号与户名
            let note = row_strs.get(1).cloned();
            let date_str = row_strs.get(4).unwrap_or(&"".to_string()).clone();

            if date_str.len() != 8 || !date_str.starts_with("20") {
                continue;
            }

            let dt = NaiveDate::parse_from_str(&date_str, "%Y%m%d")
                .map_err(|e| AppError::ParseError(format!("Invalid date format: {}", e)))?
                .and_hms_opt(0, 0, 0)
                .unwrap();
            let transaction_date = dt.and_utc().timestamp();

            let amount_str = row_strs.get(5).unwrap_or(&"0".to_string()).replace(",", "");
            let amount = amount_str.parse::<f64>().unwrap_or(0.0);

            let merchant = row_strs.get(8).cloned();
            let extended_note = row_strs.get(7).cloned();
            let mut final_note = String::new();
            if let Some(n) = note {
                final_note.push_str(&n);
            }
            if let Some(e) = extended_note {
                if !e.is_empty() && e != "***" {
                    final_note.push(' ');
                    final_note.push_str(&e);
                }
            }

            let transaction_type = if amount >= 0.0 {
                TransactionType::Income
            } else {
                TransactionType::Expense
            };

            transactions.push(RawTransaction {
                transaction_date,
                transaction_type,
                amount: amount.abs(),
                merchant,
                notes: Some(final_note),
                is_excluded: false,
                external_id: None, // CCB may not export clear external ID in this format
                category_hint: None,
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
    fn parses_ccb_fixture_record_count() {
        let parser = CcbParser::new();
        let path = fixture_path("建设银行.xls");
        let path = path.to_str().expect("fixture path should be valid UTF-8");

        assert!(parser.probe(path).expect("fixture should be probed"));

        let transactions = parser.parse(path).expect("fixture should parse");
        assert_eq!(transactions.len(), 152);

        let first = &transactions[0];
        assert_eq!(first.transaction_date, 1_780_272_000);
        assert_eq!(first.amount, 2000.0);
        assert_eq!(first.transaction_type, TransactionType::Income);
        assert_eq!(
            first.merchant.as_deref(),
            Some("11001008500053000881/北京住房公积金管理中心")
        );
        assert_eq!(first.notes.as_deref(), Some("代理付款"));
        assert!(!first.is_excluded);
        assert!(first.category_hint.is_none());
    }
}
