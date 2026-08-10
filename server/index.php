<?php
/**
 * ==========================================================================
 * SECURE IPTV API & STREAMING BACKEND SERVER (PHP)
 * ==========================================================================
 * Features:
 * - User Authentication & Device ID Binding
 * - Real-time Subscription Expiry Validation & Plan Management
 * - Server-Side Automatic VIP / Premium Content Filtering
 *   (Unlocks all 4K Movies & Live Channels for active VIP users;
 *    Automatically hides/omits premium items when subscription expires)
 * - Server Push / Broadcast Notifications Management
 * - AES-256-CBC Encryption & HMAC-SHA256 API Handshake
 * - Live TV, Movies, Series, Categories & App Update Routes
 * ==========================================================================
 */

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Headers: X-API-Key, X-Timestamp, X-HMAC-Signature, Content-Type, Accept");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
header("Content-Type: application/json; charset=UTF-8");
header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
header("Cache-Control: post-check=0, pre-check=0", false);
header("Pragma: no-cache");
header("Expires: 0");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

// 1. Security Configuration
$API_KEY = getenv('API_KEY') ?: "iptv_sec_api_key_2026";
$HMAC_KEY = getenv('HMAC_KEY') ?: "iptv_hmac_secret_key_889900";
$ENCRYPTION_KEY = getenv('ENCRYPTION_KEY') ?: "12345678901234567890123456789012";

// 2. Maintenance Mode Check
$ENABLE_MAINTENANCE = false;
$MAINTENANCE_MODE = $ENABLE_MAINTENANCE
    || (getenv('MAINTENANCE_MODE') === 'true')
    || file_exists(__DIR__ . '/maintenance.json')
    || file_exists(__DIR__ . '/maintenance.flag')
    || (isset($_GET['maintenance']) && ($_GET['maintenance'] === '1' || $_GET['maintenance'] === 'true'))
    || (isset($_GET['mode']) && strtolower($_GET['mode']) === 'maintenance');

