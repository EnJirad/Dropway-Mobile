# Dropway REST API Specification

## 1. General Standards

- **Base URL:** `https://dropway.app/api` (Production) / `http://10.0.2.2:3000/api` (Android Emulator Localhost)
- **Content Type:** `application/json`
- **Security:** Standard Bearer Authentication: `Authorization: Bearer <jwt_token>`
- **Idempotency:** Mutations (`POST`, `PUT`, `DELETE`) accept header `Idempotency-Key: <uuid>` to safely retry failed network requests.
- **Client Metadata:** Client headers:
  - `X-Client-Platform: Android`
  - `X-Client-Version: 2.0.0`

### Standard Response Envelope

```json
{
  "success": true,
  "data": {},
  "error": null,
  "timestamp": 1732252800000
}
```

Error response format:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "The requested house ID was not found."
  },
  "timestamp": 1732252800000
}
```

---

## 2. Authentication Endpoints

### `POST /api/auth/register`
Creates a new driver/user account.
- **Request Body:**
  ```json
  {
    "email": "courier@dropway.app",
    "password": "SecurePassword123!",
    "displayName": "Somchai Driver"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "success": true,
    "data": {
      "user": {
        "id": "c8b4d9a1-0000-4000-8000-000000000001",
        "email": "courier@dropway.app",
        "displayName": "Somchai Driver",
        "role": "driver"
      },
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6..."
    }
  }
  ```

### `POST /api/auth/login`
Authenticates user and returns JWT token.
- **Request Body:**
  ```json
  {
    "email": "courier@dropway.app",
    "password": "SecurePassword123!"
  }
  ```
- **Response (200 OK):** (Same data payload as register).

### `GET /api/auth/profile`
Retrieves authenticated user profile and subscription status.
- **Headers:** `Authorization: Bearer <token>`
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": {
      "id": "c8b4d9a1-0000-4000-8000-000000000001",
      "email": "courier@dropway.app",
      "displayName": "Somchai Driver",
      "role": "driver",
      "subscription": {
        "planId": "pro",
        "status": "active",
        "expiresAt": "2027-11-22T00:00:00Z"
      }
    }
  }
  ```

---

## 3. Folders Endpoints

### `GET /api/folders`
Lists all folders belonging to or shared with the authenticated user.
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": [
      {
        "id": "folder-mae-sot",
        "name": "Mae Sot Deliveries",
        "description": "Historical courier records for Mae Sot",
        "color": "#3B82F6",
        "icon": "folder",
        "isDefault": true,
        "houseCount": 4530,
        "updatedAt": "2026-11-22T00:00:00Z"
      }
    ]
  }
  ```

### `POST /api/folders`
Creates a new folder.
- **Request Body:**
  ```json
  {
    "name": "Tha Sai Rung Route",
    "description": "Zone 4 deliveries",
    "color": "#10B981",
    "icon": "truck"
  }
  ```

---

## 4. Houses Endpoints

### `GET /api/houses`
Queries houses with pagination and text search.
- **Query Params:** `query`, `folderId`, `limit` (default 50), `offset` (default 0)
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": {
      "houses": [
        {
          "id": "h-12345",
          "houseNumber": "123/4",
          "village": "Moo 2",
          "subdistrict": "Mae Pa",
          "district": "Mae Sot",
          "province": "Tak",
          "postalCode": "63110",
          "latitude": 16.71234,
          "longitude": 98.56789,
          "phoneNumbers": ["0812345678"],
          "recipientNames": ["Somchai"],
          "notes": "Gate with blue fence",
          "landmark": "Near Mae Pa Temple",
          "version": 1
        }
      ],
      "total": 4530
    }
  }
  ```

### `POST /api/houses`
Creates or updates a house address.
- **Request Body:**
  ```json
  {
    "houseNumber": "123/4",
    "village": "Moo 2",
    "subdistrict": "Mae Pa",
    "district": "Mae Sot",
    "province": "Tak",
    "postalCode": "63110",
    "latitude": 16.71234,
    "longitude": 98.56789,
    "phoneNumbers": ["0812345678"],
    "recipientNames": ["Somchai"],
    "notes": "Gate with blue fence",
    "folderId": "folder-mae-sot"
  }
  ```

---

## 5. Offline Sync Engine Endpoints

### `POST /api/sync/push`
Pushes pending outbox mutations from the mobile client to the cloud.
- **Request Body:**
  ```json
  {
    "mutations": [
      {
        "id": "mutation-uuid-001",
        "entityType": "HOUSE",
        "entityId": "h-12345",
        "action": "UPDATE",
        "clientTimestamp": 1732252800000,
        "payload": {
          "phoneNumbers": ["0812345678", "0898765432"],
          "notes": "Updated note from driver"
        }
      }
    ]
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": {
      "processedIds": ["mutation-uuid-001"],
      "conflicts": []
    }
  }
  ```

### `GET /api/sync/pull`
Pulls server updates since `sinceTimestamp`.
- **Query Params:** `since` (timestamp in ms)
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": {
      "houses": [],
      "folders": [],
      "serverTimestamp": 1732252900000
    }
  }
  ```

---

## 6. AI Vision & Label Endpoints

### `POST /api/ai/label`
Uploads a cropped label image or base64 string for Gemini 2.0 Flash parsing.
- **Request Body:**
  ```json
  {
    "image": "base64_encoded_jpeg...",
    "mimeType": "image/jpeg"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": {
      "rawText": "นาย สมชาย ใจดี 123/4 หมู่ 2 ต.แม่ปะ อ.แม่สอด จ.ตาก 63110 โทร 0812345678",
      "recipientName": "สมชาย ใจดี",
      "phoneNumber": "0812345678",
      "houseNumber": "123/4",
      "village": "2",
      "subdistrict": "แม่ปะ",
      "district": "แม่สอด",
      "province": "ตาก",
      "postalCode": "63110",
      "matchedHouseId": "h-12345",
      "confidence": 0.96
    }
  }
  ```
