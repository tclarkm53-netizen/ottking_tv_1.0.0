# Secure IPTV & OTT Streaming Server (PHP)

A production-ready, high-performance PHP backend for Live TV streaming, Movies, Series, User Authentication, Subscription Expiry Management, and Server Push Notifications.

---

## 🌟 Key Features

1. **User Authentication & VIP Subscriptions**:
   - Handles login requests with username, password, and hardware device binding.
   - Calculates real-time subscription validity against timestamps.
   - Automatically supports active VIP accounts and identifies expired accounts.

2. **Automatic VIP / Premium Content Filtering**:
   - When a user logs in with an **ACTIVE** subscription, the server unlocks and delivers all 4K VIP movies and live channels.
   - When a user is **EXPIRED** or a guest, all VIP / Premium content (`is_premium: true`) is omitted from the server response automatically.

3. **Server-Side Push Notifications**:
   - Broadcast announcements, new movie additions, and sports schedules from `notifications.json` or the Admin Dashboard.
   - Android client automatically syncs notifications and displays system push alerts.

4. **Web Admin Dashboard (`admin.php`)**:
   - Broadcast push notifications to all connected app users with 1 click.
   - Manage channels, movies, and series (toggle VIP/Free access, categories).
   - Manage users and extend subscriptions (+30 days, +1 year).
   - Toggle Server Maintenance Mode on/off.

5. **Security & Cryptography**:
   - AES-256-CBC Payload Encryption.
   - HMAC-SHA256 Request Authentication Handshake.
   - API Key verification.

---

## 🚀 API Endpoints

All endpoints use `index.php?route=<route_name>` or headers:

| Route | Method | Description |
|---|---|---|
| `login` | `POST` / `GET` | User login, checks subscription status, returns expiry timestamp and plan |
| `live_tv` / `tv` | `GET` | Returns all Live TV channels (filters VIP if subscription inactive) |
| `movies` / `movie` | `GET` | Returns all Movies & VOD (filters VIP if subscription inactive) |
| `series` | `GET` | Returns TV Series & Seasons |
| `channels` | `GET` | Returns full master catalog |
| `notifications` | `GET` | Returns broadcast notifications sent from server admin |
| `categories` | `GET` | Returns categories and custom artwork icons |
| `update` | `GET` | Returns app version and update metadata |
| `health` | `GET` | Health check endpoint |

---

## 🛠️ How to Deploy

### cPanel / Shared Hosting / Alwaysdata / Apache / Nginx:
1. Upload the contents of `/server/` (`index.php`, `admin.php`, `channels.json`, `notifications.json`, `users.json`) to your web server (e.g. `public_html/api/` or root).
2. Set file permissions on `.json` files to `664` or `775` so the admin panel can write updates.
3. Open `https://your-domain.com/admin.php` to access the Admin Panel (Default login: `admin` / `admin123`).
4. In the Android App settings or `PreferenceUtils`, set the API URL to:
   `https://your-domain.com/index.php`

---

## 👥 Default Demo Accounts

- **VIP Active Account**: `vip_user` / `pass123` (Active VIP Subscription till 2028)
- **Admin Account**: `admin@iptvstream.com` / `admin123` (Active Unlimited till 2030)
- **Expired Account**: `expired_user` / `123456` (Expired Subscription - tests auto-hiding of premium channels)
