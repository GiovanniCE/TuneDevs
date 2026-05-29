-- Initializes database tables used by users, songs, preferences, and tunings; it provides schema and seed data needed by the backend API.

-- AI Generated Schema using Gemini. Prompt: Based on my drafted database schema
-- (Manually created on paper, checked for BCNF, and Chase's Algorithm) convert the relations to SQL code
-- I verified the relationship of the database on paper and checked all functional dependencies.

-- Ensure we are using the database defined in your .env file
USE guitar_tuner;

-- ==========================================
-- 1. TABLE CREATION
-- ==========================================

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE songs (
    song_id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    artist VARCHAR(100) NOT NULL,
    UNIQUE (title, artist)
);

CREATE TABLE tuning_profiles (
    profile_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    song_id INT, -- Changed to allow nullable values so that a user can create a profile that is not tied to a specifc song (i.e. "Drop D")
    profile_name VARCHAR(50) DEFAULT 'Standard',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (song_id) REFERENCES songs(song_id) ON DELETE CASCADE,
    UNIQUE (user_id, song_id, profile_name)
);

CREATE TABLE string_configs (
    profile_id INT NOT NULL,
    string_number INT NOT NULL,
    note_name VARCHAR(5) NOT NULL,
    frequency_hz DECIMAL(7,3) NOT NULL, -- Increased from (6,2) to allow for more accurate data for processing.
    PRIMARY KEY (profile_id, string_number),
    FOREIGN KEY (profile_id) REFERENCES tuning_profiles(profile_id) ON DELETE CASCADE
);