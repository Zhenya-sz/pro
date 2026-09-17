# Fitness Lemon Server

This repository contains the mobile app and a dedicated `server/` directory for API-related code.

## Structure

- `server/index.php` — HTTP router
- `server/src/` — PHP classes for DB, auth, controllers, middleware and models
- `server/.env.example` — environment template
- `server/composer.json` — PHP package definition

## Quick start

```bash
cd server
composer install
cp .env.example .env
php -S 0.0.0.0:8080 -t public
```

The API keeps the mobile app and server concerns separate so backend logic can evolve without mixing in Android-specific code.
