// Automated test class for UserPreferencesServiceTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.service;

import com.autotuner.backend.model.FavoriteSongPreference;
import com.autotuner.backend.model.UserPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UserPreferencesService using an in-memory H2 database.
 */
class UserPreferencesServiceTest {

    private JdbcTemplate jdbcTemplate;
    private UserPreferencesService service;

    private static JdbcTemplate createH2JdbcTemplate() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:userprefsdb_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return new JdbcTemplate(ds);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate = createH2JdbcTemplate();
        service = new UserPreferencesService(jdbcTemplate);

        // Drop tables in reverse dependency order, then recreate
        jdbcTemplate.execute("DROP TABLE IF EXISTS tuning_profiles");
        jdbcTemplate.execute("DROP TABLE IF EXISTS string_configs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS songs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");

        jdbcTemplate.execute(
            "CREATE TABLE users (" +
                "user_id INT AUTO_INCREMENT PRIMARY KEY," +
                "username VARCHAR(50) NOT NULL UNIQUE," +
                "password_hash VARCHAR(255) NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        );

        jdbcTemplate.execute(
            "CREATE TABLE songs (" +
                "song_id INT AUTO_INCREMENT PRIMARY KEY," +
                "title VARCHAR(100) NOT NULL," +
                "artist VARCHAR(100) NOT NULL," +
                "UNIQUE (title, artist)" +
            ")"
        );

        jdbcTemplate.execute(
            "CREATE TABLE tuning_profiles (" +
                "profile_id INT AUTO_INCREMENT PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "song_id INT," +
                "profile_name VARCHAR(50) DEFAULT 'Standard'," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE," +
                "FOREIGN KEY (song_id) REFERENCES songs(song_id) ON DELETE CASCADE," +
                "UNIQUE (user_id, song_id, profile_name)" +
            ")"
        );
    }

    // ── Registration ────────────────────────────────────────────────

    @Test
    void register_createsNewUser() {
        boolean result = service.register("testuser", "password123");
        assertTrue(result);
    }

    @Test
    void register_rejectsDuplicate() {
        service.register("testuser", "pass1");
        boolean result = service.register("testuser", "pass2");
        assertFalse(result);
    }

    @Test
    void register_rejectsNullUsername() {
        assertFalse(service.register(null, "password"));
    }

    @Test
    void register_rejectsEmptyUsername() {
        assertFalse(service.register("", "password"));
    }

    @Test
    void register_rejectsBlankUsername() {
        assertFalse(service.register("   ", "password"));
    }

    @Test
    void register_rejectsNullPassword() {
        assertFalse(service.register("testuser", null));
    }

    @Test
    void register_rejectsEmptyPassword() {
        assertFalse(service.register("testuser", ""));
    }

    @Test
    void register_trimsUsername() {
        service.register("  spaced  ", "pass");
        assertTrue(service.validateLogin("spaced", "pass"));
    }

    // ── Login validation ────────────────────────────────────────────

    @Test
    void validateLogin_succeedsWithCorrectCredentials() {
        service.register("loginuser", "secret");
        assertTrue(service.validateLogin("loginuser", "secret"));
    }

    @Test
    void validateLogin_failsWithWrongPassword() {
        service.register("loginuser", "secret");
        assertFalse(service.validateLogin("loginuser", "wrong"));
    }

    @Test
    void validateLogin_failsWithNonexistentUser() {
        assertFalse(service.validateLogin("ghost", "password"));
    }

    @Test
    void validateLogin_failsWithNullUsername() {
        assertFalse(service.validateLogin(null, "password"));
    }

    @Test
    void validateLogin_failsWithEmptyUsername() {
        assertFalse(service.validateLogin("", "password"));
    }

    @Test
    void validateLogin_failsWithNullPassword() {
        service.register("loginuser", "secret");
        assertFalse(service.validateLogin("loginuser", null));
    }

    @Test
    void validateLogin_trimsInput() {
        service.register("trimuser", "trimpass");
        assertTrue(service.validateLogin("  trimuser  ", "  trimpass  "));
    }

    // ── Get preferences ─────────────────────────────────────────────

    @Test
    void getPreferences_returnsEmptyForUnknownUser() {
        UserPreferences prefs = service.getPreferences("unknown");
        assertNotNull(prefs);
        assertTrue(prefs.getPreferredTuningKeys().isEmpty());
        assertTrue(prefs.getFavoriteSongs().isEmpty());
    }

    @Test
    void getPreferences_returnsEmptyForNewUser() {
        service.register("newuser", "pass");
        UserPreferences prefs = service.getPreferences("newuser");
        assertNotNull(prefs);
        assertTrue(prefs.getPreferredTuningKeys().isEmpty());
        assertTrue(prefs.getFavoriteSongs().isEmpty());
    }

    // ── Save & retrieve preferences ─────────────────────────────────

    @Test
    void savePreferences_storesPreferredTunings() {
        service.register("tuneuser", "pass");

        UserPreferences prefs = new UserPreferences(
            List.of("standard", "drop_d"),
            List.of()
        );
        service.savePreferences("tuneuser", prefs);

        UserPreferences loaded = service.getPreferences("tuneuser");
        assertEquals(2, loaded.getPreferredTuningKeys().size());
        assertTrue(loaded.getPreferredTuningKeys().contains("standard"));
        assertTrue(loaded.getPreferredTuningKeys().contains("drop_d"));
    }

