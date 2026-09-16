# OTT KING - Server Security & Stream Token Verification Architecture

## 1. Security Overview
This backend implements two-tier stream security:
1. **`X-App-Client` Verification:** All API and video playback requests require the cryptographically verified `X-App-Client` token (`ott_king_client_token_2026`).
2. **`X-Stream-Token` / Bearer Authentication:** ExoPlayer automatically requests signed, time-limited stream tokens from `stream-token.php` and attaches `X-Stream-Token` & `Authorization: Bearer <token>` to all live stream video requests.

---

## 2. API Endpoints

### A. Request Stream Token (`POST stream-token.php`)
- **Headers:**
  - `X-Api-Key: ott_king_secret_api_key_2026`
  - `X-App-Client: ott_king_client_token_2026`
  - `Content-Type: application/json`
- **Request Body (Encrypted AES-256-GCM + HMAC-SHA256):**
  ```json
  {
    "channel_id": 1,
    "stream_url": "https://live.example.com/stream.m3u8",
    "session_token": "...",
    "device_id": "...",
    "app_client": "ott_king_client_token_2026",
    "timestamp": 1772345678
  }
  ```
- **Response:**
  ```json
  {
    "status": "success",
    "channel_id": 1,
    "stream_token": "eyJjaGFubmVsSWQiOjEsLi4ufQ==",
    "expires_at": 1772360078000,
    "app_client": "ott_king_client_token_2026",
    "authorization_header": "Bearer eyJ...",
    "authorized_stream_url": "https://live.example.com/stream.m3u8"
  }
  ```

---

### B. Edge Cookie Lifecycle (`/cookie` or `cookie.php`)

#### 1. Initial App Launch (অ্যাপ চালু হওয়ার সময় - কোনো পে-লোড লাগবে না):
- **Request:** `GET /cookie` (No payload, no body, no parameters required)
- **Behavior:** The server detects this is the initial application launch and immediately issues a bootstrap signed Edge-Cookie.
- **Response:**
  ```json
  {
    "status": "success",
    "mode": "initial_launch",
    "message": "Initial app launch cookie issued (no payload required)",
    "cookie": "base64_signed_edge_cookie...",
    "edge_cookie": "base64_signed_edge_cookie...",
    "token": "base64_signed_edge_cookie...",
    "cookie_json": {
      "edge_cookie": "base64_signed_edge_cookie...",
      "cookie": "base64_signed_edge_cookie...",
      "type": "initial_launch",
      "issued_at": 1772345678,
      "expires_at": 1772432078000
    },
    "last_cookie_verified": false
  }
  ```

#### 2. Runtime Renewal (রান টাইমে লাষ্ট কুকি যাচাই করে নিউ কুকি ইস্যু):
- **Request:** `POST /cookie`
- **Request Payload:**
  ```json
  {
    "last_cookie": "previous_edge_cookie_received...",
    "edge_cookie": "previous_edge_cookie_received...",
    "channel_id": 1,
    "stream_url": "https://live.example.com/stream.m3u8",
    "session_token": "...",
    "device_id": "...",
    "cookie_json": {
      "last_cookie": "previous_edge_cookie_received...",
      "edge_cookie": "previous_edge_cookie_received...",
      "channel_id": 1,
      "device_id": "..."
    }
  }
  ```
- **Behavior:** Server extracts and verifies the `last_cookie` HMAC signature and validity. Upon verification, the server issues a brand new renewed cookie (`new_cookie`) and returns it to the client.
- **Response:**
  ```json
  {
    "status": "success",
    "mode": "runtime_renewal",
    "message": "Last cookie verified successfully. New runtime cookie issued.",
    "last_cookie_verified": true,
    "last_cookie": "previous_edge_cookie_received...",
    "new_cookie": "new_base64_signed_edge_cookie...",
    "cookie": "new_base64_signed_edge_cookie...",
    "edge_cookie": "new_base64_signed_edge_cookie...",
    "token": "new_base64_signed_edge_cookie...",
    "channel_id": 1,
    "expires_at": 1772432078000,
    "cookie_json": {
      "edge_cookie": "new_base64_signed_edge_cookie...",
      "cookie": "new_base64_signed_edge_cookie...",
      "last_cookie": "previous_edge_cookie_received...",
      "channel_id": 1,
      "device_id": "...",
      "session_token": "...",
      "issued_at": 1772345678,
      "expires_at": 1772432078000
    }
  }
  ```
- **Response Headers:**
  - `Edge-Cookie: new_base64_signed_edge_cookie...`
  - `X-Edge-Cookie: new_base64_signed_edge_cookie...`
  - `Set-Cookie: edge_cookie=...; Path=/; HttpOnly; SameSite=Lax`
  - `X-Cookie-Json: {"edge_cookie":"...","cookie":"...","last_cookie":"..."}`

---

### C. Verify Stream Token & Edge Cookie (`GET / POST verify-stream.php`)
- **Headers Verified:**
  - `X-App-Client`
  - `X-Stream-Token`
  - `Authorization: Bearer <stream_token>`
  - `Edge-Cookie`, `edge-cookie`, `X-Edge-Cookie`
  - `X-Cookie-Json` (JSON object containing cookie)
  - `Cookie: edge_cookie=...`
  - `X-Device-Id`
- **Query Params / JSON Body (Fallback):**
  - `?channel_id=1&stream_token=...&edge_cookie=...&cookie_json=...`

---

## 3. Nginx / Stream Server Token Verification Integration Example

```nginx
location /hls/ {
    # Check X-App-Client header
    if ($http_x_app_client != "ott_king_client_token_2026") {
        return 403;
    }
    
    # Pass auth to PHP auth subrequest
    auth_request /auth_verify;
    
    # Serve stream segments
    types {
        application/vnd.apple.mpegurl m3u8;
        video/mp2t ts;
    }
    root /var/www/streams;
}

location = /auth_verify {
    internal;
    proxy_pass http://127.0.0.1/server/verify-stream.php;
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
    proxy_set_header X-Original-URI $request_uri;
}
```
