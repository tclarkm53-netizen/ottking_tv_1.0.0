<?php
require_once __DIR__ . '/security.php';

$input = get_request_payload() ?? [];
$headers = getallheaders();

$sessionToken = $input['session_token'] ?? $headers['X-Session-Token'] ?? $headers['Session-Token'] ?? $_GET['session_token'] ?? '';
$deviceId = $input['device_id'] ?? $headers['X-Device-Id'] ?? $headers['device_id'] ?? $headers['Device-Id'] ?? $_GET['device_id'] ?? '';
$appId = $input['app_id'] ?? $headers['X-App-Id'] ?? $headers['app_id'] ?? $headers['App-Id'] ?? $_GET['app_id'] ?? 'com.ottking.devcode';

if (empty($sessionToken)) {
    $edgeCookie = send_edge_cookie_header($deviceId, "", $appId);
    send_secure_response([
        "status" => "success",
        "valid" => false,
        "is_guest" => true,
        "device_id" => $deviceId,
        "app_id" => $appId,
        "edge_cookie" => $edgeCookie,
        "message" => "Guest session active"
    ]);
}

$stmt = $db->prepare("SELECT * FROM users WHERE session_token = :s");
$stmt->execute([':s' => $sessionToken]);
$user = $stmt->fetch(PDO::FETCH_ASSOC);

if (!$user) {
    $edgeCookie = send_edge_cookie_header($deviceId, "", $appId);
    send_secure_response([
        "status" => "error",
        "valid" => false,
        "device_id" => $deviceId,
        "app_id" => $appId,
        "edge_cookie" => $edgeCookie,
        "message" => "Invalid session token"
    ], 401);
}

if (!empty($user['bound_device_id']) && $user['bound_device_id'] !== $deviceId) {
    $edgeCookie = send_edge_cookie_header($deviceId, "", $appId);
    send_secure_response([
        "status" => "error",
        "valid" => false,
        "device_id" => $deviceId,
        "app_id" => $appId,
        "edge_cookie" => $edgeCookie,
        "message" => "Session invalidated on this device"
    ], 403);
}

$edgeCookie = send_edge_cookie_header($deviceId, $sessionToken, $appId);

send_secure_response([
    "status" => "success",
    "valid" => true,
    "device_id" => $deviceId,
    "app_id" => $appId,
    "edge_cookie" => $edgeCookie,
    "user_info" => [
        "username" => $user['username'],
        "package" => $user['package'],
        "expiry_date" => $user['expiry_date']
    ]
]);
