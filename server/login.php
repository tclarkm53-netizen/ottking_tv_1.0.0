<?php
require_once __DIR__ . '/security.php';

$input = get_request_payload();

if (!$input) {
    send_secure_response(["status" => "error", "message" => "Invalid JSON payload"], 400);
}

$username = $input['username'] ?? '';
$password = $input['password'] ?? '';
$deviceId = $input['device_id'] ?? '';

if (empty($username) || empty($password)) {
    send_secure_response(["status" => "error", "message" => "Username and password required"], 400);
}

$stmt = $db->prepare("SELECT * FROM users WHERE username = :u AND password = :p");
$stmt->execute([':u' => $username, ':p' => $password]);
$user = $stmt->fetch(PDO::FETCH_ASSOC);

if (!$user) {
    send_secure_response(["status" => "error", "message" => "Invalid username or password"], 401);
}

// Device Binding Limit Check (Max 1 device)
if (!empty($user['bound_device_id']) && $user['bound_device_id'] !== $deviceId) {
    send_secure_response([
        "status" => "error",
        "message" => "Device Limit Exceeded! Account bound to device: " . substr($user['bound_device_id'], 0, 8) . "..."
    ], 403);
}

// Generate session token and bind device
$sessionToken = bin2hex(random_bytes(16));
$updateStmt = $db->prepare("UPDATE users SET bound_device_id = :d, session_token = :s WHERE id = :id");
$updateStmt->execute([':d' => $deviceId, ':s' => $sessionToken, ':id' => $user['id']]);

send_secure_response([
    "status" => "success",
    "message" => "Login successful",
    "session_token" => $sessionToken,
    "user_info" => [
        "username" => $user['username'],
        "package" => $user['package'],
        "expiry_date" => $user['expiry_date'],
        "device_id" => $deviceId
    ]
]);
