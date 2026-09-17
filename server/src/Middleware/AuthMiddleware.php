<?php

namespace FitnessLemon\Middleware;

use FitnessLemon\Auth;
use FitnessLemon\Response;

class AuthMiddleware
{
    public static function authenticate(string $authorizationHeader): ?array
    {
        if (empty($authorizationHeader) || !str_starts_with($authorizationHeader, 'Bearer ')) {
            return null;
        }

        $token = trim(substr($authorizationHeader, 7));
        if ($token === '') {
            return null;
        }

        $user = Auth::getUserFromToken($token);
        if ($user === null) {
            return null;
        }

        return $user;
    }

    public static function requireRole(array $roles): callable
    {
        return function (?array $user) use ($roles): void {
            if (!$user || !in_array($user['role'] ?? '', $roles, true)) {
                Response::forbidden('Access denied');
            }
        };
    }
}
