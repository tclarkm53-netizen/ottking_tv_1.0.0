<?php
require_once __DIR__ . '/security.php';

$input = get_request_payload();
$currentVersionCode = 1;
$currentVersionName = "1.0.0";

if ($input && isset($input['version_code'])) {
    $currentVersionCode = intval($input['version_code']);
} else if (isset($_GET['version_code'])) {
    $currentVersionCode = intval($_GET['version_code']);
} else if (isset($_GET['build_version'])) {
    $currentVersionCode = intval($_GET['build_version']);
}

if ($input && isset($input['version_name'])) {
    $currentVersionName = trim($input['version_name']);
} else if (isset($_GET['version_name'])) {
    $currentVersionName = trim($_GET['version_name']);
} else if (isset($_GET['app_version'])) {
    $currentVersionName = trim($_GET['app_version']);
}

$latestVersionCode = 2; // Server latest version code
$latestVersionName = "1.1.0"; // Server latest version name

$hasUpdate = ($currentVersionCode < $latestVersionCode);

// Construct download URL dynamically based on current server host
$protocol = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off' || (isset($_SERVER['SERVER_PORT']) && $_SERVER['SERVER_PORT'] == 443)) ? "https://" : "http://";
$host = $_SERVER['HTTP_HOST'] ?? 'localhost';
$scriptDir = dirname($_SERVER['SCRIPT_NAME'] ?? '/server');
$baseUrl = rtrim($protocol . $host . $scriptDir, '/');
$updateUrl = $baseUrl . "/app-release.apk";

// Changelog fetched directly from server configuration
$changelogText = "• Live TV server-enforced GCM AES-256 encryption & HMAC SHA-256 signing\n"
               . "• Direct in-app background APK downloader & auto installer\n"
               . "• Enhanced player playback buffering for HLS and DASH streams\n"
               . "• Full Android TV remote DPAD navigation support";

send_secure_response([
    "status" => "success",
    "has_update" => $hasUpdate,
    "version_code" => $latestVersionCode,
    "version_name" => $latestVersionName,
    "current_version_code" => $currentVersionCode,
    "current_version_name" => $currentVersionName,
    "message" => $hasUpdate ? "New update available!" : "App is up to date.",
    "changelog" => $changelogText,
    "update_url" => $updateUrl
]);



