<?php

declare(strict_types=1);

require __DIR__ . '/vendor/autoload.php';

use Dotenv\Dotenv;
use FitnessLemon\Database;
use FitnessLemon\Auth;
use FitnessLemon\Response;
use FitnessLemon\Middleware\AuthMiddleware;
use FitnessLemon\Controllers\AuthController;
use FitnessLemon\Controllers\NewsController;
use FitnessLemon\Controllers\WorkoutController;
use FitnessLemon\Controllers\ScheduleController;
use FitnessLemon\Controllers\TrainerController;
use FitnessLemon\Controllers\ChatController;
use FitnessLemon\Controllers\MessageController;
use FitnessLemon\Controllers\NotificationController;
use FitnessLemon\Controllers\UserController;

try {
    Dotenv::createImmutable(__DIR__)->load();
} catch (Throwable $e) {
    error_log('DOTENV_ERROR: ' . $e->getMessage());
}

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-WP-Nonce, X-Requested-With');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

try {
    Database::connect();
} catch (Throwable $e) {
    Response::error('Database error: ' . $e->getMessage(), 500);
}

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$rawPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?? '/';
$path = preg_replace('#/+#', '/', $rawPath);
$path = rtrim($path, '/');
if ($path === '') {
    $path = '/';
}

$parts = array_values(array_filter(explode('/', $path), static fn($segment) => $segment !== ''));
$endpoint = $parts[0] ?? '';
$id = $parts[1] ?? null;
$subEndpoint = $parts[2] ?? null;

function authContext(): ?array {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (empty($header)) {
        return null;
    }
    return AuthMiddleware::authenticate($header);
}

$currentUser = authContext();

if ($method === 'GET' && $endpoint === 'health') {
    Response::success([
        'status' => 'ok',
        'time' => date('c'),
        'php' => PHP_VERSION,
    ]);
}

if ($endpoint === 'auth') {
    $controller = new AuthController($currentUser);
    if ($method === 'POST' && $id === 'login') {
        $controller->login($_POST);
        exit;
    }
    if ($method === 'POST' && $id === 'register') {
        $controller->register($_POST);
        exit;
    }
    if ($method === 'POST' && $id === 'refresh') {
        $controller->refresh($_POST);
        exit;
    }
    if ($method === 'POST' && $id === 'logout') {
        $controller->logout($_POST);
        exit;
    }
    Response::notFound('Unknown auth endpoint');
}

if ($endpoint === 'news') {
    $controller = new NewsController($currentUser);
    if ($method === 'GET' && $id === null) {
        $controller->list($_GET);
        exit;
    }
    if ($method === 'GET' && is_numeric($id ?? null)) {
        $controller->get((int)$id);
        exit;
    }
    if ($method === 'POST' && $id === null) {
        $controller->create($_POST);
        exit;
    }
    Response::notFound('Unknown news endpoint');
}

if ($endpoint === 'workouts') {
    $controller = new WorkoutController($currentUser);
    if ($method === 'GET' && $id === null) {
        $controller->list($_GET);
        exit;
    }
    if ($method === 'GET' && is_numeric($id ?? null)) {
        $controller->get((int)$id);
        exit;
    }
    if ($method === 'POST' && $id === null) {
        $controller->create($_POST);
        exit;
    }
    Response::notFound('Unknown workout endpoint');
}

if ($endpoint === 'schedule') {
    $controller = new ScheduleController($currentUser);
    if ($method === 'GET' && $id === 'week') {
        $controller->getWeek($_GET);
        exit;
    }
    if ($method === 'GET' && $id === 'day') {
        $controller->getDay($_GET);
        exit;
    }
    Response::notFound('Unknown schedule endpoint');
}

if ($endpoint === 'trainers') {
    $controller = new TrainerController($currentUser);
    if ($method === 'GET') {
        $controller->list($_GET);
        exit;
    }
    Response::notFound('Unknown trainer endpoint');
}

if ($endpoint === 'chats') {
    $controller = new ChatController($currentUser);
    if ($method === 'GET' && $id === null) {
        $controller->list();
        exit;
    }
    if ($method === 'POST' && $id === 'create') {
        $controller->create($_POST);
        exit;
    }
    if ($method === 'POST' && $id === 'send') {
        $controller->send($_POST);
        exit;
    }
    if ($method === 'GET' && $id !== null && $subEndpoint === 'messages') {
        $controller->getMessages((int)$id, $_GET);
        exit;
    }
    Response::notFound('Unknown chat endpoint');
}

if ($endpoint === 'messages') {
    $controller = new MessageController($currentUser);
    if ($method === 'POST' && $id === 'send') {
        $controller->send($_POST);
        exit;
    }
    Response::notFound('Unknown message endpoint');
}

if ($endpoint === 'users') {
    $controller = new UserController($currentUser);
    if ($method === 'GET' && $id === 'profile') {
        $controller->profile();
        exit;
    }
    if ($method === 'GET' && $id === null) {
        $controller->list($_GET);
        exit;
    }
    Response::notFound('Unknown user endpoint');
}

Response::error('Endpoint not found', 404);
