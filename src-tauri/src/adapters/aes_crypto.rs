use crate::error::AppError;
use crate::ports::crypto::CryptoProvider;
use base64ct::{Base64, Encoding};
use ring::aead::{
    Aad, BoundKey, Nonce, NonceSequence, OpeningKey, SealingKey, UnboundKey, AES_256_GCM,
};
use ring::error::Unspecified;
use ring::pbkdf2;
use ring::rand::{SecureRandom, SystemRandom};
use std::num::NonZeroU32;

pub struct AesCrypto;

impl AesCrypto {
    pub fn new() -> Self {
        Self
    }

    fn derive_key(&self, password: &str, salt: &[u8]) -> [u8; 32] {
        let mut key = [0u8; 32];
        let iterations = NonZeroU32::new(100_000).unwrap();
        pbkdf2::derive(
            pbkdf2::PBKDF2_HMAC_SHA256,
            iterations,
            salt,
            password.as_bytes(),
            &mut key,
        );
        key
    }
}

struct OneNonceSequence(Option<Nonce>);

impl OneNonceSequence {
    fn new(nonce: Nonce) -> Self {
        Self(Some(nonce))
    }
}

impl NonceSequence for OneNonceSequence {
    fn advance(&mut self) -> Result<Nonce, Unspecified> {
        self.0.take().ok_or(Unspecified)
    }
}

impl CryptoProvider for AesCrypto {
    fn encrypt(&self, plaintext: &[u8], password: &str) -> Result<String, AppError> {
        let rand = SystemRandom::new();

        let mut salt = [0u8; 16];
        rand.fill(&mut salt)
            .map_err(|_| AppError::CryptoError("Failed to generate salt".into()))?;

        let mut nonce_bytes = [0u8; 12];
        rand.fill(&mut nonce_bytes)
            .map_err(|_| AppError::CryptoError("Failed to generate nonce".into()))?;
        let nonce = Nonce::assume_unique_for_key(nonce_bytes);

        let key = self.derive_key(password, &salt);
        let unbound_key = UnboundKey::new(&AES_256_GCM, &key)
            .map_err(|_| AppError::CryptoError("Failed to create unbound key".into()))?;
        let mut sealing_key = SealingKey::new(unbound_key, OneNonceSequence::new(nonce));

        let mut in_out = plaintext.to_vec();
        sealing_key
            .seal_in_place_append_tag(Aad::empty(), &mut in_out)
            .map_err(|_| AppError::CryptoError("Encryption failed".into()))?;

        // Format: version(1 byte) + salt(16) + nonce(12) + ciphertext_with_tag
        let mut final_payload = Vec::new();
        final_payload.push(1u8); // version
        final_payload.extend_from_slice(&salt);
        final_payload.extend_from_slice(&nonce_bytes);
        final_payload.extend_from_slice(&in_out);

        Ok(Base64::encode_string(&final_payload))
    }

    fn decrypt(&self, ciphertext_b64: &str, password: &str) -> Result<Vec<u8>, AppError> {
        let payload = Base64::decode_vec(ciphertext_b64)
            .map_err(|_| AppError::CryptoError("Invalid base64 payload".into()))?;

        if payload.len() < 1 + 16 + 12 + 16 {
            return Err(AppError::CryptoError("Payload too short".into()));
        }

        if payload[0] != 1 {
            return Err(AppError::CryptoError("Unsupported version".into()));
        }

        let salt = &payload[1..17];
        let mut nonce_bytes = [0u8; 12];
        nonce_bytes.copy_from_slice(&payload[17..29]);
        let nonce = Nonce::assume_unique_for_key(nonce_bytes);

        let mut in_out = payload[29..].to_vec();

        let key = self.derive_key(password, salt);
        let unbound_key = UnboundKey::new(&AES_256_GCM, &key)
            .map_err(|_| AppError::CryptoError("Failed to create unbound key".into()))?;
        let mut opening_key = OpeningKey::new(unbound_key, OneNonceSequence::new(nonce));

        let decrypted_data = opening_key
            .open_in_place(Aad::empty(), &mut in_out)
            .map_err(|_| AppError::CryptoError("Decryption failed (Wrong password?)".into()))?;

        Ok(decrypted_data.to_vec())
    }
}
