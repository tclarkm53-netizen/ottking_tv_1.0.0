<?php
require_once __DIR__ . '/security.php';

$sessionToken = $_GET['session_token'] ?? '';
$deviceId = $_GET['device_id'] ?? '';

$notifications = [
    [
        "id" => "notif_server_welcome",
        "title" => "Welcome to OTT KING Live TV",
        "message" => "Enjoy seamless 4K & HD live TV streaming with real-time channel sync and hardware-accelerated playback.",
        "timestamp" => "Just Now",
        "type" => "SYSTEM",
        "action_text" => "Explore",
        "is_read" => false
    ],
    [
        "id" => "notif_server_stream_token",
        "title" => "Stream Token Encryption Enabled",
        "message" => "All live channels are secured with dynamic AES-256-GCM and HMAC-SHA256 device-bound tokens.",
        "timestamp" => "Today",
        "type" => "CHANNEL",
        "action_text" => "Watch Live",
        "is_read" => false
    ]
];

send_secure_response([
    "status" => "success",
    "timestamp" => time(),
    "notifications" => $notifications
]);
