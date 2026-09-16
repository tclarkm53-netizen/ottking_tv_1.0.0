<?php
require_once __DIR__ . '/config.php';

function sign_payload(string $payload): string {
    return hash_hmac('sha256', $payload, HMAC_KEY);
}

function verify_signature(string $payload, string $signature): bool {
    return hash_equals(sign_payload($payload), $signature);
}

function encrypt_payload(string $plainText): string {
    $iv  = random_bytes(12);
    $tag = '';

    $cipherText = openssl_encrypt(
        $plainText,
        'aes-256-gcm',
        ENCRYPTION_KEY,
        OPENSSL_RAW_DATA,
        $iv,
        $tag,
        '',
        16
    );

    if ($cipherText === false || $tag === null || $tag === '') {
        throw new RuntimeException('Crypto Engine: Encryption failed');
    }

    return base64_encode($iv) . '.' . base64_encode($cipherText . $tag);
}

function decrypt_payload(string $encryptedPayload): string {
    $parts = explode('.', $encryptedPayload, 2);
    if (count($parts) !== 2) {
        throw new InvalidArgumentException('Crypto Engine: Encrypted payload split format invalid');
    }

    $iv       = base64_decode($parts[0], true);
    $combined = base64_decode($parts[1], true);

    if ($iv === false || $combined === false || strlen($combined) <= 16) {
        throw new InvalidArgumentException('Crypto Engine: Base64 decode failed or payload too short');
    }

    $tag        = substr($combined, -16);
    $cipherText = substr($combined, 0, -16);

    $plainText = openssl_decrypt(
        $cipherText,
        'aes-256-gcm',
        ENCRYPTION_KEY,
        OPENSSL_RAW_DATA,
        $iv,
        $tag
    );

    if ($plainText === false) {
        throw new RuntimeException('Crypto Engine: Decryption failed (Key mismatch)');
    }

    return $plainText;
}

function get_request_payload(): ?array {
    $rawInput = file_get_contents('php://input');
    if (empty($rawInput)) return null;

    $input = json_decode($rawInput, true);
    if (!$input) return null;

    if (isset($input['encrypted_payload'])) {
        $decryptedJson = decrypt_payload($input['encrypted_payload']);
        if (isset($input['signature'])) {
            if (!verify_signature($decryptedJson, $input['signature'])) {
                throw new RuntimeException('Signature verification failed');
            }
        }
        return json_decode($decryptedJson, true);
    }

    return $input;
}

function send_secure_response($data, int $status = 200) {
    http_response_code($status);
    $json = json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    
    $encryptedPayload = encrypt_payload($json);
    $signature        = sign_payload($json);

    header("Content-Type: application/json; charset=utf-8");
    header("X-Signature: " . $signature);

    echo json_encode([
        'encrypted_payload' => $encryptedPayload,
        'signature'         => $signature
    ]);
    exit();
}

function verify_hmac($payload, $receivedSignature) {
    return verify_signature($payload, $receivedSignature);
}

function generate_hmac($payload) {
    return sign_payload($payload);
}

function encrypt_data($data) {
    $json = is_string($data) ? $data : json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    return encrypt_payload($json);
}

function decrypt_data($base64Data) {
    $plain = decrypt_payload($base64Data);
    return json_decode($plain, true);
}

function verify_app_client_header(): bool {
    $headers = getallheaders();
    $clientToken = $headers['X-App-Client'] ?? $headers['x-app-client'] ?? '';
    if (empty($clientToken)) {
        $clientToken = $_SERVER['HTTP_X_APP_CLIENT'] ?? '';
    }
    return !empty($clientToken) && hash_equals(X_APP_CLIENT_TOKEN, $clientToken);
}

function generate_stream_token(int $channelId, string $deviceId, string $sessionToken, int $ttlSeconds = 14400): array {
    $expiresAt = time() + $ttlSeconds;
    $appClient = X_APP_CLIENT_TOKEN;
    $payload = "{$channelId}:{$deviceId}:{$sessionToken}:{$appClient}:{$expiresAt}";
    $signature = sign_payload($payload);
    $token = base64_encode("{$payload}.{$signature}");

    return [
        'stream_token' => $token,
        'expires_at'   => $expiresAt * 1000,
        'app_client'   => $appClient,
        'auth_header'  => "Bearer {$token}"
    ];
}

