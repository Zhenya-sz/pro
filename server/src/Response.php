<?php

namespace FitnessLemon;

class Response
{
    public static function json(array $payload, int $status = 200): void
    {
        http_response_code($status);
        echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }

    public static function success($data = [], string $message = 'success'): void
    {
        self::json([
            'success' => true,
            'message' => $message,
            'data' => $data,
        ]);
    }

    public static function error(string $message, int $status = 400, $data = []): void
    {
        self::json([
            'success' => false,
            'message' => $message,
            'data' => $data,
        ], $status);
    }

    public static function unauthorized(string $message = 'Unauthorized'): void
    {
        self::error($message, 401);
    }

    public static function forbidden(string $message = 'Forbidden'): void
    {
        self::error($message, 403);
    }

    public static function notFound(string $message = 'Not found'): void
    {
        self::error($message, 404);
    }
}
