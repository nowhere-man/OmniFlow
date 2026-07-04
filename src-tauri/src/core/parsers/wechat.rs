use super::{BillParser, RawTransaction};
use crate::error::AppError;
use crate::models::TransactionType;
use calamine::{open_workbook_auto, Reader};
use chrono::{Duration, NaiveDate, NaiveDateTime};

pub struct WechatParser;

impl WechatParser {
    pub fn new() -> Self {
        Self
    }
}

fn parse_wechat_datetime(value: &str) -> Result<i64, AppError> {
    if let Ok(dt) = NaiveDateTime::parse_from_str(value, "%Y-%m-%d %H:%M:%S") {
        return Ok(dt.and_utc().timestamp());
    }

    if let Ok(serial) = value.parse::<f64>() {
        let epoch = NaiveDate::from_ymd_opt(1899, 12, 30)
            .and_then(|date| date.and_hms_opt(0, 0, 0))
            .ok_or_else(|| AppError::ParseError("Invalid Excel epoch".to_string()))?;
        let seconds = (serial * 86_400.0).round() as i64;
        return Ok((epoch + Duration::seconds(seconds)).and_utc().timestamp());
    }

    Err(AppError::ParseError(format!(
        "Invalid date format: {}",
        value
    )))
}

impl BillParser for WechatParser {
    fn source_name(&self) -> &'static str {
        "wechat"
    }

    fn probe(&self, file_path: &str) -> Result<bool, AppError> {
        let mut workbook = match open_workbook_auto(file_path) {
            Ok(wb) => wb,
            Err(_) => return Ok(false),
        };

        if let Some(Ok(range)) = workbook.worksheet_range_at(0) {
            let mut has_wechat_marker = false;
            let mut has_transaction_header = false;
            for row in range.rows().take(20) {
                let row_str: Vec<String> = row.iter().map(|c| c.to_string()).collect();
                let joined = row_str.join(",");
                has_wechat_marker |= joined.contains("微信支付");
                has_transaction_header |=
                    joined.contains("交易时间") && joined.contains("交易单号");
            }
            return Ok(has_wechat_marker && has_transaction_header);
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
                if row_strs.join(",").contains("交易时间")
                    && row_strs.join(",").contains("交易类型")
                {
                    data_started = true;
                }
                continue;
            }

            if row_strs.len() < 8 {
                continue;
            }

            // Expected columns
            // 0: 交易时间
            // 1: 交易类型 (商户消费, 退款, etc)
            // 2: 交易对方
            // 3: 商品
            // 4: 收/支
            // 5: 金额(元)
            // 6: 支付方式
            // 7: 当前状态
            // 8: 交易单号
            // 9: 商户单号
            // 10: 备注

            let date_str = &row_strs[0];
            if date_str.is_empty() {
                continue;
            }

            let transaction_date = match parse_wechat_datetime(date_str) {
                Ok(ts) => ts,
                Err(_) => continue,
            };

            let merchant = Some(row_strs[2].clone());
            let notes = Some(row_strs[3].clone());
            let direction_str = &row_strs[4];
            let amount_str = row_strs[5].replace("¥", "").replace(",", "");
            let amount = amount_str.parse::<f64>().unwrap_or(0.0);

            let mut is_excluded = false;
            let transaction_type = match direction_str.as_str() {
                "收入" => TransactionType::Income,
                "支出" => TransactionType::Expense,
                "/" | "中性交易" => {
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

            let external_id = if row_strs.len() > 8 {
                Some(row_strs[8].clone())
            } else {
                None
            };

            transactions.push(RawTransaction {
                transaction_date,
                transaction_type,
                amount: amount.abs(),
                merchant,
                notes,
                is_excluded,
                external_id,
                category_hint: None, // WeChat doesn't provide category
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
    fn parses_wechat_fixture_record_count() {
        let parser = WechatParser::new();
        let path = fixture_path("微信.xlsx");
        let path = path.to_str().expect("fixture path should be valid UTF-8");

        assert!(parser.probe(path).expect("fixture should be probed"));

        let transactions = parser.parse(path).expect("fixture should parse");
        assert_eq!(transactions.len(), 86);

        let first = &transactions[0];
        assert_eq!(first.amount, 8.41);
        assert_eq!(first.transaction_type, TransactionType::Expense);
        assert_eq!(first.merchant.as_deref(), Some("24H智能售货"));
        assert_eq!(first.notes.as_deref(), Some("先购后付"));
        assert_eq!(
            first.external_id.as_deref(),
            Some("4200003128202606308611070633")
        );
        assert!(!first.is_excluded);
    }
}
