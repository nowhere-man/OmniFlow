use crate::adapters::sqlite_store::SqliteStore;
use crate::error::AppError;
use crate::models::Category;
use crate::ports::storage::LedgerStore;
use tauri::State;

#[tauri::command]
pub fn list_categories(store: State<'_, SqliteStore>) -> Result<Vec<Category>, AppError> {
    store.list_categories()
}

#[tauri::command]
pub fn create_category(store: State<'_, SqliteStore>, category: Category) -> Result<(), AppError> {
    store.create_category(&category)
}

#[tauri::command]
pub fn update_category(store: State<'_, SqliteStore>, category: Category) -> Result<(), AppError> {
    store.update_category(&category)
}

#[tauri::command]
pub fn delete_category(store: State<'_, SqliteStore>, id: String) -> Result<(), AppError> {
    // We implement a simple hard delete or soft delete for category
    // In SqliteStore, we don't have delete_category yet. We should add it or use raw SQL here.
    let conn = store.get_conn().lock().unwrap();
    let now = chrono::Utc::now().timestamp();
    conn.execute(
        "UPDATE categories SET deleted_at = ?1 WHERE id = ?2",
        rusqlite::params![now, id],
    )
    .map_err(|e| AppError::DatabaseError(e.to_string()))?;
    Ok(())
}
