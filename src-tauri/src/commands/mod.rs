pub mod category;
pub mod data;
pub mod stats;
pub mod sync;
use crate::adapters::sqlite_store::SqliteStore;
use crate::core::import_pipeline::{
    confirm_import_preview, parse_import_preview, ImportPreviewItem,
};
use crate::core::parsers::{
    alipay::AlipayParser, ccb::CcbParser, jd::JdParser, meituan::MeituanParser,
    qingzi::QingziParser, wechat::WechatParser, BillParser,
};
use crate::error::AppError;
use crate::models::{Account, Ledger, PendingConfirmation, PeriodicBill, Rule, Transaction};
use crate::ports::storage::{LedgerStore, SearchResult, TransactionFilter};
use tauri::State;

fn bill_parsers() -> Vec<Box<dyn BillParser>> {
    vec![
        Box::new(AlipayParser::new()),
        Box::new(WechatParser::new()),
        Box::new(JdParser::new()),
        Box::new(MeituanParser::new()),
        Box::new(CcbParser::new()),
        Box::new(QingziParser::new()),
    ]
}

#[tauri::command]
pub fn list_ledgers(store: State<'_, SqliteStore>) -> Result<Vec<Ledger>, AppError> {
    store.list_ledgers()
}

#[tauri::command]
pub fn create_ledger(store: State<'_, SqliteStore>, ledger: Ledger) -> Result<(), AppError> {
    store.create_ledger(&ledger)
}

#[tauri::command]
pub fn update_ledger(store: State<'_, SqliteStore>, ledger: Ledger) -> Result<(), AppError> {
    store.update_ledger(&ledger)
}

#[tauri::command]
pub fn delete_ledger(store: State<'_, SqliteStore>, id: String) -> Result<(), AppError> {
    store.delete_ledger(&id)
}

#[tauri::command]
pub fn list_accounts(store: State<'_, SqliteStore>) -> Result<Vec<Account>, AppError> {
    store.list_accounts()
}

#[tauri::command]
pub fn create_account(store: State<'_, SqliteStore>, account: Account) -> Result<(), AppError> {
    store.create_account(&account)
}

#[tauri::command]
pub fn update_account(store: State<'_, SqliteStore>, account: Account) -> Result<(), AppError> {
    store.update_account(&account)
}

#[tauri::command]
pub fn delete_account(store: State<'_, SqliteStore>, id: String) -> Result<(), AppError> {
    store.delete_account(&id)
}

#[tauri::command]
pub fn list_transactions(
    store: State<'_, SqliteStore>,
    ledger_id: String,
) -> Result<Vec<Transaction>, AppError> {
    store.list_transactions(&ledger_id)
}

#[tauri::command]
pub fn create_transaction(
    store: State<'_, SqliteStore>,
    transaction: Transaction,
) -> Result<(), AppError> {
    store.insert_transaction(&transaction)
}

#[tauri::command]
pub fn update_transaction(
    store: State<'_, SqliteStore>,
    transaction: Transaction,
) -> Result<(), AppError> {
    store.update_transaction(&transaction)
}

#[tauri::command]
pub fn delete_transaction(store: State<'_, SqliteStore>, id: String) -> Result<(), AppError> {
    store.delete_transaction(&id)
}

#[tauri::command]
pub fn search_transactions(
    store: State<'_, SqliteStore>,
    ledger_id: String,
    filter: TransactionFilter,
) -> Result<SearchResult, AppError> {
    store.search_transactions(&ledger_id, &filter)
}

#[tauri::command]
pub fn list_rules(store: State<'_, SqliteStore>) -> Result<Vec<Rule>, AppError> {
    store.list_rules()
}

#[tauri::command]
pub fn create_rule(store: State<'_, SqliteStore>, rule: Rule) -> Result<(), AppError> {
    store.create_rule(&rule)
}

#[tauri::command]
pub fn update_rule(store: State<'_, SqliteStore>, rule: Rule) -> Result<(), AppError> {
    store.update_rule(&rule)
}

