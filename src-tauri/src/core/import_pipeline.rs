use serde::{Deserialize, Serialize};
use ulid::Ulid;

use crate::core::dedup_engine::DedupEngine;
use crate::core::parsers::{BillParser, RawTransaction};
use crate::core::periodic::match_pending_confirmation;
use crate::core::rule_engine::RuleEngine;
use crate::error::AppError;
use crate::models::{Account, Category, Transaction, TransactionType};
use crate::ports::storage::LedgerStore;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum DuplicateStatus {
    New,
    Fuzzy,
    Absolute,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ImportPreviewItem {
    pub preview_id: String,
    pub transaction_date: i64,
    pub transaction_type: TransactionType,
    pub amount: f64,
    pub merchant: Option<String>,
    pub notes: Option<String>,
    pub category_id: Option<String>,
    pub category_hint: Option<String>,
    pub account_id: String,
    pub tags: Vec<String>,
    pub is_excluded: bool,
    pub source_platform: String,
    pub external_id: Option<String>,
    pub duplicate_status: DuplicateStatus,
    pub duplicate_transaction_id: Option<String>,
    pub selected: bool,
}

pub fn parse_import_preview<S: LedgerStore>(
    store: &S,
    parsers: Vec<Box<dyn BillParser>>,
    file_path: &str,
    ledger_id: &str,
    default_account_id: &str,
) -> Result<Vec<ImportPreviewItem>, AppError> {
    let parser = find_parser(&parsers, file_path)?;
    let source_name = parser.source_name();
    let rules = store.list_rules()?;
    let rule_engine = RuleEngine::new(rules);
    let existing_txs = store.list_transactions(ledger_id)?;
    let categories = store.list_categories()?;
    let accounts = store.list_accounts()?;
    let mut preview = Vec::new();

    for mut raw in parser.parse(file_path)? {
        rule_engine.apply_to_raw(&mut raw);
        if raw.should_skip {
            continue;
        }

        let absolute_duplicate_id = find_absolute_duplicate_id(&raw, &existing_txs, source_name);
        let duplicate_transaction_id = absolute_duplicate_id
            .clone()
            .or_else(|| DedupEngine::is_fuzzy_duplicate(&raw, &existing_txs, source_name));
        let duplicate_status = if absolute_duplicate_id.is_some() {
            DuplicateStatus::Absolute
        } else if duplicate_transaction_id.is_some() {
            DuplicateStatus::Fuzzy
        } else {
            DuplicateStatus::New
        };

        let (tags, account_hint) = split_internal_tags(raw.tags);
        let category_id = resolve_category(raw.category_hint.as_deref(), &categories);
        let account_id = resolve_account(account_hint.as_deref(), &accounts)
            .unwrap_or_else(|| default_account_id.to_string());
        let selected = duplicate_status == DuplicateStatus::New;

        preview.push(ImportPreviewItem {
            preview_id: Ulid::new().to_string(),
            transaction_date: raw.transaction_date,
            transaction_type: raw.transaction_type,
            amount: raw.amount,
            merchant: raw.merchant,
            notes: raw.notes,
            category_id,
            category_hint: raw.category_hint,
            account_id,
            tags,
            is_excluded: raw.is_excluded,
            source_platform: source_name.to_string(),
            external_id: raw.external_id,
            duplicate_status,
            duplicate_transaction_id,
            selected,
        });
    }

    Ok(preview)
}

pub fn confirm_import_preview<S: LedgerStore>(
    store: &S,
    ledger_id: &str,
    default_account_id: &str,
    items: &[ImportPreviewItem],
) -> Result<usize, AppError> {
    let existing_txs = store.list_transactions(ledger_id)?;
    let now = chrono::Utc::now().timestamp();
    let mut to_insert = Vec::new();

    for item in items {
        if !item.selected || item.duplicate_status == DuplicateStatus::Absolute {
            continue;
        }

        let raw = RawTransaction {
            transaction_date: item.transaction_date,
            transaction_type: item.transaction_type.clone(),
            amount: item.amount,
            merchant: item.merchant.clone(),
            notes: item.notes.clone(),
            is_excluded: item.is_excluded,
            external_id: item.external_id.clone(),
            category_hint: item.category_hint.clone(),
            tags: item.tags.clone(),
            should_skip: false,
        };
        if DedupEngine::is_absolute_duplicate(&raw, &existing_txs, &item.source_platform) {
            continue;
        }

        to_insert.push(Transaction {
            id: Ulid::new().to_string(),
            ledger_id: ledger_id.to_string(),
            account_id: if item.account_id.is_empty() {
                default_account_id.to_string()
            } else {
                item.account_id.clone()
            },
            category_id: item.category_id.clone(),
            transaction_date: item.transaction_date,
            amount: item.amount,
            transaction_type: item.transaction_type.clone(),
            merchant: item.merchant.clone(),
            notes: item.notes.clone(),
            tags: item.tags.clone(),
            is_excluded: item.is_excluded,
            external_source: Some(item.source_platform.clone()),
            external_id: item.external_id.clone(),
            created_at: now,
            updated_at: now,
            deleted_at: None,
        });
    }

    let count = to_insert.len();
    store.insert_transactions(&to_insert)?;
    for tx in &to_insert {
        match_pending_confirmation(store, tx)?;
    }
    Ok(count)
}

pub fn reapply_rules_to_transactions<S: LedgerStore>(
    store: &S,
    ledger_id: &str,
) -> Result<usize, AppError> {
    let rules = store.list_rules()?;
    let rule_engine = RuleEngine::new(rules);
    let categories = store.list_categories()?;
    let accounts = store.list_accounts()?;
    let mut updated_count = 0;

    for mut tx in store.list_transactions(ledger_id)? {
        let mut raw = RawTransaction {
            transaction_date: tx.transaction_date,
            transaction_type: tx.transaction_type.clone(),
            amount: tx.amount,
            merchant: tx.merchant.clone(),
            notes: tx.notes.clone(),
            is_excluded: tx.is_excluded,
            external_id: tx.external_id.clone(),
            category_hint: None,
            tags: tx.tags.clone(),
            should_skip: false,
        };

        if !rule_engine.apply_to_raw(&mut raw) {
            continue;
        }

        let (tags, account_hint) = split_internal_tags(raw.tags);
        tx.category_id =
            resolve_category(raw.category_hint.as_deref(), &categories).or(tx.category_id);
        tx.account_id =
            resolve_account(account_hint.as_deref(), &accounts).unwrap_or(tx.account_id);
        tx.notes = raw.notes;
        tx.tags = dedupe_tags(tags);
        tx.is_excluded = raw.is_excluded || raw.should_skip;
        tx.updated_at = chrono::Utc::now().timestamp();
        store.update_transaction(&tx)?;
        updated_count += 1;
    }

    Ok(updated_count)
}

fn find_parser<'a>(
    parsers: &'a [Box<dyn BillParser>],
    file_path: &str,
) -> Result<&'a dyn BillParser, AppError> {
    for parser in parsers {
        if parser.probe(file_path).unwrap_or(false) {
            return Ok(parser.as_ref());
        }
    }

    Err(AppError::ParseError("未匹配到支持的账单格式".into()))
}

