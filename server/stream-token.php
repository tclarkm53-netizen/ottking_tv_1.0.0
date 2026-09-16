<?php
require_once __DIR__ . '/security.php';

// Accept encrypted POST or standard JSON request
$input = get_request_payload();

if (!$input) {
    send_secure_response(["status" => "error", "message" => "Invalid request payload"], 400);
}

$channelId    = (int)($input['channel_id'] ?? 0);
$streamUrl    = $input['stream_url'] ?? '';
$sessionToken = $input['session_token'] ?? '';
$deviceId     = $input['device_id'] ?? '';
$appClient    = $input['app_client'] ?? '';

// Check header if not present in body
if (empty($appClient)) {
    $headers = getallheaders();
    $appClient = $headers['X-App-Client'] ?? $headers['x-app-client'] ?? '';
}

// 1. Verify App Client authenticity
if (empty($appClient) || !hash_equals(X_APP_CLIENT_TOKEN, $appClient)) {
    send_secure_response([
        "status" => "error",
        "message" => "Unauthorized Client: Invalid or missing X_APP_CLIENT token"
    ], 403);
}

// 2. Check channel permissions in database
$isPremium = false;
$actualStreamUrl = $streamUrl;

if ($channelId > 0) {
    $stmt = $db->prepare("SELECT * FROM channels WHERE id = :id");
    $stmt->execute([':id' => $channelId]);
    $channel = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($channel) {
        $isPremium = (bool)$channel['is_premium'];
        $actualStreamUrl = $channel['stream_url'];
    }
}

// 3. If premium channel, verify active user subscription
if ($isPremium) {
    if (empty($sessionToken)) {
        send_secure_response([
            "status" => "error",
            "message" => "VIP Subscription Required to access this stream"
        ], 403);
    }

    $stmtUser = $db->prepare("SELECT * FROM users WHERE session_token = :s");
    $stmtUser->execute([':s' => $sessionToken]);
    $user = $stmtUser->fetch(PDO::FETCH_ASSOC);

    if (!$user) {
        send_secure_response([
            "status" => "error",
            "message" => "Session expired or invalid. Please re-login."
        ], 401);
    }

    // Check expiry date
    $expiryTime = strtotime($user['expiry_date']);
    if ($expiryTime !== false && $expiryTime < time()) {
        send_secure_response([
            "status" => "error",
            "message" => "Subscription expired on " . $user['expiry_date']
        ], 403);
    }
}

// 4. Generate time-limited signed stream authorization token
$tokenData = generate_stream_token($channelId, $deviceId, $sessionToken, 14400); // 4 hours TTL
$edgeCookie = send_edge_cookie_header($deviceId, $sessionToken);

$cookieJsonPayload = [
    "edge_cookie"   => $edgeCookie,
    "cookie"        => $edgeCookie,
    "channel_id"    => $channelId,
    "device_id"     => $deviceId,
    "session_token" => $sessionToken,
    "expires_at"    => $tokenData['expires_at']
];

send_secure_response([
    "status"                => "success",
    "message"               => "Stream token issued and verified successfully",
    "channel_id"            => $channelId,
    "stream_token"          => $tokenData['stream_token'],
    "expires_at"            => $tokenData['expires_at'],
    "app_client"            => $tokenData['app_client'],
    "authorization_header"  => $tokenData['auth_header'],
    "edge_cookie"           => $edgeCookie,
    "cookie"                => $edgeCookie,
    "cookie_json"           => $cookieJsonPayload,
    "authorized_stream_url" => $actualStreamUrl
]);
