#![allow(clippy::new_without_default)]
pub mod adapters;
pub mod commands;
pub mod core;
pub mod error;
pub mod models;
pub mod ports;

use adapters::sqlite_store::SqliteStore;
use ports::storage::LedgerStore;
use std::fs;
use std::path::PathBuf;
use tauri::Manager;

pub fn init_db(app_dir: PathBuf) -> Result<SqliteStore, error::AppError> {
    fs::create_dir_all(&app_dir).map_err(|e| error::AppError::IoError(e.to_string()))?;
    let db_path = app_dir.join("ominiflow.db");

    let store = SqliteStore::new(db_path.to_str().unwrap())?;

    // Run migrations
    store.run_migrations()?;

    // Initialize default data if ledgers are empty
    let ledgers = store.list_ledgers()?;
    if ledgers.is_empty() {
        store.init_defaults()?;
    }

    Ok(store)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let app_dir = app
                .path()
                .app_data_dir()
                .unwrap_or_else(|_| PathBuf::from("."));
            let store = init_db(app_dir).expect("Failed to initialize database");
            app.manage(store);
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::list_ledgers,
            commands::create_ledger,
            commands::update_ledger,
            commands::delete_ledger,
            commands::list_accounts,
            commands::create_account,
            commands::update_account,
            commands::delete_account,
            commands::list_transactions,
            commands::create_transaction,
            commands::update_transaction,
            commands::delete_transaction,
            commands::search_transactions,
            commands::list_rules,
            commands::create_rule,
            commands::update_rule,
            commands::delete_rule,
            commands::list_periodic_bills,
            commands::create_periodic_bill,
            commands::update_periodic_bill,
            commands::delete_periodic_bill,
            commands::list_pending_confirmations,
            commands::create_pending_confirmation,
            commands::update_pending_confirmation,
            commands::delete_pending_confirmation,
            commands::generate_pending_confirmations,
            commands::import_bill,
            commands::parse_and_preview,
            commands::confirm_import,
            commands::reapply_rules,
            commands::stats::get_monthly_trend,
            commands::stats::get_trend,
            commands::stats::get_category_breakdown,
            commands::stats::get_assets_overview,
            commands::stats::get_comparison,
            commands::stats::get_tag_stats,
            commands::stats::get_top_transactions,
            commands::stats::get_dashboard_summary,
            commands::category::list_categories,
            commands::category::create_category,
            commands::category::update_category,
            commands::category::delete_category,
            commands::data::export_database,
            commands::data::clear_all_data,
            commands::sync::sync_to_webdav,
            commands::sync::restore_from_webdav
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
