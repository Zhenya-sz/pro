<?php

namespace FitnessLemon\Models;

use FitnessLemon\Database;

class Story
{
    private \PDO $db;

    public function __construct()
    {
        $this->db = Database::connect();
    }

    public function getActiveStories(): array
    {
        $stmt = $this->db->query('SELECT * FROM stories WHERE expires_at > NOW() ORDER BY created_at DESC');
        return $stmt->fetchAll();
    }
}
