use crate::adapters::aes_crypto::AesCrypto;
use crate::error::AppError;
use crate::ports::crypto::CryptoProvider;
use reqwest::blocking::Client;

pub struct SyncEngine {
    crypto: AesCrypto,
}

impl SyncEngine {
    pub fn new() -> Self {
        Self {
            crypto: AesCrypto::new(),
        }
    }

    pub fn backup_to_webdav(
        &self,
        db_path: &str,
        webdav_url: &str,
        user: &str,
        pass: &str,
        encrypt_key: &str,
    ) -> Result<(), AppError> {
        let content = std::fs::read(db_path).map_err(|e| AppError::IoError(e.to_string()))?;
        let encrypted = self.crypto.encrypt(&content, encrypt_key)?;
        let response = http_client()?
            .put(webdav_backup_url(webdav_url))
            .basic_auth(user, Some(pass))
            .body(encrypted)
            .send()
            .map_err(|e| AppError::SyncError(e.to_string()))?;
        response
            .error_for_status()
            .map_err(|e| AppError::SyncError(e.to_string()))?;

        Ok(())
    }

    pub fn restore_from_webdav(
        &self,
        db_path: &str,
        webdav_url: &str,
        user: &str,
        pass: &str,
        encrypt_key: &str,
    ) -> Result<(), AppError> {
        let downloaded_encrypted = http_client()?
            .get(webdav_backup_url(webdav_url))
            .basic_auth(user, Some(pass))
            .send()
            .map_err(|e| AppError::SyncError(e.to_string()))?
            .error_for_status()
            .map_err(|e| AppError::SyncError(e.to_string()))?
            .text()
            .map_err(|e| AppError::SyncError(e.to_string()))?;

        let decrypted = self.crypto.decrypt(&downloaded_encrypted, encrypt_key)?;
        std::fs::write(db_path, decrypted).map_err(|e| AppError::IoError(e.to_string()))?;

        Ok(())
    }
}

fn webdav_backup_url(webdav_url: &str) -> String {
    let trimmed = webdav_url.trim_end_matches('/');
    if trimmed.ends_with(".enc") {
        trimmed.to_string()
    } else {
        format!("{}/ominiflow.db.enc", trimmed)
    }
}

fn http_client() -> Result<Client, AppError> {
    Client::builder()
        .no_proxy()
        .build()
        .map_err(|e| AppError::SyncError(e.to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ports::crypto::CryptoProvider;
    use std::io::{BufRead, BufReader, Read, Write};
    use std::net::TcpListener;
    use std::sync::mpsc;
    use std::thread;
    use std::time::Duration;

    fn temp_file(name: &str) -> std::path::PathBuf {
        std::env::temp_dir().join(format!("omniflow-{}-{}", name, ulid::Ulid::new()))
    }

    fn read_request(mut stream: &std::net::TcpStream) -> (String, Vec<u8>) {
        let mut reader = BufReader::new(&mut stream);
        let mut headers = String::new();
        let mut content_length = 0usize;
        loop {
            let mut line = String::new();
            reader.read_line(&mut line).expect("header should read");
            if line == "\r\n" {
                break;
            }
            let lower = line.to_ascii_lowercase();
            if let Some((_, value)) = lower.split_once("content-length:") {
                content_length = value.trim().parse().expect("length should parse");
            }
            headers.push_str(&line);
        }
        let mut body = vec![0; content_length];
        reader.read_exact(&mut body).expect("body should read");
        (headers, body)
    }

    #[test]
    fn webdav_backup_puts_encrypted_payload_and_restore_gets_remote_payload() {
        let source = temp_file("source.db");
        let restored = temp_file("restored.db");
        std::fs::write(&source, b"local sqlite bytes").expect("source should write");

        let put_listener = TcpListener::bind("127.0.0.1:0").expect("listener should bind");
        let put_addr = put_listener.local_addr().unwrap();
        let (put_tx, put_rx) = mpsc::channel();
        thread::spawn(move || {
            let (mut stream, _) = put_listener.accept().expect("put should connect");
            let (headers, body) = read_request(&stream);
            write!(stream, "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n").unwrap();
            put_tx.send((headers, body)).unwrap();
        });

        let engine = SyncEngine::new();
        engine
            .backup_to_webdav(
                source.to_str().unwrap(),
                &format!("http://{}/backup", put_addr),
                "user",
                "pass",
                "secret",
            )
            .expect("backup should upload");
        let (headers, uploaded) = put_rx
            .recv_timeout(Duration::from_secs(2))
            .expect("upload should be captured");
        assert!(headers.starts_with("PUT /backup/ominiflow.db.enc HTTP/1.1"));
        assert!(!uploaded.is_empty());
        assert_ne!(uploaded, b"local sqlite bytes");

        let encrypted = AesCrypto::new()
            .encrypt(b"remote sqlite bytes", "secret")
            .expect("payload should encrypt");
        let get_listener = TcpListener::bind("127.0.0.1:0").expect("listener should bind");
        let get_addr = get_listener.local_addr().unwrap();
        thread::spawn(move || {
            let (mut stream, _) = get_listener.accept().expect("get should connect");
            let mut first_line = String::new();
            BufReader::new(&mut stream)
                .read_line(&mut first_line)
                .expect("request should read");
            assert!(first_line.starts_with("GET /backup/ominiflow.db.enc HTTP/1.1"));
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Length: {}\r\n\r\n{}",
                encrypted.len(),
                encrypted
            )
            .unwrap();
        });

        engine
            .restore_from_webdav(
                restored.to_str().unwrap(),
                &format!("http://{}/backup", get_addr),
                "user",
                "pass",
                "secret",
            )
            .expect("restore should download");
        assert_eq!(
            std::fs::read(&restored).expect("restored should read"),
            b"remote sqlite bytes"
        );

        let _ = std::fs::remove_file(source);
        let _ = std::fs::remove_file(restored);
    }
}
