use crate::core::parsers::RawTransaction;
use crate::models::Transaction;

pub struct DedupEngine;

impl DedupEngine {
    pub fn new() -> Self {
        Self
    }

    /// Checks if the given raw transaction is an absolute duplicate of any existing transaction.
    /// Absolute deduplication strictly relies on `external_source` and `external_id`.
    pub fn is_absolute_duplicate(
        raw: &RawTransaction,
        existing_txs: &[Transaction],
        source_name: &str,
    ) -> bool {
        if let Some(ext_id) = &raw.external_id {
            for existing in existing_txs {
                if existing.external_source.as_deref() == Some(source_name)
                    && existing.external_id.as_deref() == Some(ext_id)
                {
                    return true;
                }
            }
        }
        false
    }

    /// Checks if the given raw transaction is a fuzzy duplicate.
    /// Fuzzy deduplication uses a ±2 hour window, amount match, and merchant text similarity.
    /// CCB has only day precision, so if CCB is involved, it uses a ±24 hour window.
    pub fn is_fuzzy_duplicate(
        raw: &RawTransaction,
        existing_txs: &[Transaction],
        source_name: &str,
    ) -> Option<String> {
        let is_ccb = source_name == "ccb";
        let time_window = if is_ccb { 86400 } else { 7200 }; // 24 hours vs 2 hours

        for existing in existing_txs {
            // Check amount
            if (existing.amount - raw.amount).abs() > 0.01 {
                continue;
            }

            // Check time window
            let diff = (existing.transaction_date - raw.transaction_date).abs();
            if diff > time_window {
                continue;
            }

            // Check merchant/notes fuzzy match
            let ext_text = format!(
                "{} {}",
                existing.merchant.as_deref().unwrap_or(""),
                existing.notes.as_deref().unwrap_or("")
            );
            let raw_text = format!(
                "{} {}",
                raw.merchant.as_deref().unwrap_or(""),
                raw.notes.as_deref().unwrap_or("")
            );

            // Very simple substring overlap check for now. Can use Levenshtein distance later.
            if !ext_text.trim().is_empty() && !raw_text.trim().is_empty() {
                if ext_text.contains(&raw_text) || raw_text.contains(&ext_text) || diff == 0 {
                    return Some(existing.id.clone());
                }
            } else if diff <= time_window / 2 {
                // If text is empty but time and amount are very close
                return Some(existing.id.clone());
            }
        }

        None
    }
}
