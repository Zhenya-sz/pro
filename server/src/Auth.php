<?php

namespace FitnessLemon;

use Firebase\JWT\JWT;
use Firebase\JWT\Key;

class Auth
{
    public static function generateToken(int $userId, string $role = 'user'): string
    {
        $payload = [
            'user_id' => $userId,
            'role' => $role,
            'iat' => time(),
            'exp' => time() + (int)($_ENV['JWT_TTL'] ?? 86400),
        ];

        return JWT::encode($payload, $_ENV['JWT_SECRET'] ?? 'change-me', 'HS256');
    }

    public static function validateToken(string $token): ?object
    {
        try {
            return JWT::decode($token, new Key($_ENV['JWT_SECRET'] ?? 'change-me', 'HS256'));
        } catch (\Throwable $e) {
            error_log('JWT decode error: ' . $e->getMessage());
            return null;
        }
    }

    public static function getUserFromToken(string $token): ?array
    {
        $decoded = self::validateToken($token);
        if ($decoded === null) {
            return null;
        }

        try {
            $db = Database::connect();
            $stmt = $db->prepare('SELECT id, name, email, phone, role, avatar, is_active FROM users WHERE id = ?');
            $stmt->execute([(int)($decoded->user_id ?? 0)]);
            $user = $stmt->fetch();

            if (!$user || empty($user['is_active'])) {
                return null;
            }

            return $user;
        } catch (\Throwable $e) {
            error_log('getUserFromToken error: ' . $e->getMessage());
            return null;
        }
    }

    public static function hashPassword(string $password): string
    {
        return password_hash($password, PASSWORD_BCRYPT, ['cost' => 12]);
    }

    public static function verifyPassword(string $password, string $hash): bool
    {
        return password_verify($password, $hash);
    }
}