#[tauri::command]
pub fn delete_rule(store: State<'_, SqliteStore>, id: String) -> Result<(), AppError> {
    store.delete_rule(&id)
}

#[tauri::command]
pub fn list_periodic_bills(store: State<'_, SqliteStore>) -> Result<Vec<PeriodicBill>, AppError> {
    store.list_periodic_bills()
}

#[tauri::command]
pub fn create_periodic_bill(
    store: State<'_, SqliteStore>,
    bill: PeriodicBill,
) -> Result<(), AppError> {
    store.create_periodic_bill(&bill)
}

#[tauri::command]
pub fn update_periodic_bill(
    store: State<'_, SqliteStore>,
    bill: PeriodicBill,
) -> Result<(), AppError> {
    store.update_periodic_bill(&bill)
}

#[tauri::command]
pub fn delete_periodic_bill(store: State<'_, SqliteStore>, id: String) -> Result<(), AppError> {
    store.delete_periodic_bill(&id)
}

#[tauri::command]
pub fn list_pending_confirmations(
    store: State<'_, SqliteStore>,
) -> Result<Vec<PendingConfirmation>, AppError> {
    store.list_pending_confirmations()
}

#[tauri::command]
pub fn create_pending_confirmation(
    store: State<'_, SqliteStore>,
    pending: PendingConfirmation,
) -> Result<(), AppError> {
    store.create_pending_confirmation(&pending)
}

#[tauri::command]
pub fn update_pending_confirmation(
    store: State<'_, SqliteStore>,
    pending: PendingConfirmation,
) -> Result<(), AppError> {
    store.update_pending_confirmation(&pending)
}

#[tauri::command]
pub fn delete_pending_confirmation(
    store: State<'_, SqliteStore>,
    id: String,
) -> Result<(), AppError> {
    store.delete_pending_confirmation(&id)
}

#[tauri::command]
pub fn generate_pending_confirmations(
    store: State<'_, SqliteStore>,
    now_ts: i64,
) -> Result<Vec<PendingConfirmation>, AppError> {
    crate::core::periodic::generate_due_confirmations(&*store, now_ts)
}

#[tauri::command]
pub async fn import_bill(
    store: State<'_, SqliteStore>,
    file_path: String,
    ledger_id: String,
    account_id: String,
) -> Result<usize, AppError> {
    let preview =
        parse_import_preview(&*store, bill_parsers(), &file_path, &ledger_id, &account_id)?;
    Ok(preview.len())
}

#[tauri::command]
pub async fn parse_and_preview(
    store: State<'_, SqliteStore>,
    file_path: String,
    ledger_id: String,
    account_id: String,
) -> Result<Vec<ImportPreviewItem>, AppError> {
    parse_import_preview(&*store, bill_parsers(), &file_path, &ledger_id, &account_id)
}

#[tauri::command]
pub fn confirm_import(
    store: State<'_, SqliteStore>,
    ledger_id: String,
    account_id: String,
    items: Vec<ImportPreviewItem>,
) -> Result<usize, AppError> {
    confirm_import_preview(&*store, &ledger_id, &account_id, &items)
}

