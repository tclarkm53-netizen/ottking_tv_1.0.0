const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

// Security Keys
const API_KEY = process.env.API_KEY || "iptv_sec_api_key_2026";
const HMAC_KEY = process.env.HMAC_KEY || "iptv_hmac_secret_key_889900";
const ENCRYPTION_KEY = process.env.ENCRYPTION_KEY || "12345678901234567890123456789012"; // 32 characters (256-bit)

app.use(cors());
app.use(express.json());

// Load channels dataset
const channelsPath = path.join(__dirname, 'channels.json');
let channelsData = [];
try {
  channelsData = JSON.parse(fs.readFileSync(channelsPath, 'utf8'));
  console.log(`[SERVER] Loaded ${channelsData.length} channels from channels.json`);
} catch (err) {
  console.error('[SERVER] Failed to load channels.json:', err.message);
}

// Load settings
const settingsPath = path.join(__dirname, 'settings.json');
let appSettings = {
  whatsapp_url: "https://wa.me/8801700000000",
  telegram_url: "https://t.me/telegram",
  app_share_url: "https://verify-app.alwaysdata.net/download",
  developer_info: "Official Stream IPTV Engine v2.0.0\nPowered by Secure Cloud Infrastructure & Real-Time AES-256 Content Delivery System.",
  version_code: 2,
  version_name: "2.0.0",
  min_version_code: 1,
  force_update: false,
  title: "🚀 New Version 2.0.0 Available!",
  message: "A new update for Live TV Player is available with improved video playback, better stability, and new features.",
  download_url: "https://verify-app.alwaysdata.net/new/mobile/app-release.apk",
  release_notes: "• Faster HD stream loading\n• Enhanced video player controls\n• Automatic category filtering\n• Performance and bug fixes"
};

try {
  if (fs.existsSync(settingsPath)) {
    const saved = JSON.parse(fs.readFileSync(settingsPath, 'utf8'));
    appSettings = { ...appSettings, ...saved };
  }
} catch (err) {
  console.error('[SERVER] Failed to load settings.json:', err.message);
}

// AES-256-GCM Encryption Helper matching Crypto_lib
function encryptData(text, secretKey) {
  const iv = crypto.randomBytes(12);
  const keyBytes = Buffer.alloc(32);
  Buffer.from(secretKey, 'utf8').copy(keyBytes, 0, 0, Math.min(32, secretKey.length));

  const cipher = crypto.createCipheriv('aes-256-gcm', keyBytes, iv);
  let encrypted = cipher.update(text, 'utf8');
  encrypted = Buffer.concat([encrypted, cipher.final()]);
  const tag = cipher.getAuthTag();
  const combined = Buffer.concat([encrypted, tag]);

  const payload = iv.toString('base64') + '.' + combined.toString('base64');
  const hmac = crypto.createHmac('sha256', HMAC_KEY).update(text).digest('hex');

  return {
    encryptedPayload: payload,
    signature: hmac
  };
}

// HMAC Verification Middleware
function verifySecurity(req, res, next) {
  const apiKey = req.headers['x-api-key'] || req.query.apiKey;
  const timestamp = req.headers['x-timestamp'] || req.query.timestamp;
  const signature = req.headers['x-hmac-signature'] || req.query.signature;

  if (!apiKey || apiKey !== API_KEY) {
    return res.status(401).json({ status: 'error', message: 'Invalid or missing API Key' });
  }

  if (!timestamp || !signature) {
    return res.status(400).json({ status: 'error', message: 'Missing timestamp or HMAC signature headers' });
  }

  // Calculate expected HMAC
  const uri = req.baseUrl + req.path;
  const expectedHmac = crypto
    .createHmac('sha256', HMAC_KEY)
    .update(timestamp + uri)
    .digest('hex');

  if (signature.toLowerCase() !== expectedHmac.toLowerCase()) {
    console.warn(`[SECURITY FAIL] Signature mismatch. Received: ${signature}, Expected: ${expectedHmac}`);
    return res.status(403).json({ status: 'error', message: 'HMAC signature verification failed' });
  }

  next();
}

// Health Check Endpoint
app.get('/api/health', (req, res) => {
  res.json({
    status: 'online',
    serverTime: new Date().toISOString(),
    security: {
      apiKeyRequired: true,
      hmacEnabled: true,
      encryption: 'AES-256-CBC'
    }
  });
});

// Secure Channels API Endpoint
app.get('/api/channels', verifySecurity, (req, res) => {
  try {
    const rawJson = JSON.stringify(channelsData);
    const encryptedPayload = encryptData(rawJson, ENCRYPTION_KEY);

    res.json({
      status: 'success',
      timestamp: Date.now(),
      whatsapp_url: appSettings.whatsapp_url,
      telegram_url: appSettings.telegram_url,
      app_share_url: appSettings.app_share_url,
      developer_info: appSettings.developer_info,
      count: channelsData.length,
      encrypted_payload: encryptedPayload.encryptedPayload,
      signature: encryptedPayload.signature,
      data: encryptedPayload.encryptedPayload,
      app_update: {
        version_code: appSettings.version_code,
        version_name: appSettings.version_name,
        min_version_code: appSettings.min_version_code,
        force_update: appSettings.force_update,
        title: appSettings.title,
        message: appSettings.message,
        download_url: appSettings.download_url,
        release_notes: appSettings.release_notes
      }
    });
  } catch (error) {
    console.error('[SERVER ERROR]', error);
    res.status(500).json({ status: 'error', message: 'Failed to encrypt channel payload' });
  }
});

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`=================================================`);
  console.log(` Secure IPTV API Server listening on port ${PORT}`);
  console.log(` Endpoint: http://localhost:${PORT}/api/channels`);
  console.log(` API Key: ${API_KEY}`);
  console.log(` HMAC Key: ${HMAC_KEY}`);
  console.log(` Encryption Key: ${ENCRYPTION_KEY}`);
  console.log(`=================================================`);
});
