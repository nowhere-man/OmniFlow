use crate::core::parsers::RawTransaction;
use crate::models::Rule;
use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuleCondition {
    pub match_type: String, // "merchant_keyword", "description_keyword", "notes_keyword", "regex", "amount_range"
    pub value: String,      // Value or JSON serialized params
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuleAction {
    pub action_type: String, // "set_category", "set_account", "add_tag", "set_notes", "exclude", "skip"
    pub value: String,
}

pub struct RuleEngine {
    rules: Vec<Rule>,
    regex_cache: HashMap<String, Regex>,
}

impl RuleEngine {
    pub fn new(mut rules: Vec<Rule>) -> Self {
        rules.sort_by_key(|r| -r.priority); // Highest priority first

        // Pre-compile all regex patterns
        let mut regex_cache = HashMap::new();
        for rule in &rules {
            let conditions: Vec<RuleCondition> =
                serde_json::from_str(&rule.match_condition).unwrap_or_default();
            for cond in &conditions {
                if cond.match_type == "regex" {
                    if let Ok(re) = Regex::new(&cond.value) {
                        regex_cache.insert(cond.value.clone(), re);
                    }
                }
            }
        }

        Self { rules, regex_cache }
    }

    /// Apply rules to a raw transaction. Returns true if any rule matched (short-circuits).
    pub fn apply_to_raw(&self, tx: &mut RawTransaction) -> bool {
        for rule in &self.rules {
            let conditions: Vec<RuleCondition> =
                serde_json::from_str(&rule.match_condition).unwrap_or_default();
            let actions: Vec<RuleAction> = serde_json::from_str(&rule.action).unwrap_or_default();

            if self.evaluate_conditions(&conditions, tx) {
                self.execute_actions(&actions, tx);
                return true; // Short circuit on first matched rule
            }
        }
        false
    }

    fn evaluate_conditions(&self, conditions: &[RuleCondition], tx: &RawTransaction) -> bool {
        if conditions.is_empty() {
            return false;
        }

        for cond in conditions {
            let matches = match cond.match_type.as_str() {
                "merchant_keyword" => tx
                    .merchant
                    .as_ref()
                    .is_some_and(|m| m.contains(&cond.value)),
                "description_keyword" => {
                    // Match against notes (description/product name)
                    tx.notes.as_ref().is_some_and(|n| n.contains(&cond.value))
                }
                "notes_keyword" => tx.notes.as_ref().is_some_and(|n| n.contains(&cond.value)),
                "regex" => {
                    let target = format!(
                        "{} {}",
                        tx.merchant.as_deref().unwrap_or(""),
                        tx.notes.as_deref().unwrap_or("")
                    );
                    if let Some(re) = self.regex_cache.get(&cond.value) {
                        re.is_match(&target)
                    } else if let Ok(re) = Regex::new(&cond.value) {
                        re.is_match(&target)
                    } else {
                        false
                    }
                }
                "amount_range" => {
                    // value format: "min,max" e.g. "100,500"
                    let parts: Vec<&str> = cond.value.split(',').collect();
                    if parts.len() == 2 {
                        let min: f64 = parts[0].parse().unwrap_or(0.0);
                        let max: f64 = parts[1].parse().unwrap_or(f64::MAX);
                        tx.amount >= min && tx.amount <= max
                    } else {
                        false
                    }
                }
                _ => false,
            };

            if !matches {
                return false; // All conditions must match (AND logic)
            }
        }
        true
    }

    fn execute_actions(&self, actions: &[RuleAction], tx: &mut RawTransaction) {
        for action in actions {
            match action.action_type.as_str() {
                "set_category" => {
                    tx.category_hint = Some(action.value.clone());
                }
                "set_account" => {
                    // Store account hint for later mapping
                    // We extend RawTransaction to support this - for now, store in notes as prefix
                    // Better: we add an account_hint field. We use tags to pass this.
                    tx.tags.push(format!("__account_hint:{}", action.value));
                }
                "add_tag" => {
                    tx.tags.push(action.value.clone());
                }
                "set_notes" => {
                    tx.notes = Some(action.value.clone());
                }
                "exclude" => {
                    tx.is_excluded = action.value == "true";
                }
                "skip" => {
                    tx.should_skip = action.value == "true";
                }
                _ => {}
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::TransactionType;

    fn raw_transaction(amount: f64) -> RawTransaction {
        RawTransaction {
            transaction_date: 1,
            transaction_type: TransactionType::Expense,
            amount,
            merchant: Some("咖啡店".to_string()),
            notes: Some("拿铁".to_string()),
            is_excluded: false,
            external_id: None,
            category_hint: None,
            tags: vec![],
            should_skip: false,
        }
    }

    #[test]
    fn applies_amount_range_and_field_mapping_actions() {
        let rule = Rule {
            id: "rule".to_string(),
            name: "大额咖啡".to_string(),
            priority: 1,
            match_condition: serde_json::json!([
                { "match_type": "amount_range", "value": "10,20" }
            ])
            .to_string(),
            action: serde_json::json!([
                { "action_type": "set_category", "value": "food" },
                { "action_type": "set_account", "value": "cash" },
                { "action_type": "add_tag", "value": "coffee" },
                { "action_type": "set_notes", "value": "规则备注" },
                { "action_type": "exclude", "value": "true" },
                { "action_type": "skip", "value": "true" }
            ])
            .to_string(),
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let engine = RuleEngine::new(vec![rule]);
        let mut tx = raw_transaction(15.0);

        assert!(engine.apply_to_raw(&mut tx));
        assert_eq!(tx.category_hint.as_deref(), Some("food"));
        assert!(tx.tags.iter().any(|tag| tag == "__account_hint:cash"));
        assert!(tx.tags.iter().any(|tag| tag == "coffee"));
        assert_eq!(tx.notes.as_deref(), Some("规则备注"));
        assert!(tx.is_excluded);
        assert!(tx.should_skip);
    }
}
