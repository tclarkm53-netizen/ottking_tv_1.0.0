<?php
// OTT KING Server Configuration & Security Keys
header('Content-Type: application/json; charset=utf-8');

define('API_KEY', '2K27SAA70ZK');
define('HMAC_KEY', '1d1d702a20fcbb565473edf4e4119ec9');
define('ENCRYPTION_KEY', 'ae854c8077a43cb0f5e3293a4f422dbb');
define('X_APP_CLIENT_TOKEN', API_KEY);

// SQLite Database Setup
$db_file = __DIR__ . '/ottking.sqlite';
$db = new PDO('sqlite:' . $db_file);
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

// Initialize Tables
$db->exec("CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE,
    password TEXT,
    package TEXT,
    expiry_date TEXT,
    bound_device_id TEXT,
    session_token TEXT
)");

$db->exec("CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT,
    icon TEXT
)");

$db->exec("CREATE TABLE IF NOT EXISTS channels (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT,
    logo_url TEXT,
    stream_url TEXT,
    category_id INTEGER,
    is_premium INTEGER,
    stream_type TEXT
)");

$db->exec("CREATE TABLE IF NOT EXISTS reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT,
    category TEXT,
    description TEXT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
)");

// Seed initial demo data if empty
$userCount = $db->query("SELECT COUNT(*) FROM users")->fetchColumn();
if ($userCount == 0) {
    $db->exec("INSERT INTO users (username, password, package, expiry_date, bound_device_id, session_token) VALUES 
        ('admin', '123456', 'VIP Premium Ultra', '2030-12-31', '', ''),
        ('user1', '1234', 'Basic Plan', '2026-12-31', '', '')");
}

$catCount = $db->query("SELECT COUNT(*) FROM categories")->fetchColumn();
if ($catCount == 0) {
    $db->exec("INSERT INTO categories (id, name, icon) VALUES 
        (1, 'All', 'ic_tv'),
        (2, 'Sports Live', 'ic_play'),
        (3, 'News & World', 'ic_info'),
        (4, 'Movies & Cinema', 'ic_play'),
        (5, 'Entertainment', 'ic_tv')");
}

$chanCount = $db->query("SELECT COUNT(*) FROM channels")->fetchColumn();
if ($chanCount == 0) {
    // Sample high-quality stream test links (HLS / MP4)
    $db->exec("INSERT INTO channels (name, logo_url, stream_url, category_id, is_premium, stream_type) VALUES 
        ('OTT KING Sports 1 HD', 'https://picsum.photos/200/200?random=1', 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 2, 0, 'hls'),
        ('OTT KING Premium Sports 4K', 'https://picsum.photos/200/200?random=2', 'https://playertest.longtailvideo.com/adaptive/bbbell/bbbell.m3u8', 2, 1, 'hls'),
        ('World News 24/7', 'https://picsum.photos/200/200?random=3', 'https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8', 3, 0, 'hls'),
        ('Action Movies Live', 'https://picsum.photos/200/200?random=4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 4, 0, 'ts'),
        ('VIP Cinema Ultra', 'https://picsum.photos/200/200?random=5', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4', 4, 1, 'ts'),
        ('Entertainment Plus', 'https://picsum.photos/200/200?random=6', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4', 5, 0, 'ts')");
}
