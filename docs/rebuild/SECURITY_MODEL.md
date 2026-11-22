# Dropway Security, Cryptography & Integrity Model

## 1. Threat Model & Security Objectives

Dropway handles sensitive delivery information: customer recipient names, phone numbers, exact house geolocation coordinates, delivery notes, and cash-on-delivery amounts. The security model addresses the following core risks:
1. **Unauthorized Access & Multi-Tenant Leaks:** Ensuring drivers only access folders and houses they own or have been explicitly granted share access to.
2. **Device-Level Data at Rest Theft:** Safeguarding cached local SQLite records and API session tokens on lost or stolen mobile devices.
3. **Man-In-The-Middle & Network Snooping:** Protecting sync payloads and OCR image transfers over insecure Wi-Fi / cellular connections.
4. **QR Code Tampering & Replay Attacks:** Preventing malicious actors from spoofing folder share payloads or tampering with address coordinates in transit.

---

## 2. Cryptographic Storage & Android KeyStore

### Mobile Device Security (`data/sharing/VaultCryptoManager.kt`)
- **AndroidKeyStore Provider:** Encryption keys (`MasterKey`) are generated and maintained within hardware-backed security modules (TEE / StrongBox) when available on Android 9+ (API 28+).
- **Cipher Suite:** `AES/GCM/NoPadding` with 256-bit key length and 128-bit authentication tag.
- **Key Generation Parameters:**
  - Key Alias: `dropway_vault_master_key`
  - Purpose: `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`
  - Block Mode: `BLOCK_MODE_GCM`
  - Padding: `ENCRYPTION_PADDING_NONE`
  - User Authentication Requirement: Optionally bound to biometric / device lock if enabled by enterprise policy.
- **Initialization Vector (IV):** A unique, cryptographically random 12-byte IV is generated per encryption operation via `SecureRandom` and prepended to the ciphertext.

---

## 3. QR Sharing & Signature Verification

When sharing a folder or route via QR code:
1. **Token Generation:** A cryptographic token payload is created containing:
   - `folderId`: Unique folder UUID.
   - `ownerId`: Originator UUID.
   - `permissions`: `READ_ONLY` or `READ_WRITE`.
   - `expiresAt`: Epoch millisecond timestamp (default: 24 hours).
   - `nonce`: 16-byte random salt.
2. **Signature Generation:**
   $$\text{Signature} = \text{HMAC-SHA256}(\text{SecretKey}, \text{PayloadBody})$$
3. **Verification Protocol:**
   - The recipient scanner decrypts and checks the expiry timestamp against local clock.
   - Verifies the HMAC-SHA256 signature using the shared secret or server verification endpoint.
   - Rejects expired, tampered, or replayed tokens.

---

## 4. Network Security & Token Management

- **Transport:** TLS 1.3 enforced for all API communication with strict host verification.
- **Authentication Headers:** Short-lived JWT access tokens accompanied by secure refresh tokens.
- **Token Storage:** Tokens are stored in private SharedPreferences encrypted with `VaultCryptoManager`.
- **Anti-Tampering:** Every mutation payload sent to `/api/sync/push` includes a client-generated signature ensuring payload integrity during relay.

---

## 5. UI Security & Zero Sensitive Leakage

- **Logging Sanitization:** All sensitive fields (`phoneNumber`, `recipientName`, `codAmount`) are masked in logs (e.g. `081****678`).
- **Screen Protection:** Flag `FLAG_SECURE` can be enabled in settings to prevent unauthorized screenshots or screen capture of customer address lists.
