<?php

namespace FitnessLemon\Controllers;

use FitnessLemon\Auth;
use FitnessLemon\Database;
use FitnessLemon\Middleware\AuthMiddleware;
use FitnessLemon\Response;

class AuthController
{
    private ?array $currentUser;

    public function __construct(?array $currentUser)
    {
        $this->currentUser = $currentUser;
    }

    public function login(array $input): void
    {
        $email = trim((string)($input['email'] ?? ''));
        $password = (string)($input['password'] ?? '');

        if ($email === '' || $password === '') {
            Response::error('Email and password are required');
            return;
        }

        $db = Database::connect();
        $stmt = $db->prepare('SELECT id, name, email, password_hash, role, is_active FROM users WHERE email = ?');
        $stmt->execute([$email]);
        $user = $stmt->fetch();

        if (!$user || !Auth::verifyPassword($password, $user['password_hash'])) {
            Response::unauthorized('Invalid credentials');
            return;
        }

        if (empty($user['is_active'])) {
            Response::forbidden('User is inactive');
            return;
        }

        $token = Auth::generateToken((int)$user['id'], (string)$user['role']);

        Response::success([
            'token' => $token,
            'user' => [
                'id' => (int)$user['id'],
                'name' => $user['name'],
                'email' => $user['email'],
                'role' => $user['role'],
            ],
        ], 'Login successful');
    }

    public function register(array $input): void
    {
        $name = trim((string)($input['name'] ?? ''));
        $email = trim((string)($input['email'] ?? ''));
        $phone = preg_replace('/[^0-9]/', '', (string)($input['phone'] ?? ''));
        $password = (string)($input['password'] ?? '');

        if ($name === '' || $email === '' || $phone === '' || $password === '') {
            Response::error('All fields are required');
            return;
        }

        $db = Database::connect();
        $check = $db->prepare('SELECT id FROM users WHERE email = ? OR phone = ?');
        $check->execute([$email, $phone]);
        if ($check->fetch()) {
            Response::error('User already exists', 409);
            return;
        }

        $hash = Auth::hashPassword($password);
        $insert = $db->prepare('INSERT INTO users (name, email, phone, password_hash, role, is_active, created_at) VALUES (?, ?, ?, ?, ?, 1, NOW())');
        $insert->execute([$name, $email, $phone, $hash, 'user']);

        Response::success(['id' => (int)$db->lastInsertId()], 'Registration successful');
    }

    public function refresh(array $input): void
    {
        $token = trim((string)($input['token'] ?? ''));
        if ($token === '') {
            Response::unauthorized('Token is required');
            return;
        }

        $decoded = Auth::validateToken($token);
        if ($decoded === null) {
            Response::unauthorized('Token expired or invalid');
            return;
        }

        $newToken = Auth::generateToken((int)($decoded->user_id ?? 0), (string)($decoded->role ?? 'user'));
        Response::success(['token' => $newToken], 'Token refreshed');
    }

    public function logout(array $input): void
    {
        Response::success([], 'Logged out');
    }
}
