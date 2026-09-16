<?php
require_once __DIR__ . '/security.php';

$input = get_request_payload();

$sessionToken = $input['session_token'] ?? '';
$deviceId = $input['device_id'] ?? '';
$username = $input['username'] ?? '';

if (empty($sessionToken) && empty($deviceId) && empty($username)) {
    send_secure_response(["status" => "error", "message" => "Session token, device ID, or username required"], 400);
}

$unbound = false;

if (!empty($sessionToken)) {
    $stmt = $db->prepare("UPDATE users SET bound_device_id = NULL, session_token = NULL WHERE session_token = :s");
    $stmt->execute([':s' => $sessionToken]);
    if ($stmt->rowCount() > 0) {
        $unbound = true;
    }
}

if (!$unbound && !empty($username)) {
    $stmt = $db->prepare("UPDATE users SET bound_device_id = NULL, session_token = NULL WHERE username = :u");
    $stmt->execute([':u' => $username]);
    if ($stmt->rowCount() > 0) {
        $unbound = true;
    }
}

if (!$unbound && !empty($deviceId)) {
    $stmt = $db->prepare("UPDATE users SET bound_device_id = NULL, session_token = NULL WHERE bound_device_id = :d");
    $stmt->execute([':d' => $deviceId]);
}

send_secure_response([
    "status" => "success",
    "message" => "Logged out successfully from server. Device unbound."
]);