    @Test
    void savePreferences_storesFavoriteSongsWithArtist() {
        service.register("songuser", "pass");

        FavoriteSongPreference song = new FavoriteSongPreference("Enter Sandman", "standard", "Metallica");
        UserPreferences prefs = new UserPreferences(List.of(), List.of(song));
        service.savePreferences("songuser", prefs);

        UserPreferences loaded = service.getPreferences("songuser");
        assertEquals(1, loaded.getFavoriteSongs().size());
        assertEquals("Enter Sandman", loaded.getFavoriteSongs().get(0).getName());
        assertEquals("standard", loaded.getFavoriteSongs().get(0).getTuning());
        assertEquals("Metallica", loaded.getFavoriteSongs().get(0).getArtist());
    }

    @Test
    void savePreferences_storesFavoriteSongsWithoutArtist() {
        service.register("songuser2", "pass");

        FavoriteSongPreference song = new FavoriteSongPreference("My Song", "drop_d");
        UserPreferences prefs = new UserPreferences(List.of(), List.of(song));
        service.savePreferences("songuser2", prefs);

        UserPreferences loaded = service.getPreferences("songuser2");
        assertEquals(1, loaded.getFavoriteSongs().size());
        assertEquals("My Song", loaded.getFavoriteSongs().get(0).getName());
        assertNull(loaded.getFavoriteSongs().get(0).getArtist());
    }

    @Test
    void savePreferences_preservesArtistOnResubmitWithoutArtist() {
        service.register("preserve", "pass");

        // First save: include artist
        FavoriteSongPreference songWith = new FavoriteSongPreference("Blackbird", "standard", "The Beatles");
        service.savePreferences("preserve", new UserPreferences(List.of(), List.of(songWith)));

        // Second save: same song, no artist
        FavoriteSongPreference songWithout = new FavoriteSongPreference("Blackbird", "standard");
        service.savePreferences("preserve", new UserPreferences(List.of(), List.of(songWithout)));

        UserPreferences loaded = service.getPreferences("preserve");
        assertEquals(1, loaded.getFavoriteSongs().size());
        assertEquals("The Beatles", loaded.getFavoriteSongs().get(0).getArtist());
    }

    @Test
    void savePreferences_overwritesPreviousPreferences() {
        service.register("overwrite", "pass");

        service.savePreferences("overwrite", new UserPreferences(
            List.of("standard", "drop_d"),
            List.of()
        ));

        // Overwrite with different set
        service.savePreferences("overwrite", new UserPreferences(
            List.of("eb_standard"),
            List.of()
        ));

        UserPreferences loaded = service.getPreferences("overwrite");
        assertEquals(1, loaded.getPreferredTuningKeys().size());
        assertEquals("eb_standard", loaded.getPreferredTuningKeys().get(0));
    }

    @Test
    void savePreferences_autoCreatesUser_whenNotFound() {
        // Don't register — savePreferences should auto-create
        UserPreferences prefs = new UserPreferences(
            List.of("standard"),
            List.of()
        );
        service.savePreferences("autouser", prefs);

        UserPreferences loaded = service.getPreferences("autouser");
        assertEquals(1, loaded.getPreferredTuningKeys().size());
    }

    @Test
    void savePreferences_skipsNullTuningKeys() {
        service.register("nullkey", "pass");

        UserPreferences prefs = new UserPreferences();
        prefs.setPreferredTuningKeys(null);
        prefs.setFavoriteSongs(null);

        // Should not throw
        assertDoesNotThrow(() -> service.savePreferences("nullkey", prefs));
    }

    @Test
    void savePreferences_skipsEmptySongNames() {
        service.register("emptysong", "pass");

        FavoriteSongPreference empty = new FavoriteSongPreference("", "standard");
        UserPreferences prefs = new UserPreferences(List.of(), List.of(empty));
        service.savePreferences("emptysong", prefs);

        UserPreferences loaded = service.getPreferences("emptysong");
        assertTrue(loaded.getFavoriteSongs().isEmpty());
    }

    @Test
    void savePreferences_skipsNullSongNames() {
        service.register("nullsong", "pass");

        FavoriteSongPreference nullName = new FavoriteSongPreference(null, "standard");
        UserPreferences prefs = new UserPreferences(List.of(), List.of(nullName));
        service.savePreferences("nullsong", prefs);

        UserPreferences loaded = service.getPreferences("nullsong");
        assertTrue(loaded.getFavoriteSongs().isEmpty());
    }

    @Test
    void savePreferences_truncatesLongSongTitle() {
        service.register("longname", "pass");

        String longTitle = "A".repeat(150);
        FavoriteSongPreference song = new FavoriteSongPreference(longTitle, "standard");
        UserPreferences prefs = new UserPreferences(List.of(), List.of(song));
        service.savePreferences("longname", prefs);

        UserPreferences loaded = service.getPreferences("longname");
        assertEquals(1, loaded.getFavoriteSongs().size());
        assertEquals(100, loaded.getFavoriteSongs().get(0).getName().length());
    }

    @Test
    void savePreferences_multipleSongsWithDifferentArtists() {
        service.register("multi", "pass");

        List<FavoriteSongPreference> songs = List.of(
            new FavoriteSongPreference("Song A", "standard", "Artist 1"),
            new FavoriteSongPreference("Song B", "drop_d", "Artist 2")
        );
        service.savePreferences("multi", new UserPreferences(List.of(), songs));

        UserPreferences loaded = service.getPreferences("multi");
        assertEquals(2, loaded.getFavoriteSongs().size());
    }
}
