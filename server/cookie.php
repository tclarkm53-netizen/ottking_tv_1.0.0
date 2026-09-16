<?php
require_once __DIR__ . '/security.php';

// Accept encrypted POST, standard JSON request, or GET request
$rawInput = file_get_contents('php://input');
$isEncryptedRequest = false;
$input = [];

if (!empty($rawInput)) {
    $decodedRaw = json_decode($rawInput, true);
    if (is_array($decodedRaw) && isset($decodedRaw['encrypted_payload'])) {
        $isEncryptedRequest = true;
        try {
            $input = get_request_payload() ?? [];
        } catch (Exception $e) {
            send_secure_response(["status" => "error", "message" => "Payload decryption failed"], 400);
        }
    } else if (is_array($decodedRaw)) {
        $input = $decodedRaw;
    }
}

if (empty($input)) {
    $input = $_GET ?? [];
}

$headers = getallheaders();

// Extract last cookie from payload, JSON headers, or query parameters
$lastCookie = '';
if (!empty($input['last_cookie'])) {
    $lastCookie = clean_cookie_token($input['last_cookie']);
} else if (!empty($input['edge_cookie'])) {
    $lastCookie = clean_cookie_token($input['edge_cookie']);
} else if (!empty($input['cookie'])) {
    $lastCookie = clean_cookie_token($input['cookie']);
} else if (isset($input['cookie_json']) && is_array($input['cookie_json'])) {
    $lastCookie = clean_cookie_token($input['cookie_json']['last_cookie'] ?? $input['cookie_json']['edge_cookie'] ?? $input['cookie_json']['cookie'] ?? '');
}

if (empty($lastCookie)) {
    $lastCookie = extract_edge_cookie_from_request();
}

$channelId    = (int)($input['channel_id'] ?? $_GET['channel_id'] ?? 0);
$streamUrl    = $input['stream_url'] ?? $_GET['stream_url'] ?? '';
$sessionToken = $input['session_token'] ?? $_GET['session_token'] ?? $headers['X-Session-Token'] ?? $headers['Session-Token'] ?? '';
$deviceId     = $input['device_id'] ?? $_GET['device_id'] ?? $headers['X-Device-Id'] ?? $headers['device_id'] ?? '';
if (empty($deviceId)) {
    $deviceId = 'dev_' . substr(md5($_SERVER['REMOTE_ADDR'] ?? '127.0.0.1'), 0, 10);
}

$ttlSeconds = 86400; // 24 hours
$expiresAtMs = (time() + $ttlSeconds) * 1000;

// CASE 1: App Launch / Startup (কোনো পেলোড লাগবে না - No payload needed)
// When app opens, it fetches the initial bootstrap cookie directly without sending any payload
if (empty($lastCookie)) {
    $initialCookie = generate_edge_cookie($deviceId, $sessionToken, $ttlSeconds);

    $cookieJsonPayload = [
        "edge_cookie"   => $initialCookie,
        "cookie"        => $initialCookie,
        "type"          => "initial_launch",
        "channel_id"    => $channelId,
        "device_id"     => $deviceId,
        "session_token" => $sessionToken,
        "issued_at"     => time(),
        "expires_at"    => $expiresAtMs
    ];

    header("Edge-Cookie: " . $initialCookie);
    header("X-Edge-Cookie: " . $initialCookie);
    header("Set-Cookie: edge_cookie=" . $initialCookie . "; Path=/; HttpOnly; SameSite=Lax");
    header("X-Cookie-Json: " . json_encode($cookieJsonPayload, JSON_UNESCAPED_SLASHES));

    $responseData = [
        "status"               => "success",
        "mode"                 => "initial_launch",
        "message"              => "Initial app launch cookie issued (no payload required)",
        "cookie"               => $initialCookie,
        "edge_cookie"          => $initialCookie,
        "token"                => $initialCookie,
        "channel_id"           => $channelId,
        "expires_at"           => $expiresAtMs,
        "cookie_json"          => $cookieJsonPayload,
        "last_cookie_verified" => false
    ];

    if ($isEncryptedRequest) {
        send_secure_response($responseData, 200);
    } else {
        header("Content-Type: application/json; charset=utf-8");
        header("X-Signature: " . sign_payload(json_encode($responseData, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)));
        echo json_encode($responseData, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
        exit();
    }
}

// CASE 2: Runtime verification and new cookie renewal (রান টাইমে লাষ্ট কুকি যাচাই করে নিউ কুকি ইস্যু)
$verification = verify_edge_cookie($lastCookie);
$lastCookieVerified = $verification['valid'];

if ($lastCookieVerified) {
    if (!empty($verification['device_id'])) {
        $deviceId = $verification['device_id'];
    }
    if (!empty($verification['session_token'])) {
        $sessionToken = $verification['session_token'];
    }
}

// Generate brand new cookie for runtime
$newCookie = generate_edge_cookie($deviceId, $sessionToken, $ttlSeconds);

$cookieJsonPayload = [
    "edge_cookie"   => $newCookie,
    "cookie"        => $newCookie,
    "last_cookie"   => $lastCookie,
    "type"          => "runtime_renewal",
    "channel_id"    => $channelId,
    "device_id"     => $deviceId,
    "session_token" => $sessionToken,
    "issued_at"     => time(),
    "expires_at"    => $expiresAtMs
];

// Set response headers
header("Edge-Cookie: " . $newCookie);
header("X-Edge-Cookie: " . $newCookie);
header("Set-Cookie: edge_cookie=" . $newCookie . "; Path=/; HttpOnly; SameSite=Lax");
header("X-Cookie-Json: " . json_encode($cookieJsonPayload, JSON_UNESCAPED_SLASHES));
header("X-Edge-Cookie-Json: " . json_encode(["edge_cookie" => $newCookie, "cookie" => $newCookie], JSON_UNESCAPED_SLASHES));

$responseData = [
    "status"               => "success",
    "mode"                 => "runtime_renewal",
    "message"              => $lastCookieVerified ? "Last cookie verified successfully. New runtime cookie issued." : "Runtime cookie renewed.",
    "last_cookie_verified" => $lastCookieVerified,
    "last_cookie"          => $lastCookie,
    "new_cookie"           => $newCookie,
    "cookie"               => $newCookie,
    "edge_cookie"          => $newCookie,
    "token"                => $newCookie,
    "channel_id"           => $channelId,
    "expires_at"           => $expiresAtMs,
    "cookie_json"          => $cookieJsonPayload
];

if ($isEncryptedRequest) {
    send_secure_response($responseData, 200);
} else {
    header("Content-Type: application/json; charset=utf-8");
    header("X-Signature: " . sign_payload(json_encode($responseData, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)));
    echo json_encode($responseData, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit();
}

