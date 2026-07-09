use crate::adapters::sqlite_store::SqliteStore;
use crate::error::AppError;
use std::fs;
use tauri::AppHandle;
use tauri::Manager;

#[tauri::command]
pub fn export_database(app: AppHandle, target_path: String) -> Result<(), AppError> {
    let app_dir = app
        .path()
        .app_data_dir()
        .map_err(|e| AppError::IoError(e.to_string()))?;
    let db_path = app_dir.join("ominiflow.db");

    if db_path.exists() {
        fs::copy(db_path, target_path).map_err(|e| AppError::IoError(e.to_string()))?;
        Ok(())
    } else {
        Err(AppError::IoError("Database file not found".into()))
    }
}

#[tauri::command]
pub fn clear_all_data(store: tauri::State<'_, SqliteStore>) -> Result<(), AppError> {
    let conn = store.get_conn().lock().unwrap();
    // Delete all records but keep structure
    conn.execute_batch(
        "
        DELETE FROM transactions;
        DELETE FROM accounts;
        DELETE FROM ledgers;
        DELETE FROM categories;
        ",
    )
    .map_err(|e| AppError::DatabaseError(e.to_string()))?;

    Ok(())
}