function verify_stream_token(string $token, int $channelId = 0, string $deviceId = ''): array {
    $decoded = base64_decode($token, true);
    if (!$decoded || !str_contains($decoded, '.')) {
        return ['valid' => false, 'error' => 'Invalid stream token format'];
    }

    $lastDot = strrpos($decoded, '.');
    $payload = substr($decoded, 0, $lastDot);
    $receivedSig = substr($decoded, $lastDot + 1);

    if (!verify_signature($payload, $receivedSig)) {
        return ['valid' => false, 'error' => 'Stream token HMAC signature mismatch'];
    }

    $parts = explode(':', $payload);
    if (count($parts) < 5) {
        return ['valid' => false, 'error' => 'Malformed stream token payload'];
    }

    $tokenChannelId = (int)$parts[0];
    $tokenDeviceId  = $parts[1];
    $tokenSession   = $parts[2];
    $tokenAppClient = $parts[3];
    $tokenExpires   = (int)$parts[4];

    if ($tokenExpires < time()) {
        return ['valid' => false, 'error' => 'Stream token has expired'];
    }

    if (!hash_equals(X_APP_CLIENT_TOKEN, $tokenAppClient)) {
        return ['valid' => false, 'error' => 'Invalid App Client token'];
    }

    if ($channelId > 0 && $tokenChannelId > 0 && $tokenChannelId !== $channelId) {
        return ['valid' => false, 'error' => 'Token not authorized for this channel'];
    }

    return [
        'valid'        => true,
        'channel_id'   => $tokenChannelId,
        'device_id'    => $tokenDeviceId,
        'session_token'=> $tokenSession,
        'expires_at'   => $tokenExpires
    ];
}

function generate_edge_cookie(string $deviceId, string $sessionToken, int $ttlSeconds = 86400, string $appId = 'com.ottking.devcode'): string {
    $expiresAt = time() + $ttlSeconds;
    $appClient = X_APP_CLIENT_TOKEN;
    $payload = "edge:{$deviceId}:{$sessionToken}:{$appClient}:{$expiresAt}:{$appId}";
    $sig = sign_payload($payload);
    return base64_encode("{$payload}.{$sig}");
}

function send_edge_cookie_header(string $deviceId, string $sessionToken, string $appId = 'com.ottking.devcode'): string {
    $cookieVal = generate_edge_cookie($deviceId, $sessionToken, 86400, $appId);
    header("Edge-Cookie: " . $cookieVal);
    header("edge-cookie: " . $cookieVal);
    header("X-Edge-Cookie: " . $cookieVal);
    header("Set-Cookie: edge_cookie=" . $cookieVal . "; Path=/; HttpOnly; SameSite=Lax");
    return $cookieVal;
}

function verify_edge_cookie(string $cookie, int $gracePeriodSeconds = 86400): array {
    $decoded = base64_decode($cookie, true);
    if (!$decoded || !str_contains($decoded, '.')) {
        return ['valid' => false, 'error' => 'Invalid edge-cookie format'];
    }
    $lastDot = strrpos($decoded, '.');
    $payload = substr($decoded, 0, $lastDot);
    $receivedSig = substr($decoded, $lastDot + 1);

    if (!verify_signature($payload, $receivedSig)) {
        return ['valid' => false, 'error' => 'Edge-cookie HMAC signature mismatch'];
    }

    $parts = explode(':', $payload);
    if (count($parts) < 5 || $parts[0] !== 'edge') {
        return ['valid' => false, 'error' => 'Malformed edge-cookie payload'];
    }

    $tokenDeviceId  = $parts[1];
    $tokenSession   = $parts[2];
    $tokenAppClient = $parts[3];
    $tokenExpires   = (int)$parts[4];
    $tokenAppId     = $parts[5] ?? 'com.ottking.devcode';

    $isExpired = ($tokenExpires < time());
    if ($tokenExpires + $gracePeriodSeconds < time()) {
        return ['valid' => false, 'error' => 'Edge-cookie has expired beyond grace period'];
    }

    if (!hash_equals(X_APP_CLIENT_TOKEN, $tokenAppClient)) {
        return ['valid' => false, 'error' => 'Invalid App Client in edge-cookie'];
    }

    return [
        'valid'         => true,
        'is_expired'    => $isExpired,
        'device_id'     => $tokenDeviceId,
        'session_token' => $tokenSession,
        'app_id'        => $tokenAppId,
        'expires_at'    => $tokenExpires
    ];
}

