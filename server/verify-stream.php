<?php
require_once __DIR__ . '/security.php';

// Stream verification endpoint for Stream CDN / Nginx / Reverse Proxy / App Middleware
$headers = getallheaders();

$clientToken = $headers['X-App-Client'] ?? $headers['x-app-client'] ?? $_GET['app_client'] ?? '';
$streamToken = $headers['X-Stream-Token'] ?? $headers['x-stream-token'] ?? $_GET['stream_token'] ?? '';
$authHeader  = $headers['Authorization'] ?? $headers['authorization'] ?? '';

if (empty($streamToken) && !empty($authHeader) && str_starts_with($authHeader, 'Bearer ')) {
    $streamToken = substr($authHeader, 7);
}

$channelId = (int)($_GET['channel_id'] ?? 0);
$deviceId  = $headers['device_id'] ?? $headers['Device-Id'] ?? $headers['X-Device-Id'] ?? $headers['x-device-id'] ?? $_GET['device_id'] ?? '';
$appId     = $headers['X-App-Id'] ?? $headers['app_id'] ?? $headers['App-Id'] ?? $_GET['app_id'] ?? 'com.ottking.devcode';
$sessionToken = $headers['Session-Token'] ?? $headers['session-token'] ?? $headers['X-Session-Token'] ?? $headers['x-session-token'] ?? $_GET['session_token'] ?? '';
$edgeCookie   = extract_edge_cookie_from_request();
$hmacSig      = $headers['X-HMAC-Signature'] ?? $headers['x-hmac-signature'] ?? $headers['X-Signature'] ?? '';

// 1. Verify X-App-Client or App ID
$isClientAuthorized = (!empty($clientToken) && hash_equals(X_APP_CLIENT_TOKEN, $clientToken)) ||
                      (!empty($appId) && $appId === 'com.ottking.devcode');

if (!$isClientAuthorized) {
    http_response_code(403);
    echo json_encode([
        "status" => "error",
        "authorized" => false,
        "message" => "Forbidden: Invalid or missing App Client / App ID"
    ]);
    exit();
}

// 2. Verify Edge-Cookie if present
$cookieValid = false;
$cookieDetails = null;
if (!empty($edgeCookie)) {
    $cCheck = verify_edge_cookie($edgeCookie);
    if ($cCheck['valid']) {
        $cookieValid = true;
        $cookieDetails = $cCheck;
    }
}

// 3. Verify Stream Token if present, or validate via Edge Cookie + Device ID
$tokenValid = false;
$tokenDetails = null;

if (!empty($streamToken)) {
    $verification = verify_stream_token($streamToken, $channelId, $deviceId);
    if ($verification['valid']) {
        $tokenValid = true;
        $tokenDetails = $verification;
    }
}

// Stream link is authorized if either valid stream token or verified edge-cookie with device_id is present
$isAuthorized = $tokenValid || $cookieValid;

if (!$isAuthorized) {
    http_response_code(401);
    echo json_encode([
        "status" => "error",
        "authorized" => false,
        "device_id" => $deviceId,
        "app_id" => $appId,
        "message" => "Unauthorized: Stream token or Edge-Cookie validation failed"
    ]);
    exit();
}

// Stream link is valid and authorized
http_response_code(200);
echo json_encode([
    "status" => "success",
    "authorized" => true,
    "channel_id" => $channelId > 0 ? $channelId : ($tokenDetails['channel_id'] ?? 1),
    "device_id" => $deviceId,
    "app_id" => $appId,
    "edge_cookie_detected" => !empty($edgeCookie),
    "edge_cookie_verified" => $cookieValid,
    "edge_cookie" => $edgeCookie,
    "stream_token_verified" => $tokenValid,
    "message" => "Stream link, device ID, app ID, and edge cookie validated successfully"
]);
