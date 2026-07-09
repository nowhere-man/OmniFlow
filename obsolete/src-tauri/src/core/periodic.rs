use ulid::Ulid;

use crate::error::AppError;
use crate::models::{PendingConfirmation, Transaction};
use crate::ports::storage::LedgerStore;

const MATCH_WINDOW_SECONDS: i64 = 7 * 24 * 60 * 60;

pub fn generate_due_confirmations<S: LedgerStore>(
    store: &S,
    now_ts: i64,
) -> Result<Vec<PendingConfirmation>, AppError> {
    let existing = store.list_pending_confirmations()?;
    let mut created = Vec::new();
    let now = chrono::Utc::now().timestamp();

    for bill in store.list_periodic_bills()? {
        if bill.next_date > now_ts {
            continue;
        }

        let already_pending = existing.iter().any(|pending| {
            pending.periodic_bill_id == bill.id
                && pending.due_date == bill.next_date
                && pending.deleted_at.is_none()
        });
        if already_pending {
            continue;
        }

        let pending = PendingConfirmation {
            id: Ulid::new().to_string(),
            periodic_bill_id: bill.id,
            transaction_id: None,
            due_date: bill.next_date,
            amount: bill.amount,
            bill_type: bill.bill_type,
            category_id: bill.category_id,
            account_id: bill.account_id,
            status: "pending".to_string(),
            created_at: now,
            updated_at: now,
            deleted_at: None,
        };
        store.create_pending_confirmation(&pending)?;
        created.push(pending);
    }

    Ok(created)
}

pub fn match_pending_confirmation<S: LedgerStore>(
    store: &S,
    transaction: &Transaction,
) -> Result<(), AppError> {
    let mut pending = store
        .list_pending_confirmations()?
        .into_iter()
        .find(|pending| {
            pending.status == "pending"
                && pending.account_id == transaction.account_id
                && pending.category_id == transaction.category_id
                && pending.bill_type == transaction.transaction_type
                && (pending.amount - transaction.amount).abs() <= 0.01
                && (pending.due_date - transaction.transaction_date).abs() <= MATCH_WINDOW_SECONDS
        });

    if let Some(ref mut pending) = pending {
        pending.status = "confirmed".to_string();
        pending.transaction_id = Some(transaction.id.clone());
        pending.updated_at = chrono::Utc::now().timestamp();
        store.update_pending_confirmation(pending)?;
    }

    Ok(())
}