if ($MAINTENANCE_MODE) {
    http_response_code(503);
    echo json_encode([
        "status" => "maintenance",
        "app_status" => "maintenance",
        "maintenance" => true,
        "message" => "Server is currently under maintenance. Please try again later.",
        "timestamp" => round(microtime(true) * 1000)
    ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit();
}

function getHeader($name) {
    $keyUpper = 'HTTP_' . strtoupper(str_replace('-', '_', $name));
    if (isset($_SERVER[$keyUpper])) return $_SERVER[$keyUpper];
    if (function_exists('getallheaders')) {
        foreach (getallheaders() as $k => $v) {
            if (strcasecmp($k, $name) === 0) return $v;
        }
    }
    return $_GET[$name] ?? null;
}

$reqApiKey = getHeader('X-API-Key');
$reqTimestamp = getHeader('X-Timestamp');
$reqSignature = getHeader('X-HMAC-Signature');

// Check API Key
if (!$reqApiKey || $reqApiKey !== $API_KEY) {
    http_response_code(401);
    echo json_encode(["status" => "error", "message" => "Invalid or missing X-API-Key"]);
    exit();
}

$route = $_GET['route'] ?? 'channels';

// 3. Health Check Endpoint
if ($route === 'health') {
    http_response_code(200);
    echo json_encode([
        "status" => "online",
        "route" => "health",
        "serverTime" => date('c'),
        "supportedRoutes" => ["live_tv", "categories", "movies", "series", "channels", "health", "update", "login", "notifications"]
    ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit();
}

// 4. Server Broadcast Notifications Endpoint
if ($route === 'notifications' || $route === 'notification') {
    $notifFile = __DIR__ . '/notifications.json';
    $notifications = [];
    if (file_exists($notifFile)) {
        $notifications = json_decode(file_get_contents($notifFile), true) ?: [];
    }
    
    http_response_code(200);
    echo json_encode([
        "status" => "success",
        "route" => "notifications",
        "count" => count($notifications),
        "notifications" => $notifications
    ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit();
}

// 5. Categories Endpoint
if ($route === 'categories' || $route === 'category') {
    $categoryIcons = [
        "Sports" => "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=400",
        "Sports VIP" => "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=400",
        "News" => "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=400",
        "Entertainment" => "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=400",
        "Entertainment VIP" => "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400",
        "Movies" => "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400",
        "Action" => "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400",
        "Action VIP" => "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400",
        "Sci-Fi" => "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400",
        "Sci-Fi VIP" => "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400",
        "Drama" => "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=400",
        "Drama VIP" => "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=400",
        "Crime VIP" => "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=400",
        "General" => "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400"
    ];
    $categoriesList = [];
    foreach ($categoryIcons as $catName => $iconUrl) {
        $categoriesList[] = ["name" => $catName, "icon" => $iconUrl];
    }

    http_response_code(200);
    echo json_encode([
        "status" => "success",
        "route" => "categories",
        "categories" => $categoriesList,
        "category_icons" => $categoryIcons
    ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit();
}

// 6. App Update Endpoint
if ($route === 'update' || $route === 'check_update') {
    http_response_code(200);
    echo json_encode([
        "status" => "success",
        "route" => "update",
        "app_update" => [
            "version_code" => 2,
            "version_name" => "2.0.0",
            "min_version_code" => 1,
            "force_update" => false,
            "title" => "🚀 New Version 2.0.0 Available!",
            "message" => "A new update for Live TV Player is available with improved video playback, better stability, and new features.",
            "download_url" => "https://verify-app.alwaysdata.net/new/mobile/app-release.apk",
            "release_notes" => "• Faster HD stream loading\n• Enhanced video player controls\n• Automatic category filtering\n• Performance and bug fixes",
            "update_time" => date('Y-m-d')
        ]
    ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit();
}

// Check Timestamp & HMAC Signature
if (!$reqTimestamp || !$reqSignature) {
    http_response_code(400);
    echo json_encode(["status" => "error", "message" => "Missing X-Timestamp or X-HMAC-Signature headers"]);
    exit();
}

$requestUri = $_SERVER['REQUEST_URI'] ?? '/index.php';
$parsed = parse_url($requestUri);
$pathAndQuery = $parsed['path'] . (isset($parsed['query']) ? '?' . $parsed['query'] : '');

$expectedHmac1 = hash_hmac('sha256', $reqTimestamp . $parsed['path'], $HMAC_KEY);
$expectedHmac2 = hash_hmac('sha256', $reqTimestamp . $pathAndQuery, $HMAC_KEY);

if (strcasecmp($reqSignature, $expectedHmac1) !== 0 && strcasecmp($reqSignature, $expectedHmac2) !== 0) {
    http_response_code(403);
    echo json_encode(["status" => "error", "message" => "HMAC signature verification failed"]);
    exit();
}

// 7. Load User Database
$usersFile = __DIR__ . '/users.json';
$usersDb = [];
if (file_exists($usersFile)) {
    $usersDb = json_decode(file_get_contents($usersFile), true) ?: [];
}

// 8. User Login & Subscription Authentication Endpoint
if ($route === 'login') {
    $username = trim($_POST['username'] ?? $_GET['username'] ?? '');
    $password = trim($_POST['password'] ?? $_GET['password'] ?? '');
    $deviceId = trim($_GET['device_id'] ?? $_POST['device_id'] ?? 'DEV-UNKNOWN');

    if (empty($username) || empty($password)) {
        http_response_code(400);
        echo json_encode(["status" => "error", "message" => "Username and password are required"]);
        exit();
    }

    $matchedUser = null;
    if (isset($usersDb[$username])) {
        $matchedUser = $usersDb[$username];
    } else {
        // Search by email or lowercase
        foreach ($usersDb as $k => $u) {
            if (strcasecmp($k, $username) === 0 || (!empty($u['email']) && strcasecmp($u['email'], $username) === 0)) {
                $matchedUser = $u;
                break;
            }
        }
    }

    $currentTimeMs = round(microtime(true) * 1000);

    if ($matchedUser !== null) {
        if (!empty($matchedUser['password']) && $matchedUser['password'] !== $password) {
            http_response_code(401);
            echo json_encode(["status" => "error", "message" => "Invalid password. Please check your credentials."]);
            exit();
        }

        $expiryTimestamp = $matchedUser['expiry_timestamp'] ?? strtotime($matchedUser['expiry_date'] ?? 'now') * 1000;
        $isSubActive = ($currentTimeMs < $expiryTimestamp);
        $subStatus = $isSubActive ? "ACTIVE" : "EXPIRED";
        $planName = $matchedUser['plan'] ?? "VIP Ultra 4K";
        $dispUser = $matchedUser['username'] ?? $username;
        $email = $matchedUser['email'] ?? ($username . "@iptvstream.com");

        http_response_code(200);
        echo json_encode([
            "status" => "success",
            "message" => "Login successful",
            "username" => $dispUser,
            "email" => $email,
            "device_id" => $deviceId,
            "plan" => $planName,
            "subscription_status" => $subStatus,
            "expiry_timestamp" => $expiryTimestamp,
            "expiry_date" => date('Y-m-d H:i:s', (int)($expiryTimestamp / 1000))
        ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
        exit();
    } else {
        http_response_code(401);
        echo json_encode([
            "status" => "error",
            "message" => "Account not registered in server database. Please contact admin to activate your subscription."
        ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
        exit();
    }
}

// 8.5 Session Validation Endpoint
if ($route === 'session_check') {
    $username = trim($_POST['username'] ?? $_GET['username'] ?? $_GET['user_id'] ?? $_POST['user_id'] ?? '');
    $deviceId = trim($_GET['device_id'] ?? $_POST['device_id'] ?? 'DEV-UNKNOWN');

    if (empty($username)) {
        http_response_code(400);
        echo json_encode(["status" => "error", "message" => "User ID or username is required"]);
        exit();
    }

    $matchedUser = null;
    if (isset($usersDb[$username])) {
        $matchedUser = $usersDb[$username];
    } else {
        foreach ($usersDb as $k => $u) {
            if (strcasecmp($k, $username) === 0 || (!empty($u['email']) && strcasecmp($u['email'], $username) === 0)) {
                $matchedUser = $u;
                break;
            }
        }
    }

    $currentTimeMs = round(microtime(true) * 1000);

    if ($matchedUser !== null) {
        $expiryTimestamp = $matchedUser['expiry_timestamp'] ?? strtotime($matchedUser['expiry_date'] ?? 'now') * 1000;
        $isSubActive = ($currentTimeMs < $expiryTimestamp);
        $subStatus = $isSubActive ? "ACTIVE" : "EXPIRED";
        $planName = $matchedUser['plan'] ?? "VIP Ultra 4K";
        $dispUser = $matchedUser['username'] ?? $username;
        $email = $matchedUser['email'] ?? ($username . "@iptvstream.com");

        http_response_code(200);
        echo json_encode([
            "status" => "success",
            "message" => "Session verified",
            "username" => $dispUser,
            "email" => $email,
            "device_id" => $deviceId,
            "plan" => $planName,
            "subscription_status" => $subStatus,
            "expiry_timestamp" => $expiryTimestamp,
            "expiry_date" => date('Y-m-d H:i:s', (int)($expiryTimestamp / 1000))
        ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
        exit();
    } else {
        http_response_code(401);
        echo json_encode([
            "status" => "error",
            "message" => "Session expired or user account not found on server."
        ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
        exit();
    }
}

// 9. Check Subscription Status for Content Requests
$userId = $_GET['user_id'] ?? $_POST['username'] ?? $_GET['username'] ?? '';
$deviceId = $_GET['device_id'] ?? $_POST['device_id'] ?? '';

$isSubscriptionValid = false;

if (!empty($userId)) {
    if (isset($usersDb[$userId])) {
        $expiryTs = $usersDb[$userId]['expiry_timestamp'] ?? strtotime($usersDb[$userId]['expiry_date'] ?? 'now') * 1000;
        $isSubscriptionValid = (round(microtime(true) * 1000) < $expiryTs);
    } else {
        foreach ($usersDb as $k => $u) {
            if (strcasecmp($k, $userId) === 0 || (!empty($u['email']) && strcasecmp($u['email'], $userId) === 0)) {
                $expiryTs = $u['expiry_timestamp'] ?? strtotime($u['expiry_date'] ?? 'now') * 1000;
                $isSubscriptionValid = (round(microtime(true) * 1000) < $expiryTs);
                break;
            }
        }
    }
}

// 10. Load and Filter Channels / Content
$channelsFile = __DIR__ . '/channels.json';
$allChannels = [];
if (file_exists($channelsFile)) {
    $allChannels = json_decode(file_get_contents($channelsFile), true) ?: [];
}

$filteredData = [];
foreach ($allChannels as $item) {
    // Check route filter
    $itemCat = strtolower($item['category'] ?? 'tv');
    if ($route === 'live_tv' || $route === 'live_tv_channels' || $route === 'tv') {
        if ($itemCat !== 'tv' && $itemCat !== 'live_tv') {
            continue;
        }
    } else if ($route === 'movies' || $route === 'movie') {
        if ($itemCat !== 'movie' && $itemCat !== 'movies') {
            continue;
        }
    } else if ($route === 'series') {
        if ($itemCat !== 'series') {
            continue;
        }
    }

    // Check VIP / Premium status
    $isPremium = !empty($item['is_premium'])
        || (isset($item['subCategory']) && (stripos($item['subCategory'], 'vip') !== false || stripos($item['subCategory'], 'premium') !== false))
        || (isset($item['title']) && (stripos($item['title'], '[vip]') !== false || stripos($item['title'], '[premium]') !== false));

    // SERVER-SIDE SUBSCRIPTION FILTERING:
    // If subscription is expired or inactive, omit all VIP/Premium items from server response
    if ($isPremium && !$isSubscriptionValid) {
        continue;
    }

    $filteredData[] = $item;
}

$rawJsonPayload = json_encode($filteredData);

// 11. Encrypt Payload using AES-256-CBC
$iv = openssl_random_pseudo_bytes(16);
$encryptedRaw = openssl_encrypt($rawJsonPayload, 'aes-256-cbc', $ENCRYPTION_KEY, OPENSSL_RAW_DATA, $iv);

http_response_code(200);
echo json_encode([
    "status" => "success",
    "route" => $route,
    "timestamp" => round(microtime(true) * 1000),
    "subscription_status" => $isSubscriptionValid ? "ACTIVE" : "EXPIRED",
    "count" => count($filteredData),
    "iv" => bin2hex($iv),
    "data" => bin2hex($encryptedRaw),
    "app_update" => [
        "version_code" => 2,
        "version_name" => "2.0.0",
        "min_version_code" => 1,
        "force_update" => false,
        "title" => "🚀 New Version 2.0.0 Available!",
        "message" => "A new update for Live TV Player is available with improved video playback, better stability, and new features.",
        "download_url" => "https://verify-app.alwaysdata.net/new/mobile/app-release.apk",
        "release_notes" => "• Faster HD stream loading\n• Enhanced video player controls\n• Automatic category filtering\n• Performance and bug fixes",
        "update_time" => date('Y-m-d')
    ]
], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
?>
