<?php
require_once __DIR__ . '/security.php';

$headers = getallheaders();
$sessionToken = $_GET['session_token'] ?? $headers['X-Session-Token'] ?? $headers['Session-Token'] ?? '';
$deviceId = $headers['X-Device-Id'] ?? $headers['device_id'] ?? $headers['Device-Id'] ?? $_GET['device_id'] ?? '';
$appId = $headers['X-App-Id'] ?? $headers['app_id'] ?? $headers['App-Id'] ?? $_GET['app_id'] ?? 'com.ottking.devcode';
$isSubscriber = false;

if (!empty($sessionToken)) {
    $stmt = $db->prepare("SELECT id FROM users WHERE session_token = :s");
    $stmt->execute([':s' => $sessionToken]);
    if ($stmt->fetch()) {
        $isSubscriber = true;
    }
}

if ($isSubscriber) {
    // Return all channels including VIP premium
    $stmt = $db->query("SELECT * FROM channels ORDER BY id ASC");
} else {
    // Return only non-premium free channels
    $stmt = $db->query("SELECT * FROM channels WHERE is_premium = 0 ORDER BY id ASC");
}

$channels = $stmt->fetchAll(PDO::FETCH_ASSOC);

$edgeCookie = send_edge_cookie_header($deviceId, $sessionToken, $appId);

send_secure_response([
    "status" => "success",
    "timestamp" => time(),
    "is_vip_subscriber" => $isSubscriber,
    "device_id" => $deviceId,
    "app_id" => $appId,
    "edge_cookie" => $edgeCookie,
    "channels" => $channels
]);
