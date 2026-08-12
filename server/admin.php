<?php
/**
 * ==========================================================================
 * IPTV SERVER WEB ADMIN PANEL (PHP)
 * ==========================================================================
 * Server Owner Control Dashboard:
 * 1. Broadcast / Send Push Notifications to All App Users
 * 2. Manage Live TV Channels & Movies (Add, Edit, Delete, VIP Flag)
 * 3. User & Subscription Management (Extend Expiry, Activate/Deactivate VIP)
 * 4. Server Maintenance Mode Control & Health Monitor
 * ==========================================================================
 */

session_start();

$ADMIN_USER = "admin";
$ADMIN_PASS = "admin123";

// Simple Auth
if (isset($_POST['action']) && $_POST['action'] === 'login') {
    if ($_POST['user'] === $ADMIN_USER && $_POST['pass'] === $ADMIN_PASS) {
        $_SESSION['admin_logged'] = true;
    } else {
        $login_error = "Invalid admin username or password!";
    }
}

if (isset($_GET['logout'])) {
    session_destroy();
    header("Location: admin.php");
    exit();
}

$isLogged = !empty($_SESSION['admin_logged']);

$channelsFile = __DIR__ . '/channels.json';
$notificationsFile = __DIR__ . '/notifications.json';
$usersFile = __DIR__ . '/users.json';
$settingsFile = __DIR__ . '/settings.json';
$maintenanceFile = __DIR__ . '/maintenance.flag';

$channels = file_exists($channelsFile) ? (json_decode(file_get_contents($channelsFile), true) ?: []) : [];
$notifications = file_exists($notificationsFile) ? (json_decode(file_get_contents($notificationsFile), true) ?: []) : [];
$users = file_exists($usersFile) ? (json_decode(file_get_contents($usersFile), true) ?: []) : [];
$settings = [
    "whatsapp_url" => "https://wa.me/8801700000000",
    "telegram_url" => "https://t.me/telegram",
    "app_share_url" => "https://verify-app.alwaysdata.net/download",
    "developer_info" => "Official Stream IPTV Engine v2.0.0\nPowered by Secure Cloud Infrastructure & Real-Time AES-256 Content Delivery System.",
    "version_code" => 2,
    "version_name" => "2.0.0",
    "min_version_code" => 1,
    "force_update" => false,
    "title" => "🚀 New Version 2.0.0 Available!",
    "message" => "A new update for Live TV Player is available with improved video playback, better stability, and new features.",
    "download_url" => "https://verify-app.alwaysdata.net/new/mobile/app-release.apk",
    "release_notes" => "• Faster HD stream loading\n• Enhanced video player controls\n• Automatic category filtering\n• Performance and bug fixes"
];
if (file_exists($settingsFile)) {
    $savedSettings = json_decode(file_get_contents($settingsFile), true);
    if (is_array($savedSettings)) {
        $settings = array_merge($settings, $savedSettings);
    }
}
$isMaintenance = file_exists($maintenanceFile);

$successMsg = "";

