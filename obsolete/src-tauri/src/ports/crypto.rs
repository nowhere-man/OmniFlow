use crate::error::AppError;

pub trait CryptoProvider: Send + Sync {
    /// 加密给定的明文，返回 Base64 编码的密文串 (含 Nonce / Salt)
    fn encrypt(&self, plaintext: &[u8], password: &str) -> Result<String, AppError>;

    /// 解密给定的 Base64 密文串，返回明文
    fn decrypt(&self, ciphertext_b64: &str, password: &str) -> Result<Vec<u8>, AppError>;
}
