#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("Database error: {0}")]
    DatabaseError(String),

    #[error("File I/O error: {0}")]
    IoError(String),

    #[error("Parsing error: {0}")]
    ParseError(String),

    #[error("Validation error: {0}")]
    ValidationError(String),

    #[error("Not found: {0}")]
    NotFoundError(String),

    #[error("Sync error: {0}")]
    SyncError(String),

    #[error("Crypto error: {0}")]
    CryptoError(String),

    #[error("Internal error: {0}")]
    InternalError(String),
}

impl AppError {
    fn code(&self) -> &'static str {
        match self {
            AppError::DatabaseError(_) => "database_error",
            AppError::IoError(_) => "io_error",
            AppError::ParseError(_) => "parse_error",
            AppError::ValidationError(_) => "validation_error",
            AppError::NotFoundError(_) => "not_found",
            AppError::SyncError(_) => "sync_error",
            AppError::CryptoError(_) => "crypto_error",
            AppError::InternalError(_) => "internal_error",
        }
    }

    fn kind(&self) -> &'static str {
        match self {
            AppError::ValidationError(_) | AppError::NotFoundError(_) | AppError::ParseError(_) => {
                "business"
            }
            AppError::IoError(_) | AppError::SyncError(_) | AppError::CryptoError(_) => {
                "recoverable"
            }
            AppError::DatabaseError(_) | AppError::InternalError(_) => "fatal",
        }
    }
}

// Convert rusqlite errors to AppError
impl From<rusqlite::Error> for AppError {
    fn from(err: rusqlite::Error) -> Self {
        AppError::DatabaseError(err.to_string())
    }
}

// Allow AppError to be sent to JS as JSON
impl serde::Serialize for AppError {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        use serde::ser::SerializeStruct;

        let mut state = serializer.serialize_struct("AppError", 3)?;
        state.serialize_field("code", self.code())?;
        state.serialize_field("kind", self.kind())?;
        state.serialize_field("message", &self.to_string())?;
        state.end()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn app_error_serializes_as_structured_json() {
        let value = serde_json::to_value(AppError::ValidationError("金额必须大于 0".to_string()))
            .expect("error should serialize");

        assert_eq!(value["code"], "validation_error");
        assert_eq!(value["kind"], "business");
        assert_eq!(value["message"], "Validation error: 金额必须大于 0");
    }
}