fn resolve_category(hint: Option<&str>, categories: &[Category]) -> Option<String> {
    let hint = hint?;
    categories
        .iter()
        .find(|category| category.id == hint || category.name == hint)
        .map(|category| category.id.clone())
}

fn find_absolute_duplicate_id(
    raw: &RawTransaction,
    existing_txs: &[Transaction],
    source_name: &str,
) -> Option<String> {
    let external_id = raw.external_id.as_deref()?;
    existing_txs
        .iter()
        .find(|existing| {
            existing.external_source.as_deref() == Some(source_name)
                && existing.external_id.as_deref() == Some(external_id)
        })
        .map(|existing| existing.id.clone())
}

fn resolve_account(hint: Option<&str>, accounts: &[Account]) -> Option<String> {
    let hint = hint?;
    accounts
        .iter()
        .find(|account| account.id == hint || account.name == hint)
        .map(|account| account.id.clone())
}

fn split_internal_tags(tags: Vec<String>) -> (Vec<String>, Option<String>) {
    let mut visible_tags = Vec::new();
    let mut account_hint = None;

    for tag in tags {
        if let Some(hint) = tag.strip_prefix("__account_hint:") {
            account_hint = Some(hint.to_string());
        } else {
            visible_tags.push(tag);
        }
    }

    (visible_tags, account_hint)
}

fn dedupe_tags(tags: Vec<String>) -> Vec<String> {
    let mut result = Vec::new();
    for tag in tags {
        if !result.contains(&tag) {
            result.push(tag);
        }
    }
    result
}
