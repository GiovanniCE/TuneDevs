// Handles local login/registration and persistence of user preferences; it provides authentication booleans and saved/loaded UserPreferences.

package com.autotuner.backend.service;

import com.autotuner.backend.model.FavoriteSongPreference;
import com.autotuner.backend.model.UserPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserPreferencesService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferencesService.class);

    private static final String DEFAULT_ARTIST = "Unknown";

    private final JdbcTemplate jdbcTemplate;

    public UserPreferencesService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean validateLogin(String username, String password) {
        // Normalize user input before comparing so accidental surrounding
        // spaces do not break local development logins.
        String normalizedUsername = username == null ? null : username.trim();
        String normalizedPassword = password == null ? null : password.trim();

        if (normalizedUsername == null || normalizedUsername.isEmpty() || normalizedPassword == null) {
            return false;
        }

        String stored = jdbcTemplate.query(
                "SELECT password_hash FROM users WHERE username = ?",
                rs -> rs.next() ? rs.getString("password_hash") : null,
            normalizedUsername
        );

        if (stored == null) {
            return false;
        }

        // Temporary plain-text compare for local development.
        return stored.equals(normalizedPassword);
    }

    public boolean register(String username, String password) {
        String normalizedUsername = username == null ? null : username.trim();
        String normalizedPassword = password == null ? null : password.trim();

        if (normalizedUsername == null || normalizedUsername.isEmpty() || normalizedPassword == null || normalizedPassword.isEmpty()) {
            return false;
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                    normalizedUsername,
                    normalizedPassword
            );
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    public UserPreferences getPreferences(String username) {
        Integer userId = findUserId(username);
        if (userId == null) {
            // Unknown users behave like new users with no saved preferences.
            return new UserPreferences(new ArrayList<>(), new ArrayList<>());
        }

        List<String> preferredTuningKeys = jdbcTemplate.query(
                "SELECT profile_name FROM tuning_profiles WHERE user_id = ? AND song_id IS NULL ORDER BY profile_id",
                (rs, rowNum) -> rs.getString("profile_name"),
                userId
        );

        List<FavoriteSongPreference> favoriteSongs = jdbcTemplate.query(
                "SELECT s.title AS song_name, s.artist AS song_artist, tp.profile_name AS tuning_key " +
                        "FROM tuning_profiles tp " +
                        "JOIN songs s ON s.song_id = tp.song_id " +
                "WHERE tp.user_id = ? AND tp.song_id IS NOT NULL " +
                        "ORDER BY tp.profile_id",
                (rs, rowNum) -> {
                    String artist = rs.getString("song_artist");
                    // The database placeholder should not leak into UI labels.
                    if (artist != null && DEFAULT_ARTIST.equalsIgnoreCase(artist.trim())) {
                        artist = null;
                    }
                    return new FavoriteSongPreference(
                            rs.getString("song_name"),
                            rs.getString("tuning_key"),
                            artist
                    );
                },
                userId
        );

        return new UserPreferences(preferredTuningKeys, favoriteSongs);
    }

    @Transactional
    public void savePreferences(String username, UserPreferences preferences) {
        Integer userId = findUserId(username);
        if (userId == null) {
            // Some local flows can save preferences for a newly seen username,
            // so create a lightweight development account automatically.
            register(username, "local_dev");
            userId = findUserId(username);
        }

        if (userId == null) {
            throw new IllegalStateException("Unable to resolve user id for: " + username);
        }

        // Preserve existing artist values by song title if a subsequent payload omits artist.
        Map<String, String> existingArtistByTitle = new HashMap<>();
        List<Map<String, Object>> existingSongs = jdbcTemplate.queryForList(
                "SELECT s.title AS title, s.artist AS artist " +
                        "FROM tuning_profiles tp " +
                        "JOIN songs s ON s.song_id = tp.song_id " +
                        "WHERE tp.user_id = ? AND tp.song_id IS NOT NULL",
                userId
        );
        for (Map<String, Object> row : existingSongs) {
            Object titleObj = row.get("title");
            Object artistObj = row.get("artist");
            if (titleObj == null || artistObj == null) {
                continue;
            }
            String title = titleObj.toString().trim();
            String artist = artistObj.toString().trim();
            if (!title.isEmpty() && !artist.isEmpty() && !DEFAULT_ARTIST.equals(artist)) {
                existingArtistByTitle.putIfAbsent(title.toLowerCase(), artist);
            }
        }

        // Rebuild the user's preference rows from the incoming payload. This is
        // simpler than diffing because the preferences list is small.
        jdbcTemplate.update("DELETE FROM tuning_profiles WHERE user_id = ?", userId);

        if (preferences.getPreferredTuningKeys() != null) {
            for (String tuningKey : preferences.getPreferredTuningKeys()) {
            String normalizedTuningKey = sanitizeProfileName(tuningKey);
            if (normalizedTuningKey == null) {
                continue;
            }

                try {
                    jdbcTemplate.update(
                            "INSERT IGNORE INTO tuning_profiles (user_id, song_id, profile_name) VALUES (?, NULL, ?)",
                            userId,
                            normalizedTuningKey
                    );
                } catch (RuntimeException ex) {
                    log.warn("Skipping invalid preferred tuning '{}' for user '{}': {}", normalizedTuningKey, username, ex.getMessage());
                }
            }
        }

        if (preferences.getFavoriteSongs() != null) {
            for (FavoriteSongPreference song : preferences.getFavoriteSongs()) {
            String normalizedTitle = sanitizeSongTitle(song.getName());
            String normalizedTuning = sanitizeProfileName(song.getTuning());

            if (normalizedTitle == null || normalizedTuning == null) {
                    continue;
                }

                log.info("savePreferences: song='{}' raw artist from payload='{}'", normalizedTitle, song.getArtist());
                // If the frontend omits artist during a later save, keep the
                // artist previously stored for the same title when available.
                String resolvedArtist = (song.getArtist() != null && !song.getArtist().isBlank())
                ? song.getArtist().trim()
                    : existingArtistByTitle.getOrDefault(normalizedTitle.toLowerCase(), DEFAULT_ARTIST);
            resolvedArtist = sanitizeArtist(resolvedArtist);
                log.info("savePreferences: song='{}' resolved artist='{}'", normalizedTitle, resolvedArtist);

                try {
                    // Ensure the song row with the resolved artist exists.
                    // INSERT IGNORE makes this idempotent — no duplicate-key errors even if the
                    // row was written on a previous save.
                    jdbcTemplate.update(
                        "INSERT IGNORE INTO songs (title, artist) VALUES (?, ?)",
                        normalizedTitle,
                        resolvedArtist
                    );

                    Integer songId = jdbcTemplate.query(
                        "SELECT song_id FROM songs WHERE title = ? AND artist = ?",
                        rs -> rs.next() ? rs.getInt("song_id") : null,
                        normalizedTitle,
                        resolvedArtist
                    );

                    if (songId == null) {
                        continue;
                    }

                    jdbcTemplate.update(
                        "INSERT IGNORE INTO tuning_profiles (user_id, song_id, profile_name) VALUES (?, ?, ?)",
                        userId,
                        songId,
                        normalizedTuning
                    );
                } catch (RuntimeException ex) {
                    log.warn(
                        "Skipping invalid favorite song '{}' / artist '{}' / tuning '{}' for user '{}': {}",
                        normalizedTitle,
                        resolvedArtist,
                        normalizedTuning,
                        username,
                        ex.getMessage()
                    );
                }
            }
        }
    }

    private String sanitizeSongTitle(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Match the database column size instead of letting SQL reject input.
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }

    private String sanitizeArtist(String value) {
        if (value == null) {
            return DEFAULT_ARTIST;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_ARTIST;
        }

        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }

    private String sanitizeProfileName(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // tuning_profiles.profile_name is limited to 50 characters.
        return trimmed.length() <= 50 ? trimmed : trimmed.substring(0, 50);
    }

    private Integer findUserId(String username) {
        return jdbcTemplate.query(
                "SELECT user_id FROM users WHERE username = ?",
                rs -> rs.next() ? rs.getInt("user_id") : null,
                username
        );
    }
}
