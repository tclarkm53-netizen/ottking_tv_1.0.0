<?php
/**
 * Crypto_lib.php
 * Secure AES-256-GCM & HMAC-SHA256 Encryption Library
 */

if (!defined('BASEPATH') && !defined('ALLOW_SERVER_ACCESS')) {
    define('ALLOW_SERVER_ACCESS', true);
}

class Crypto_lib
{
    protected $CI;
    private $encryption_key = null;
    private $hmac_secret = null;
    private $app_key_id = null;

    public function __construct()
    {
        if (function_exists('get_instance')) {
            $this->CI =& get_instance();
            if (isset($this->CI->db)) {
                $this->CI->load->database();
            }
        }
    }

    public function set_keys(string $encryptionKey, string $hmacSecret, string $appKeyId = 'default')
    {
        $this->encryption_key = $encryptionKey;
        $this->hmac_secret    = $hmacSecret;
        $this->app_key_id     = $appKeyId;
    }

    /**
     * app_key_id দিয়ে ডেটাবেইস থেকে ডাইনামিক কী লোড করার মেথড
     */
    public function init_keys_by_id(string $appKeyId): bool
    {
        if ($this->CI && isset($this->CI->db)) {
            $query = $this->CI->db->get_where('app_credentials', [
                'app_key_id' => $appKeyId,
                'status'     => 1 // শুধুমাত্র অ্যাক্টিভ অ্যাপের জন্য
            ], 1);

            $credential = $query->row();

            if (!$credential) {
                return false;
            }

            // ডাইনামিকালি প্রোপার্টিতে কী সেট করা হচ্ছে
            $this->app_key_id     = $credential->app_key_id;
            $this->encryption_key = $credential->encryption_key;
            $this->hmac_secret    = $credential->hmac_secret;

            return true;
        }
        return false;
    }

    /**
     * কী লোড হয়েছে কিনা তা নিশ্চিত করার অভ্যন্তরীণ মেথড
     */
    private function ensure_keys_loaded()
    {
        if (empty($this->encryption_key) || empty($this->hmac_secret)) {
            throw new RuntimeException('Crypto Engine: Encryption keys are not initialized or app is inactive.');
        }
    }

    public function sign_payload(string $payload): string
    {
        $this->ensure_keys_loaded();
        return hash_hmac('sha256', $payload, $this->hmac_secret);
    }

    public function verify_signature(string $payload, string $signature): bool
    {
        return hash_equals($this->sign_payload($payload), $signature);
    }

    public function encrypt_payload(string $plainText): string
    {
        $this->ensure_keys_loaded();
        
        $iv  = random_bytes(12);
        $tag = '';

        $cipherText = openssl_encrypt(
            $plainText,
            'aes-256-gcm',
            $this->encryption_key, // ডাইনামিক কী
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

    public function decrypt_payload(string $encryptedPayload): string
    {
        $this->ensure_keys_loaded();

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
            $this->encryption_key, // ডাইনামিক কী
            OPENSSL_RAW_DATA,
            $iv,
            $tag
        );

        if ($plainText === false) {
            throw new RuntimeException('Crypto Engine: Decryption failed (Key mismatch)');
        }

        return $plainText;
    }

    /**
     * আপনার অ্যাপের বর্তমান লজিক (Encrypt-and-Sign) অক্ষুণ্ন রাখা হলো
     */
    public function secure_response(array $payload): array
    {
        $json = json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        return [
            'encrypted_payload' => $this->encrypt_payload($json),
            'signature'         => $this->sign_payload($json),
        ];
    }
}