/**
 * Robust cookie extractor: extracts Edge-Cookie from Headers, Cookies, JSON headers,
 * URL Query params, and JSON request bodies (plain or encrypted).
 * Ensures that whether sent as raw string, standard cookie, or structured JSON,
 * the server always catches the proper cookie without missing it.
 */
function extract_edge_cookie_from_request(): string {
    $headers = getallheaders();

    // 1. Direct Edge-Cookie headers
    $direct = $headers['Edge-Cookie'] ?? $headers['Edge-cookie'] ?? $headers['edge-cookie'] ??
              $headers['X-Edge-Cookie'] ?? $headers['x-edge-cookie'] ?? $_SERVER['HTTP_EDGE_COOKIE'] ?? '';
    if (!empty($direct)) {
        return clean_cookie_token($direct);
    }

    // 2. JSON headers (e.g. X-Cookie-Json, X-Edge-Cookie-Json)
    $jsonHeader = $headers['X-Cookie-Json'] ?? $headers['x-cookie-json'] ??
                  $headers['X-Edge-Cookie-Json'] ?? $headers['x-edge-cookie-json'] ?? '';
    if (!empty($jsonHeader)) {
        $parsed = json_decode($jsonHeader, true);
        if (is_array($parsed)) {
            $val = $parsed['edge_cookie'] ?? $parsed['cookie'] ?? $parsed['token'] ?? '';
            if (!empty($val)) {
                return clean_cookie_token($val);
            }
        }
    }

    // 3. Standard HTTP Cookie ($_COOKIE or raw HTTP_COOKIE header)
    if (!empty($_COOKIE['edge_cookie'])) {
        return clean_cookie_token($_COOKIE['edge_cookie']);
    }
    if (!empty($_COOKIE['edge-cookie'])) {
        return clean_cookie_token($_COOKIE['edge-cookie']);
    }
    $cookieHeader = $_SERVER['HTTP_COOKIE'] ?? $headers['Cookie'] ?? $headers['cookie'] ?? '';
    if (!empty($cookieHeader)) {
        if (preg_match('/(?:edge_cookie|edge-cookie)=([^;]+)/i', $cookieHeader, $matches)) {
            return clean_cookie_token(urldecode($matches[1]));
        }
    }

    // 4. Query Parameters (GET)
    if (!empty($_GET['edge_cookie'])) {
        return clean_cookie_token($_GET['edge_cookie']);
    }
    if (!empty($_GET['cookie'])) {
        return clean_cookie_token($_GET['cookie']);
    }
    if (!empty($_GET['cookie_json'])) {
        $parsed = json_decode($_GET['cookie_json'], true);
        if (is_array($parsed)) {
            $val = $parsed['edge_cookie'] ?? $parsed['cookie'] ?? '';
            if (!empty($val)) {
                return clean_cookie_token($val);
            }
        }
    }

    // 5. Request Payload / Body JSON
    try {
        $payload = get_request_payload();
        if (is_array($payload)) {
            if (!empty($payload['edge_cookie'])) {
                return clean_cookie_token($payload['edge_cookie']);
            }
            if (!empty($payload['cookie'])) {
                return clean_cookie_token($payload['cookie']);
            }
            if (isset($payload['cookie_json']) && is_array($payload['cookie_json'])) {
                $val = $payload['cookie_json']['edge_cookie'] ?? $payload['cookie_json']['cookie'] ?? '';
                if (!empty($val)) {
                    return clean_cookie_token($val);
                }
            }
        }
    } catch (Exception $e) {}

    return '';
}

function clean_cookie_token(string $raw): string {
    $raw = trim($raw);
    if (stripos($raw, 'edge_cookie=') !== false) {
        if (preg_match('/edge_cookie=([^;]+)/i', $raw, $m)) {
            return trim($m[1]);
        }
    }
    if (stripos($raw, 'edge-cookie=') !== false) {
        if (preg_match('/edge-cookie=([^;]+)/i', $raw, $m)) {
            return trim($m[1]);
        }
    }
    return $raw;
}



