use crate::core::sync_engine::SyncEngine;
use crate::error::AppError;
use tauri::AppHandle;
use tauri::Manager;

#[tauri::command]
pub fn sync_to_webdav(
    app: AppHandle,
    url: String,
    user: String,
    pass: String,
    encrypt_key: String,
) -> Result<(), AppError> {
    let app_dir = app
        .path()
        .app_data_dir()
        .map_err(|e| AppError::IoError(e.to_string()))?;
    let db_path = app_dir.join("ominiflow.db").to_string_lossy().to_string();

    let engine = SyncEngine::new();
    engine.backup_to_webdav(&db_path, &url, &user, &pass, &encrypt_key)
}

#[tauri::command]
pub fn restore_from_webdav(
    app: AppHandle,
    url: String,
    user: String,
    pass: String,
    encrypt_key: String,
) -> Result<(), AppError> {
    let app_dir = app
        .path()
        .app_data_dir()
        .map_err(|e| AppError::IoError(e.to_string()))?;
    let db_path = app_dir.join("ominiflow.db").to_string_lossy().to_string();

    let engine = SyncEngine::new();
    engine.restore_from_webdav(&db_path, &url, &user, &pass, &encrypt_key)
}