// Actions
if ($isLogged && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';

    // 1. Send Notification
    if ($action === 'send_notification') {
        $title = trim($_POST['notif_title'] ?? '');
        $message = trim($_POST['notif_message'] ?? '');
        $icon = trim($_POST['notif_icon'] ?? 'bell');

        if (!empty($title) && !empty($message)) {
            $newNotif = [
                "id" => "notif_" . time() . "_" . rand(100, 999),
                "title" => $title,
                "message" => $message,
                "timestamp" => date("d M, h:i A"),
                "icon" => $icon
            ];
            array_unshift($notifications, $newNotif);
            file_put_contents($notificationsFile, json_encode($notifications, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
            $successMsg = "Notification broadcasted successfully to all connected apps!";
        }
    }

    // 2. Delete Notification
    if ($action === 'delete_notification') {
        $id = $_POST['notif_id'] ?? '';
        $notifications = array_values(array_filter($notifications, function($n) use ($id) {
            return ($n['id'] ?? '') !== $id;
        }));
        file_put_contents($notificationsFile, json_encode($notifications, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
        $successMsg = "Notification deleted.";
    }

    // 3. Add Channel / Movie
    if ($action === 'add_channel') {
        $title = trim($_POST['title'] ?? '');
        $streamUrl = trim($_POST['streamUrl'] ?? '');
        $logoUrl = trim($_POST['logoUrl'] ?? '');
        $category = trim($_POST['category'] ?? 'tv');
        $subCategory = trim($_POST['subCategory'] ?? 'General');
        $quality = trim($_POST['quality'] ?? '1080p HD');
        $is_premium = !empty($_POST['is_premium']);

        if (!empty($title) && !empty($streamUrl)) {
            $newCh = [
                "title" => $title,
                "streamUrl" => $streamUrl,
                "logoUrl" => !empty($logoUrl) ? $logoUrl : "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400",
                "category" => $category,
                "subCategory" => $subCategory,
                "isFavorite" => false,
                "streamType" => strpos($streamUrl, '.m3u8') !== false ? "hls" : (strpos($streamUrl, '.ts') !== false ? "ts" : "mp4"),
                "quality" => $quality,
                "is_premium" => $is_premium
            ];
            $channels[] = $newCh;
            file_put_contents($channelsFile, json_encode($channels, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
            $successMsg = "Channel / Movie added successfully!";
        }
    }

    // 4. Delete Channel
    if ($action === 'delete_channel') {
        $idx = (int)$_POST['channel_index'];
        if (isset($channels[$idx])) {
            array_splice($channels, $idx, 1);
            file_put_contents($channelsFile, json_encode($channels, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
            $successMsg = "Channel removed.";
        }
    }

    // 5. User Management (Add / Extend Subscription)
    if ($action === 'save_user') {
        $userId = trim($_POST['user_key'] ?? '');
        $uName = trim($_POST['username'] ?? '');
        $uEmail = trim($_POST['email'] ?? '');
        $uPass = trim($_POST['password'] ?? '123456');
        $days = (int)($_POST['valid_days'] ?? 30);
        $plan = trim($_POST['plan'] ?? 'VIP 4K Ultra Premium');

        if (!empty($userId)) {
            $expiryTime = time() + ($days * 24 * 3600);
            $users[$userId] = [
                "username" => !empty($uName) ? $uName : $userId,
                "email" => !empty($uEmail) ? $uEmail : $userId . "@iptvstream.com",
                "password" => $uPass,
                "plan" => $plan,
                "subscription_status" => ($days > 0) ? "ACTIVE" : "EXPIRED",
                "expiry_date" => date('Y-m-d H:i:s', $expiryTime),
                "expiry_timestamp" => $expiryTime * 1000
            ];
            file_put_contents($usersFile, json_encode($users, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
            $successMsg = "User subscription updated! Expiry set to $days days from today.";
        }
    }

    // 5.5 Unbind Device Binding for User
    if ($action === 'unbind_device') {
        $uKey = trim($_POST['user_key'] ?? '');
        if (!empty($uKey)) {
            foreach ($users as $k => &$u) {
                if ($k === $uKey || strcasecmp($k, $uKey) === 0 || (!empty($u['email']) && strcasecmp($u['email'], $uKey) === 0)) {
                    $u['bound_device_id'] = "";
                }
            }
            unset($u);
            file_put_contents($usersFile, json_encode($users, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
            $successMsg = "Device binding reset for user '$uKey'. Account can now log in on a new device.";
        }
    }

    // 6. Save App Settings & Support Links
    if ($action === 'save_settings') {
        $settings['whatsapp_url'] = trim($_POST['whatsapp_url'] ?? '');
        $settings['telegram_url'] = trim($_POST['telegram_url'] ?? '');
        $settings['app_share_url'] = trim($_POST['app_share_url'] ?? '');
        $settings['developer_info'] = trim($_POST['developer_info'] ?? '');
        $settings['version_code'] = (int)($_POST['version_code'] ?? 2);
        $settings['version_name'] = trim($_POST['version_name'] ?? '2.0.0');
        $settings['min_version_code'] = (int)($_POST['min_version_code'] ?? 1);
        $settings['force_update'] = !empty($_POST['force_update']);
        $settings['title'] = trim($_POST['title'] ?? '🚀 New Version Available!');
        $settings['message'] = trim($_POST['message'] ?? '');
        $settings['download_url'] = trim($_POST['download_url'] ?? '');
        $settings['release_notes'] = trim($_POST['release_notes'] ?? '');

        file_put_contents($settingsFile, json_encode($settings, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
        $successMsg = "App Settings, Support Links & App Update info updated successfully!";
    }

    // 7. Toggle Maintenance
    if ($action === 'toggle_maintenance') {
        if ($isMaintenance) {
            @unlink($maintenanceFile);
            $isMaintenance = false;
            $successMsg = "Maintenance Mode disabled. Server is ONLINE.";
        } else {
            file_put_contents($maintenanceFile, "MAINTENANCE ACTIVE");
            $isMaintenance = true;
            $successMsg = "Maintenance Mode ENABLED. App requests will see maintenance message.";
        }
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>IPTV Server Control Panel</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #0f172a; color: #f8fafc; padding: 20px; }
        .container { max-width: 1100px; margin: 0 auto; }
        .header { display: flex; justify-content: space-between; align-items: center; padding: 20px; background: #1e293b; border-radius: 12px; margin-bottom: 20px; border: 1px solid #334155; }
        .header h1 { font-size: 22px; color: #38bdf8; display: flex; align-items: center; gap: 10px; }
        .badge { padding: 4px 10px; border-radius: 20px; font-size: 12px; font-weight: bold; }
        .badge-green { background: #10b98120; color: #34d399; border: 1px solid #10b981; }
        .badge-red { background: #ef444420; color: #f87171; border: 1px solid #ef4444; }
        .badge-vip { background: #eab30820; color: #fbbf24; border: 1px solid #eab308; }
        .card { background: #1e293b; border-radius: 12px; padding: 20px; margin-bottom: 20px; border: 1px solid #334155; }
        .card h2 { font-size: 18px; margin-bottom: 15px; color: #e2e8f0; display: flex; align-items: center; gap: 8px; }
        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 15px; margin-bottom: 20px; }
        .stat-card { background: #0f172a; padding: 15px; border-radius: 8px; border: 1px solid #334155; }
        .stat-card .num { font-size: 28px; font-weight: bold; color: #38bdf8; }
        .stat-card .label { font-size: 13px; color: #94a3b8; margin-top: 4px; }
        input, select, textarea, button { width: 100%; padding: 10px 12px; border-radius: 6px; border: 1px solid #475569; background: #0f172a; color: #fff; margin-bottom: 12px; font-size: 14px; }
        input:focus, select:focus, textarea:focus { outline: none; border-color: #38bdf8; }
        button { background: #0284c7; color: #fff; font-weight: bold; cursor: pointer; border: none; transition: 0.2s; }
        button:hover { background: #0369a1; }
        button.btn-danger { background: #dc2626; }
        button.btn-danger:hover { background: #b91c1c; }
        button.btn-warning { background: #d97706; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
        th, td { padding: 10px; text-align: left; border-bottom: 1px solid #334155; }
        th { background: #0f172a; color: #94a3b8; font-weight: 600; }
        .alert { background: #05966920; border: 1px solid #059669; color: #34d399; padding: 12px 16px; border-radius: 8px; margin-bottom: 20px; }
        .login-box { max-width: 400px; margin: 100px auto; background: #1e293b; padding: 30px; border-radius: 12px; border: 1px solid #334155; }
    </style>
</head>
<body>

<div class="container">
<?php if (!$isLogged): ?>
    <div class="login-box">
        <h2 style="margin-bottom: 20px; color: #38bdf8;">🔐 Server Admin Login</h2>
        <?php if (!empty($login_error)): ?>
            <div style="color: #f87171; margin-bottom: 15px; font-size: 13px;"><?= htmlspecialchars($login_error) ?></div>
        <?php endif; ?>
        <form method="POST">
            <input type="hidden" name="action" value="login">
            <label style="font-size: 12px; color: #94a3b8;">Admin Username</label>
            <input type="text" name="user" placeholder="admin" required>
            <label style="font-size: 12px; color: #94a3b8;">Password</label>
            <input type="password" name="pass" placeholder="admin123" required>
            <button type="submit" style="margin-top: 10px;">Sign In to Dashboard</button>
        </form>
    </div>
<?php else: ?>

    <div class="header">
        <div>
            <h1>📺 IPTV & OTT Streaming Server Control</h1>
            <div style="font-size: 13px; color: #94a3b8; margin-top: 4px;">Connected with Android App & REST API Engine</div>
        </div>
        <div style="display: flex; gap: 10px; align-items: center;">
            <span class="badge <?= $isMaintenance ? 'badge-red' : 'badge-green' ?>">
                <?= $isMaintenance ? '⚠️ MAINTENANCE MODE' : '🟢 SERVER ONLINE' ?>
            </span>
            <a href="?logout=1" style="color: #94a3b8; font-size: 13px; text-decoration: none; padding: 6px 12px; background: #334155; border-radius: 6px;">Logout</a>
        </div>
    </div>

    <?php if (!empty($successMsg)): ?>
        <div class="alert">✓ <?= htmlspecialchars($successMsg) ?></div>
    <?php endif; ?>

    <!-- STATS -->
    <div class="grid">
        <div class="stat-card">
            <div class="num"><?= count($channels) ?></div>
            <div class="label">Total Channels & Movies</div>
        </div>
        <div class="stat-card">
            <div class="num"><?= count($notifications) ?></div>
            <div class="label">Active Push Notifications</div>
        </div>
        <div class="stat-card">
            <div class="num"><?= count($users) ?></div>
            <div class="label">Registered Accounts</div>
        </div>
        <div class="stat-card">
            <form method="POST">
                <input type="hidden" name="action" value="toggle_maintenance">
                <button type="submit" class="<?= $isMaintenance ? 'badge-green' : 'btn-warning' ?>" style="margin-top: 8px;">
                    <?= $isMaintenance ? 'Disable Maintenance Mode' : 'Enable Maintenance Mode' ?>
                </button>
            </form>
        </div>
    </div>

    <!-- 1. BROADCAST NOTIFICATIONS -->
    <div class="card">
        <h2>📢 Broadcast Push Notification to All Users</h2>
        <form method="POST" style="display: grid; grid-template-columns: 1fr 1fr auto; gap: 10px; align-items: end;">
            <input type="hidden" name="action" value="send_notification">
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Notification Title</label>
                <input type="text" name="notif_title" placeholder="🎬 New Blockbuster Movies Added!" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Message Details</label>
                <input type="text" name="notif_message" placeholder="Watch Inception 4K and live sports streams now." required>
            </div>
            <div>
                <button type="submit" style="margin-bottom: 12px; padding: 10px 20px;">Send Broadcast</button>
            </div>
        </form>

        <table style="margin-top: 15px;">
            <thead>
                <tr>
                    <th>Title</th>
                    <th>Message</th>
                    <th>Date/Time</th>
                    <th>Action</th>
                </tr>
            </thead>
            <tbody>
                <?php foreach ($notifications as $n): ?>
                <tr>
                    <td style="font-weight: bold; color: #38bdf8;"><?= htmlspecialchars($n['title'] ?? '') ?></td>
                    <td><?= htmlspecialchars($n['message'] ?? '') ?></td>
                    <td><?= htmlspecialchars($n['timestamp'] ?? '') ?></td>
                    <td>
                        <form method="POST" style="margin: 0; display: inline;">
                            <input type="hidden" name="action" value="delete_notification">
                            <input type="hidden" name="notif_id" value="<?= htmlspecialchars($n['id'] ?? '') ?>">
                            <button type="submit" class="btn-danger" style="padding: 4px 8px; font-size: 12px; width: auto;">Delete</button>
                        </form>
                    </td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>

    <!-- 2. USER & SUBSCRIPTION MANAGEMENT -->
    <div class="card">
        <h2>👥 User Accounts & VIP Subscription Manager</h2>
        <form method="POST" style="display: grid; grid-template-columns: 1fr 1fr 1fr 1fr auto; gap: 10px; align-items: end; margin-bottom: 20px;">
            <input type="hidden" name="action" value="save_user">
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Username / ID</label>
                <input type="text" name="user_key" placeholder="vip_user" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Password</label>
                <input type="text" name="password" placeholder="pass123" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Plan Name</label>
                <input type="text" name="plan" placeholder="VIP 4K Ultra" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Validity (Days from now)</label>
                <input type="number" name="valid_days" value="30" required>
            </div>
            <div>
                <button type="submit" style="margin-bottom: 12px; padding: 10px 16px;">Save / Extend</button>
            </div>
        </form>

        <table>
            <thead>
                <tr>
                    <th>User ID</th>
                    <th>Plan</th>
                    <th>Subscription Status</th>
                    <th>Expiry Date</th>
                    <th>Bound Device ID</th>
                    <th>Action</th>
                </tr>
            </thead>
            <tbody>
                <?php foreach ($users as $uid => $u): 
                    $exp = $u['expiry_timestamp'] ?? (strtotime($u['expiry_date'] ?? 'now') * 1000);
                    $isAct = (round(microtime(true) * 1000) < $exp);
                    $boundDev = $u['bound_device_id'] ?? '';
                ?>
                <tr>
                    <td style="font-weight: bold;"><?= htmlspecialchars($uid) ?></td>
                    <td><?= htmlspecialchars($u['plan'] ?? 'VIP') ?></td>
                    <td>
                        <span class="badge <?= $isAct ? 'badge-green' : 'badge-red' ?>">
                            <?= $isAct ? 'ACTIVE' : 'EXPIRED' ?>
                        </span>
                    </td>
                    <td><?= htmlspecialchars($u['expiry_date'] ?? 'Expired') ?></td>
                    <td style="font-family: monospace; font-size: 12px; color: <?= !empty($boundDev) ? '#38bdf8' : '#94a3b8' ?>;">
                        <?= !empty($boundDev) ? htmlspecialchars($boundDev) : '<i>Unbound (Any Device)</i>' ?>
                    </td>
                    <td>
                        <?php if (!empty($boundDev)): ?>
                        <form method="POST" style="margin: 0; display: inline;">
                            <input type="hidden" name="action" value="unbind_device">
                            <input type="hidden" name="user_key" value="<?= htmlspecialchars($uid) ?>">
                            <button type="submit" class="btn-warning" style="padding: 4px 8px; font-size: 11px; width: auto;">Unbind Device</button>
                        </form>
                        <?php else: ?>
                        <span style="font-size: 11px; color: #64748b;">Ready for Login</span>
                        <?php endif; ?>
                    </td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>

    <!-- 3. CHANNELS & MOVIES MANAGER -->
    <div class="card">
        <h2>🎬 Live TV Channels & Movies Database</h2>
        <form method="POST" style="display: grid; grid-template-columns: 2fr 3fr 1fr 1fr 1fr auto; gap: 8px; align-items: end; margin-bottom: 20px;">
            <input type="hidden" name="action" value="add_channel">
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Channel/Movie Title</label>
                <input type="text" name="title" placeholder="Star Sports VIP" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Stream URL (.m3u8 / .mp4 / .ts)</label>
                <input type="text" name="streamUrl" placeholder="https://stream.m3u8" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Type</label>
                <select name="category">
                    <option value="tv">Live TV</option>
                    <option value="movie">Movie</option>
                    <option value="series">Series</option>
                </select>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Category</label>
                <input type="text" name="subCategory" placeholder="Sports VIP" value="Sports">
            </div>
            <div style="display: flex; align-items: center; gap: 6px; padding-bottom: 16px;">
                <input type="checkbox" name="is_premium" id="is_prem" value="1" style="width: auto; margin: 0;">
                <label for="is_prem" style="font-size: 12px; color: #fbbf24; cursor: pointer;">VIP</label>
            </div>
            <div>
                <button type="submit" style="margin-bottom: 12px; padding: 10px 16px;">Add Item</button>
            </div>
        </form>

        <table>
            <thead>
                <tr>
                    <th>#</th>
                    <th>Title</th>
                    <th>Type</th>
                    <th>Category</th>
                    <th>Access</th>
                    <th>Action</th>
                </tr>
            </thead>
            <tbody>
                <?php foreach ($channels as $idx => $c): ?>
                <tr>
                    <td><?= $idx + 1 ?></td>
                    <td style="font-weight: 600;"><?= htmlspecialchars($c['title'] ?? '') ?></td>
                    <td><span style="text-transform: uppercase; font-size: 11px; background: #334155; padding: 2px 6px; border-radius: 4px;"><?= htmlspecialchars($c['category'] ?? 'tv') ?></span></td>
                    <td><?= htmlspecialchars($c['subCategory'] ?? '') ?></td>
                    <td>
                        <?php if (!empty($c['is_premium'])): ?>
                            <span class="badge badge-vip">VIP ONLY</span>
                        <?php else: ?>
                            <span class="badge badge-green">FREE</span>
                        <?php endif; ?>
                    </td>
                    <td>
                        <form method="POST" style="margin: 0; display: inline;">
                            <input type="hidden" name="action" value="delete_channel">
                            <input type="hidden" name="channel_index" value="<?= $idx ?>">
                            <button type="submit" class="btn-danger" style="padding: 4px 8px; font-size: 12px; width: auto;">Delete</button>
                        </form>
                    </td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>

    <!-- 4. APP LINKS & UPDATE CONFIGURATION -->
    <div class="card">
        <h2>🔗 App Support Links, Share URL & Software Updates</h2>
        <form method="POST" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
            <input type="hidden" name="action" value="save_settings">
            
            <div>
                <label style="font-size: 12px; color: #94a3b8;">WhatsApp Support URL</label>
                <input type="text" name="whatsapp_url" value="<?= htmlspecialchars($settings['whatsapp_url'] ?? '') ?>" placeholder="https://wa.me/8801700000000" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Telegram Group/Channel URL</label>
                <input type="text" name="telegram_url" value="<?= htmlspecialchars($settings['telegram_url'] ?? '') ?>" placeholder="https://t.me/telegram" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">App Share URL (Promotional Download Link)</label>
                <input type="text" name="app_share_url" value="<?= htmlspecialchars($settings['app_share_url'] ?? '') ?>" placeholder="https://verify-app.alwaysdata.net/download" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">APK Download Direct Link</label>
                <input type="text" name="download_url" value="<?= htmlspecialchars($settings['download_url'] ?? '') ?>" placeholder="https://verify-app.alwaysdata.net/new/mobile/app-release.apk" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">App Version Name</label>
                <input type="text" name="version_name" value="<?= htmlspecialchars($settings['version_name'] ?? '2.0.0') ?>" placeholder="2.0.0" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">App Version Code (Integer)</label>
                <input type="number" name="version_code" value="<?= (int)($settings['version_code'] ?? 2) ?>" placeholder="2" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Update Dialog Title</label>
                <input type="text" name="title" value="<?= htmlspecialchars($settings['title'] ?? '') ?>" placeholder="🚀 New Version 2.0.0 Available!" required>
            </div>
            <div>
                <label style="font-size: 12px; color: #94a3b8;">Developer / Engine Info Text</label>
                <input type="text" name="developer_info" value="<?= htmlspecialchars($settings['developer_info'] ?? '') ?>" placeholder="Official Stream IPTV Engine v2.0.0">
            </div>
            <div style="grid-column: span 2;">
                <label style="font-size: 12px; color: #94a3b8;">Update Message / Release Notes</label>
                <textarea name="message" rows="3" placeholder="A new update for Live TV Player is available with improved video playback..."><?= htmlspecialchars($settings['message'] ?? '') ?></textarea>
            </div>
            <div style="grid-column: span 2; display: flex; align-items: center; justify-content: space-between;">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <input type="checkbox" name="force_update" id="chk_force" value="1" <?= !empty($settings['force_update']) ? 'checked' : '' ?> style="width: auto; margin: 0;">
                    <label for="chk_force" style="font-size: 13px; color: #ef4444; cursor: pointer; font-weight: bold;">Force Mandatory Update</label>
                </div>
                <button type="submit" style="width: auto; padding: 10px 24px;">Save Configuration</button>
            </div>
        </form>
    </div>

<?php endif; ?>
</div>

</body>
</html>
