use crate::adapters::sqlite_store::SqliteStore;
use crate::core::stats_engine::{
    AssetData, CategoryBreakdown, ComparisonData, DashboardSummary, RankedTransaction, TagStat,
    TrendDataPoint,
};
use crate::error::AppError;
use tauri::State;

#[tauri::command]
pub fn get_monthly_trend(
    store: State<'_, SqliteStore>,
    ledger_id: String,
    start_ts: i64,
    end_ts: i64,
) -> Result<Vec<TrendDataPoint>, AppError> {
    let engine = crate::core::stats_engine::StatsEngine::new(store.get_conn());
    engine.get_monthly_trend(&ledger_id, start_ts, end_ts)
}

#[tauri::command]
pub fn get_trend(
    store: State<'_, SqliteStore>,
    ledger_id: String,
    start_ts: i64,
    end_ts: i64,
    granularity: String,
) -> Result<Vec<TrendDataPoint>, AppError> {
    let engine = crate::core::stats_engine::StatsEngine::new(store.get_conn());
    engine.get_trend(&ledger_id, start_ts, end_ts, &granularity)
}

#[tauri::command]
pub fn get_category_breakdown(
    store: State<'_, SqliteStore>,
    ledger_id: String,
    start_ts: i64,
    end_ts: i64,
    tx_type: String,
) -> Result<Vec<CategoryBreakdown>, AppError> {
    let engine = crate::core::stats_engine::StatsEngine::new(store.get_conn());
    engine.get_category_breakdown(&ledger_id, start_ts, end_ts, &tx_type)
}

#[tauri::command]
pub fn get_assets_overview(store: State<'_, SqliteStore>) -> Result<Vec<AssetData>, AppError> {
    let engine = crate::core::stats_engine::StatsEngine::new(store.get_conn());
    engine.get_assets_overview()
}

#[tauri::command]
pub fn get_comparison(
    store: State<'_, SqliteStore>,
    ledger_id: String,
    current_start: i64,
    current_end: i64,
    previous_start: i64,
    previous_end: i64,
) -> Result<ComparisonData, AppError> {
    let engine = crate::core::stats_engine::StatsEngine::new(store.get_conn());
    engine.get_comparison(
        &ledger_id,
        current_start,
        current_end,
        previous_start,
        previous_end,
    )
}

#[tauri::command]
pub fn get_tag_stats(
    store: State<'_, SqliteStore>,
    ledger_id: String,
    start_ts: i64,
    end_ts: i64,
) -> Result<Vec<TagStat>, AppError> {
    let engine = crate::core::stats_engine::StatsEngine::new(store.get_conn());
    engine.get_tag_stats(&ledger_id, start_ts, end_ts)
}

#[tauri::command]
pub fn get_top_transactions(
    store: State<'_, SqliteStore>,
    ledger_id: String,
    start_ts: i64,
    end_ts: i64,
    tx_type: String,
    limit: i64,
) -> Result<Vec<RankedTransaction>, AppError> {
    let engine = crate::core::stats_engine::StatsEngine::new(store.get_conn());
    engine.get_top_transactions(&ledger_id, start_ts, end_ts, &tx_type, limit)
}

#[tauri::command]
pub fn get_dashboard_summary(
    store: State<'_, SqliteStore>,
    ledger_id: String,
    start_ts: i64,
    end_ts: i64,
) -> Result<DashboardSummary, AppError> {
    let engine = crate::core::stats_engine::StatsEngine::new(store.get_conn());
    engine.get_dashboard_summary(&ledger_id, start_ts, end_ts)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stats_command_surface_exposes_analysis_apis() {
        let _ = get_trend;
        let _ = get_comparison;
        let _ = get_tag_stats;
        let _ = get_top_transactions;
        let _ = get_dashboard_summary;
    }
}
