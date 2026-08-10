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

// AES-256-CBC Encryption Helper
function encryptData(text, secretKey) {
  const iv = crypto.randomBytes(16);
  const cipher = crypto.createCipheriv('aes-256-cbc', Buffer.from(secretKey, 'utf8'), iv);
  let encrypted = cipher.update(text, 'utf8', 'hex');
  encrypted += cipher.final('hex');
  return {
    iv: iv.toString('hex'),
    encryptedData: encrypted
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
      count: channelsData.length,
      iv: encryptedPayload.iv,
      data: encryptedPayload.encryptedData
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
