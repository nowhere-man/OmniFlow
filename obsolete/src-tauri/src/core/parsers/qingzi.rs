use super::{BillParser, RawTransaction};
use crate::error::AppError;
use crate::models::TransactionType;
use serde_json::Value;
use std::collections::HashMap;
use std::fs;

pub struct QingziParser;

impl QingziParser {
    pub fn new() -> Self {
        Self
    }
}

fn json_array_field(root: &Value, key: &str) -> Result<Vec<Value>, AppError> {
    match root.get(key) {
        Some(Value::Array(items)) => Ok(items.clone()),
        Some(Value::String(raw)) => serde_json::from_str::<Vec<Value>>(raw)
            .map_err(|e| AppError::ParseError(format!("无法解析青子记账字段 {}: {}", key, e))),
        _ => Ok(Vec::new()),
    }
}

fn object_id(value: &Value) -> Option<&str> {
    value
        .get("id")
        .or_else(|| value.get("identifier"))
        .and_then(|id| id.as_str())
}

impl BillParser for QingziParser {
    fn source_name(&self) -> &'static str {
        "qingzi"
    }

    fn probe(&self, file_path: &str) -> Result<bool, AppError> {
        let content = fs::read_to_string(file_path).unwrap_or_default();
        // Check for Qingzi JSON structure markers
        Ok(content.contains("entryJsonString")
            || content.contains("bookJsonString")
            || content.contains("青子记账"))
    }

    fn parse(&self, file_path: &str) -> Result<Vec<RawTransaction>, AppError> {
        let content =
            fs::read_to_string(file_path).map_err(|e| AppError::IoError(e.to_string()))?;

        let root: Value = serde_json::from_str(&content)
            .map_err(|e| AppError::ParseError(format!("无法解析青子记账 JSON: {}", e)))?;

        // Parse category map: id -> (name, type)
        // type: 0 = income, 1 = expense
        let mut category_map: HashMap<String, (String, i64)> = HashMap::new();
        for cat in json_array_field(&root, "categoryJsonString")? {
            let id = object_id(&cat).unwrap_or("").to_string();
            let name = cat
                .get("name")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();
            let cat_type = cat.get("type").and_then(|v| v.as_i64()).unwrap_or(1);
            if !id.is_empty() {
                category_map.insert(id, (name, cat_type));
            }
        }

        // Parse account map: id -> name
        let mut account_map: HashMap<String, String> = HashMap::new();
        for acc in json_array_field(&root, "accountJsonString")? {
            let id = object_id(&acc).unwrap_or("").to_string();
            let name = acc
                .get("name")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();
            if !id.is_empty() {
                account_map.insert(id, name);
            }
        }

        // Parse mark (tag) map: id -> name
        let mut mark_map: HashMap<String, String> = HashMap::new();
        for mark in json_array_field(&root, "markJsonString")? {
            let id = object_id(&mark).unwrap_or("").to_string();
            let name = mark
                .get("name")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();
            if !id.is_empty() {
                mark_map.insert(id, name);
            }
        }

        // Parse entries
        let mut results = Vec::new();
        for entry in json_array_field(&root, "entryJsonString")? {
            let amount = entry
                .get("value")
                .and_then(|v| v.as_f64())
                .unwrap_or(0.0)
                .abs();

            // createDate is Unix timestamp (seconds or milliseconds)
            let create_date = entry
                .get("createDate")
                .and_then(|v| v.as_i64())
                .unwrap_or(0);
            // If timestamp is in milliseconds (> year 2100 in seconds), convert
            let transaction_date = if create_date > 4_000_000_000 {
                create_date / 1000
            } else {
                create_date
            };

            let notes = entry
                .get("content")
                .and_then(|v| v.as_str())
                .map(|s| s.to_string());

            // Determine type from category
            let category_id = entry
                .get("categoryID")
                .and_then(|v| v.as_str())
                .unwrap_or("");

            let (category_hint, transaction_type) =
                if let Some((name, cat_type)) = category_map.get(category_id) {
                    let tx_type = if *cat_type == 0 {
                        TransactionType::Income
                    } else {
                        TransactionType::Expense
                    };
                    (Some(name.clone()), tx_type)
                } else {
                    (None, TransactionType::Expense)
                };

            // Resolve account name (stored as hint via tag for now)
            let _account_name = entry
                .get("accountID")
                .and_then(|v| v.as_str())
                .and_then(|id| account_map.get(id))
                .cloned();

            // Resolve tags from markIDs (comma-separated)
            let mut tags = Vec::new();
            if let Some(mark_ids) = entry.get("markIDs").and_then(|v| v.as_str()) {
                for mark_id in mark_ids.split(',') {
                    let mark_id = mark_id.trim();
                    if !mark_id.is_empty() {
                        if let Some(name) = mark_map.get(mark_id) {
                            tags.push(name.clone());
                        }
                    }
                }
            }

            // Check excludeFromBudget
            let is_excluded = entry
                .get("excludeFromBudget")
                .and_then(|v| v.as_bool())
                .unwrap_or(false);

            let external_id = object_id(&entry).map(|s| s.to_string());

            results.push(RawTransaction {
                transaction_date,
                transaction_type,
                amount,
                merchant: None,
                notes,
                is_excluded,
                external_id,
                category_hint,
                tags,
                should_skip: false,
            });
        }

        Ok(results)
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
    fn parses_qingzi_fixture_records_and_core_fields() {
        let parser = QingziParser::new();
        let path = fixture_path("青子记账.json");
        let path = path.to_str().expect("fixture path should be valid UTF-8");

        assert!(parser.probe(path).expect("fixture should be probed"));

        let transactions = parser.parse(path).expect("fixture should parse");
        assert_eq!(transactions.len(), 4436);

        let first = &transactions[0];
        assert_eq!(first.transaction_date, 1_755_264_060);
        assert_eq!(first.amount, 4.0);
        assert_eq!(first.transaction_type, TransactionType::Expense);
        assert_eq!(first.notes.as_deref(), Some("停车"));
        assert_eq!(first.category_hint.as_deref(), Some("车"));
        assert!(!first.is_excluded);
    }
}
