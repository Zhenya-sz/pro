<?php

namespace FitnessLemon\Notifications;

use FitnessLemon\Database;

class FCMService
{
    public function sendToUser(int $userId, string $title, string $body, array $data = []): bool
    {
        $db = Database::connect();
        $stmt = $db->prepare('SELECT fcm_token FROM user_devices WHERE user_id = ? AND fcm_token IS NOT NULL');
        $stmt->execute([$userId]);
        $tokens = $stmt->fetchAll(\PDO::FETCH_COLUMN);

        if (empty($tokens)) {
            return false;
        }

        $apiKey = $_ENV['FCM_API_KEY'] ?? '';
        if ($apiKey === '') {
            return false;
        }

        $payload = [
            'registration_ids' => $tokens,
            'notification' => [
                'title' => $title,
                'body' => $body,
                'sound' => 'default',
            ],
            'data' => array_merge(['click_action' => 'FLUTTER_NOTIFICATION_CLICK'], $data),
            'priority' => 'high',
        ];

        $ch = curl_init('https://fcm.googleapis.com/fcm/send');
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($payload));
        curl_setopt($ch, CURLOPT_HTTPHEADER, [
            'Authorization: key=' . $apiKey,
            'Content-Type: application/json',
        ]);

        $response = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        return $httpCode === 200 && str_contains((string)$response, 'success');
    }
}