#[tauri::command]
pub fn reapply_rules(store: State<'_, SqliteStore>, ledger_id: String) -> Result<usize, AppError> {
    crate::core::import_pipeline::reapply_rules_to_transactions(&*store, &ledger_id)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::core::import_pipeline::{
        confirm_import_preview, parse_import_preview, reapply_rules_to_transactions,
        DuplicateStatus, ImportPreviewItem,
    };
    use crate::core::parsers::alipay::AlipayParser;
    use crate::core::parsers::qingzi::QingziParser;
    use crate::core::periodic::generate_due_confirmations;
    use crate::models::{AccountType, Category, Rule, TransactionType};
    use std::path::PathBuf;

    fn fixture_path(file_name: &str) -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("..")
            .join("examples")
            .join(file_name)
    }

    #[test]
    fn bill_parsers_include_qingzi_import() {
        let parsers = bill_parsers();
        let parser_names: Vec<&str> = parsers.iter().map(|parser| parser.source_name()).collect();

        assert!(parser_names.contains(&"qingzi"));

        let fixture = fixture_path("青子记账.json");
        let fixture = fixture
            .to_str()
            .expect("fixture path should be valid UTF-8");
        let matched = parsers
            .iter()
            .find(|parser| parser.probe(fixture).unwrap_or(false))
            .map(|parser| parser.source_name());

        assert_eq!(matched, Some("qingzi"));
    }

    #[test]
    fn command_surface_exposes_crud_search_rules_and_periodic_bills() {
        let _ = update_transaction;
        let _ = delete_transaction;
        let _ = create_ledger;
        let _ = update_ledger;
        let _ = delete_ledger;
        let _ = create_account;
        let _ = update_account;
        let _ = delete_account;
        let _ = search_transactions;
        let _ = list_rules;
        let _ = create_rule;
        let _ = update_rule;
        let _ = delete_rule;
        let _ = list_periodic_bills;
        let _ = create_periodic_bill;
        let _ = update_periodic_bill;
        let _ = delete_periodic_bill;
        let _ = list_pending_confirmations;
        let _ = create_pending_confirmation;
        let _ = update_pending_confirmation;
        let _ = delete_pending_confirmation;
        let _ = generate_pending_confirmations;
        let _ = parse_and_preview;
        let _ = confirm_import;
        let _ = reapply_rules;
    }

    #[test]
    fn import_pipeline_parses_rules_dedups_previews_and_confirms() {
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
        let default_account = Account {
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
        let cash_account = Account {
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
        let car_category = Category {
            id: "car".to_string(),
            name: "车".to_string(),
            category_type: TransactionType::Expense,
            parent_id: None,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let rule = Rule {
            id: "parking-rule".to_string(),
            name: "大额支出规则".to_string(),
            priority: 10,
            match_condition: serde_json::json!([
                { "match_type": "amount_range", "value": "200,300" }
            ])
            .to_string(),
            action: serde_json::json!([
                { "action_type": "set_category", "value": "car" },
                { "action_type": "set_account", "value": "cash" },
                { "action_type": "add_tag", "value": "大额测试" },
                { "action_type": "set_notes", "value": "规则备注" }
            ])
            .to_string(),
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };

        store
            .create_ledger(&ledger)
            .expect("ledger should be saved");
        store
            .create_account(&default_account)
            .expect("default account should be saved");
        store
            .create_account(&cash_account)
            .expect("cash account should be saved");
        store
            .create_category(&car_category)
            .expect("category should be saved");
        store.create_rule(&rule).expect("rule should be saved");

        let fixture = fixture_path("支付宝.csv");
        let fixture = fixture
            .to_str()
            .expect("fixture path should be valid UTF-8");
        let first_raw = AlipayParser::new()
            .parse(fixture)
            .expect("fixture should parse")
            .into_iter()
            .next()
            .expect("fixture should have rows");
        let existing = Transaction {
            id: "existing-alipay".to_string(),
            ledger_id: ledger.id.clone(),
            account_id: default_account.id.clone(),
            category_id: None,
            transaction_date: first_raw.transaction_date,
            amount: first_raw.amount,
            transaction_type: first_raw.transaction_type,
            merchant: first_raw.merchant,
            notes: first_raw.notes,
            tags: vec![],
            is_excluded: false,
            external_source: Some("alipay".to_string()),
            external_id: first_raw.external_id.clone(),
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        store
            .insert_transaction(&existing)
            .expect("existing transaction should be saved");

        let preview = parse_import_preview(
            &store,
            bill_parsers(),
            fixture,
            &ledger.id,
            &default_account.id,
        )
        .expect("preview should be built");

        let duplicate = preview
            .iter()
            .find(|item| item.external_id == first_raw.external_id)
            .expect("absolute duplicate should be present");
        assert_eq!(duplicate.duplicate_status, DuplicateStatus::Absolute);
        assert_eq!(
            duplicate.duplicate_transaction_id.as_deref(),
            Some("existing-alipay")
        );
        assert!(!duplicate.selected);

        let ruled = preview
            .iter()
            .find(|item| {
                item.duplicate_status == DuplicateStatus::New
                    && item.tags.iter().any(|tag| tag == "大额测试")
            })
            .expect("rule-matched preview item should be present");
        assert_eq!(ruled.category_id.as_deref(), Some("car"));
        assert_eq!(ruled.account_id, "cash");
        assert_eq!(ruled.notes.as_deref(), Some("规则备注"));

        let mut forced_duplicate = duplicate.clone();
        forced_duplicate.selected = true;
        let mut selected_ruled = ruled.clone();
        selected_ruled.selected = true;
        let inserted = confirm_import_preview(
            &store,
            &ledger.id,
            &default_account.id,
            &[forced_duplicate, selected_ruled],
        )
        .expect("confirm should succeed");

        assert_eq!(inserted, 1);
        assert_eq!(store.list_transactions(&ledger.id).unwrap().len(), 2);
    }

    #[test]
    fn import_pipeline_skip_rule_excludes_transactions_before_preview() {
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
        let skip_rule = Rule {
            id: "skip-all".to_string(),
            name: "跳过导入".to_string(),
            priority: 1,
            match_condition: serde_json::json!([
                { "match_type": "amount_range", "value": "0,100000" }
            ])
            .to_string(),
            action: serde_json::json!([
                { "action_type": "skip", "value": "true" }
            ])
            .to_string(),
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
            .create_rule(&skip_rule)
            .expect("skip rule should be saved");

        let fixture = fixture_path("支付宝.csv");
        let fixture = fixture
            .to_str()
            .expect("fixture path should be valid UTF-8");
        let preview =
            parse_import_preview(&store, bill_parsers(), fixture, &ledger.id, &account.id)
                .expect("preview should be built");

        assert!(preview.is_empty());
        assert_eq!(
            confirm_import_preview(&store, &ledger.id, &account.id, &preview).unwrap(),
            0
        );
        assert!(store.list_transactions(&ledger.id).unwrap().is_empty());
    }

    #[test]
    fn import_pipeline_returns_fuzzy_duplicates_for_review() {
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
        store
            .create_ledger(&ledger)
            .expect("ledger should be saved");
        store
            .create_account(&account)
            .expect("account should be saved");

        let fixture = fixture_path("青子记账.json");
        let fixture = fixture
            .to_str()
            .expect("fixture path should be valid UTF-8");
        let first_raw = QingziParser::new()
            .parse(fixture)
            .expect("fixture should parse")
            .into_iter()
            .next()
            .expect("fixture should have rows");
        let existing = Transaction {
            id: "existing-fuzzy".to_string(),
            ledger_id: ledger.id.clone(),
            account_id: account.id.clone(),
            category_id: None,
            transaction_date: first_raw.transaction_date,
            amount: first_raw.amount,
            transaction_type: first_raw.transaction_type,
            merchant: first_raw.merchant,
            notes: first_raw.notes.clone(),
            tags: vec![],
            is_excluded: false,
            external_source: Some("qingzi".to_string()),
            external_id: None,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        store
            .insert_transaction(&existing)
            .expect("existing transaction should be saved");

        let preview =
            parse_import_preview(&store, bill_parsers(), fixture, &ledger.id, &account.id)
                .expect("preview should be built");
        let duplicate = preview
            .iter()
            .find(|item| item.notes == first_raw.notes && item.amount == first_raw.amount)
            .expect("fuzzy duplicate should be present");

        assert_eq!(duplicate.duplicate_status, DuplicateStatus::Fuzzy);
        assert_eq!(
            duplicate.duplicate_transaction_id.as_deref(),
            Some("existing-fuzzy")
        );
        assert!(!duplicate.selected);
    }

    #[test]
    fn reapply_rules_updates_existing_transactions() {
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
        let cash_account = Account {
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
        let category = Category {
            id: "food".to_string(),
            name: "餐饮".to_string(),
            category_type: TransactionType::Expense,
            parent_id: None,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let rule = Rule {
            id: "coffee-rule".to_string(),
            name: "咖啡规则".to_string(),
            priority: 1,
            match_condition: serde_json::json!([
                { "match_type": "merchant_keyword", "value": "咖啡" }
            ])
            .to_string(),
            action: serde_json::json!([
                { "action_type": "set_category", "value": "food" },
                { "action_type": "set_account", "value": "cash" },
                { "action_type": "add_tag", "value": "coffee" },
                { "action_type": "set_notes", "value": "规则备注" },
                { "action_type": "exclude", "value": "true" }
            ])
            .to_string(),
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let transaction = Transaction {
            id: "tx".to_string(),
            ledger_id: ledger.id.clone(),
            account_id: account.id.clone(),
            category_id: None,
            transaction_date: 1,
            amount: 18.0,
            transaction_type: TransactionType::Expense,
            merchant: Some("咖啡店".to_string()),
            notes: None,
            tags: vec![],
            is_excluded: false,
            external_source: None,
            external_id: None,
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
            .create_account(&cash_account)
            .expect("cash account should be saved");
        store
            .create_category(&category)
            .expect("category should be saved");
        store.create_rule(&rule).expect("rule should be saved");
        store
            .insert_transaction(&transaction)
            .expect("transaction should be saved");

        assert_eq!(
            reapply_rules_to_transactions(&store, &ledger.id).unwrap(),
            1
        );
        let updated = store.get_transaction("tx").unwrap().unwrap();
        assert_eq!(updated.category_id.as_deref(), Some("food"));
        assert_eq!(updated.account_id, "cash");
        assert_eq!(updated.notes.as_deref(), Some("规则备注"));
        assert_eq!(updated.tags, vec!["coffee"]);
        assert!(updated.is_excluded);
    }

    #[test]
    fn due_periodic_bill_becomes_pending_and_import_can_confirm_it() {
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
        let category = Category {
            id: "rent".to_string(),
            name: "房租".to_string(),
            category_type: TransactionType::Expense,
            parent_id: None,
            created_at: 1,
            updated_at: 1,
            deleted_at: None,
        };
        let bill = PeriodicBill {
            id: "rent-bill".to_string(),
            name: "房租".to_string(),
            amount: 2500.0,
            bill_type: TransactionType::Expense,
            category_id: Some(category.id.clone()),
            account_id: account.id.clone(),
            cron_expression: "monthly".to_string(),
            next_date: 1_700_000_000,
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
            .create_category(&category)
            .expect("category should be saved");
        store
            .create_periodic_bill(&bill)
            .expect("periodic bill should be saved");

        let pending = generate_due_confirmations(&store, 1_700_000_100)
            .expect("due confirmations should generate");
        assert_eq!(pending.len(), 1);
        assert_eq!(pending[0].periodic_bill_id, bill.id);
        assert_eq!(pending[0].status, "pending");

        let item = ImportPreviewItem {
            preview_id: "preview".to_string(),
            transaction_date: 1_700_000_010,
            transaction_type: TransactionType::Expense,
            amount: 2500.0,
            merchant: Some("房东".to_string()),
            notes: Some("房租".to_string()),
            category_id: Some(category.id),
            category_hint: None,
            account_id: account.id.clone(),
            tags: vec![],
            is_excluded: false,
            source_platform: "manual".to_string(),
            external_id: Some("rent-real".to_string()),
            duplicate_status: DuplicateStatus::New,
            duplicate_transaction_id: None,
            selected: true,
        };
        assert_eq!(
            confirm_import_preview(&store, &ledger.id, &account.id, &[item]).unwrap(),
            1
        );

        let pending = store.list_pending_confirmations().unwrap();
        assert_eq!(pending.len(), 1);
        assert_eq!(pending[0].status, "confirmed");
        assert!(pending[0].transaction_id.is_some());
    }
}
