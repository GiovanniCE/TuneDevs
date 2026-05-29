// Owns tuning catalog persistence, standard seeding, and custom tuning CRUD; it provides validated tuning definitions and database updates.

package com.autotuner.backend.service;

import com.autotuner.backend.model.CreateCustomTuningRequest;
import com.autotuner.backend.model.TuningDefinitionDto;
import com.autotuner.backend.model.TuningStringDto;
import com.autotuner.backend.model.ValidNoteOptionDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class TuningCatalogService {

    private final JdbcTemplate jdbcTemplate;

    public TuningCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
        seedStandardTunings();
    }

    public List<ValidNoteOptionDto> getValidGuitarNotes() {
        List<ValidNoteOptionDto> options = new ArrayList<>();
        // Bass + Guitar range: B0 up to A5.
        for (int midi = 23; midi <= 81; midi++) {
            String note = midiToSharpNote(midi);
            options.add(new ValidNoteOptionDto(note, round2(midiToFrequency(midi))));
        }
        return options;
    }

    public List<TuningDefinitionDto> getStandardTunings() {
        return fetchTunings(null);
    }

    public List<TuningDefinitionDto> getTuningsForUser(String username) {
        // Users see the shared catalog first, followed by their custom tunings.
        List<TuningDefinitionDto> standard = fetchTunings(null);
        List<TuningDefinitionDto> custom = fetchTunings(username == null ? null : username.trim());
        List<TuningDefinitionDto> merged = new ArrayList<>(standard.size() + custom.size());
        merged.addAll(standard);
        merged.addAll(custom);
        return merged;
    }

    public Optional<TuningDefinitionDto> getTuningForUser(String username, String tuningKey) {
        String normalizedKey = tuningKey == null ? "" : tuningKey.trim();
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("Tuning key is required");
        }

        TuningDefinitionDto standard = fetchTuningByKey(normalizedKey, null);
        if (standard != null) {
            return Optional.of(standard);
        }

        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }

        // Standard tunings are globally addressable; custom tunings require a
        // user owner so one user's custom key cannot leak into another account.
        return Optional.ofNullable(fetchTuningByKey(normalizedKey, normalizedUsername));
    }

    @Transactional
    public TuningDefinitionDto createCustomTuning(String username, CreateCustomTuningRequest request) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (request == null || request.displayName() == null || request.displayName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tuning name is required");
        }

        if (request.strings() == null || request.strings().size() < 4 || request.strings().size() > 8) {
            throw new IllegalArgumentException("Between 4 and 8 strings are required");
        }

        int stringCount = request.strings().size();
        String instrument = normalizeInstrument(request.instrument(), stringCount);
        Map<Integer, CreateCustomTuningRequest.CustomTuningStringInput> byString = new LinkedHashMap<>();
        for (CreateCustomTuningRequest.CustomTuningStringInput s : request.strings()) {
            if (s == null || s.stringNumber() < 1 || s.stringNumber() > stringCount || s.noteName() == null) {
                throw new IllegalArgumentException("Invalid string input");
            }
            byString.put(s.stringNumber(), s);
        }
        if (byString.size() != stringCount) {
            throw new IllegalArgumentException("String numbers 1 through " + stringCount + " must each be present exactly once");
        }

        // Slug the display name into a stable key, then prefix by username to
        // avoid collisions between different users' custom tunings.
        String keyBase = request.displayName().trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (keyBase.isEmpty()) {
            keyBase = "custom_tuning";
        }
        String tuningKey = nextAvailableCustomKey(normalizedUsername, keyBase);

        jdbcTemplate.update(
            "INSERT INTO tuning_definitions (tuning_key, display_name, instrument, owner_username) VALUES (?, ?, ?, ?)",
            tuningKey,
            request.displayName().trim(),
            instrument,
            normalizedUsername
        );

        Integer tuningId = jdbcTemplate.query(
            "SELECT tuning_id FROM tuning_definitions WHERE tuning_key = ?",
            rs -> rs.next() ? rs.getInt("tuning_id") : null,
            tuningKey
        );

        if (tuningId == null) {
            throw new IllegalStateException("Failed to create custom tuning");
        }

        List<TuningStringDto> createdStrings = new ArrayList<>();
        for (int i = 1; i <= stringCount; i++) {
            CreateCustomTuningRequest.CustomTuningStringInput s = byString.get(i);
            String note = s.noteName().trim();
            // Store rounded frequencies so API output matches the fallback
            // frontend catalog and stays readable in the database.
            double freq = round2(noteToFrequency(note));

            jdbcTemplate.update(
                "INSERT INTO tuning_definition_strings (tuning_id, string_number, note_name, frequency_hz) VALUES (?, ?, ?, ?)",
                tuningId,
                i,
                note,
                freq
            );

            createdStrings.add(new TuningStringDto(i, note, note, freq));
        }

        return new TuningDefinitionDto(tuningKey, request.displayName().trim(), instrument, true, createdStrings);
    }

    @Transactional
    public boolean deleteCustomTuning(String username, String tuningKey) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedKey = tuningKey == null ? "" : tuningKey.trim();

        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("Tuning key is required");
        }

        Integer standardExists = jdbcTemplate.query(
            "SELECT tuning_id FROM tuning_definitions WHERE tuning_key = ? AND owner_username IS NULL",
            rs -> rs.next() ? rs.getInt("tuning_id") : null,
            normalizedKey
        );
        if (standardExists != null) {
            // Only user-owned custom tunings are removable.
            throw new IllegalArgumentException("Standard tunings cannot be deleted");
        }

        int deleted = jdbcTemplate.update(
            "DELETE FROM tuning_definitions WHERE tuning_key = ? AND owner_username = ?",
            normalizedKey,
            normalizedUsername
        );

        return deleted > 0;
    }

    private List<TuningDefinitionDto> fetchTunings(String ownerUsername) {
        List<Map<String, Object>> tuningRows;
        if (ownerUsername == null) {
            // owner_username NULL marks built-in standard tunings.
            tuningRows = jdbcTemplate.queryForList(
                "SELECT tuning_id, tuning_key, display_name, instrument, owner_username FROM tuning_definitions WHERE owner_username IS NULL ORDER BY tuning_id"
            );
        } else {
            tuningRows = jdbcTemplate.queryForList(
                "SELECT tuning_id, tuning_key, display_name, instrument, owner_username FROM tuning_definitions WHERE owner_username = ? ORDER BY tuning_id",
                ownerUsername
            );
        }

        List<TuningDefinitionDto> out = new ArrayList<>();
        for (Map<String, Object> row : tuningRows) {
            int tuningId = ((Number) row.get("tuning_id")).intValue();
            String key = Objects.toString(row.get("tuning_key"), "");
            String name = Objects.toString(row.get("display_name"), key);
            List<TuningStringDto> strings = stringsForTuningId(tuningId);
            // Older rows may not have instrument populated, so infer safely.
            String instrument = normalizeFetchedInstrument(row.get("instrument"), key, strings);
            boolean custom = row.get("owner_username") != null;

            strings.sort(Comparator.comparingInt(TuningStringDto::id));
            out.add(new TuningDefinitionDto(key, name, instrument, custom, strings));
        }

        return out;
    }

    private TuningDefinitionDto fetchTuningByKey(String tuningKey, String ownerUsername) {
        List<Map<String, Object>> rows;
        if (ownerUsername == null) {
            rows = jdbcTemplate.queryForList(
                "SELECT tuning_id, tuning_key, display_name, instrument, owner_username " +
                    "FROM tuning_definitions WHERE tuning_key = ? AND owner_username IS NULL",
                tuningKey
            );
        } else {
            rows = jdbcTemplate.queryForList(
                "SELECT tuning_id, tuning_key, display_name, instrument, owner_username " +
                    "FROM tuning_definitions WHERE tuning_key = ? AND owner_username = ?",
                tuningKey,
                ownerUsername
            );
        }

        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.get(0);
        int tuningId = ((Number) row.get("tuning_id")).intValue();
        String key = Objects.toString(row.get("tuning_key"), "");
        String name = Objects.toString(row.get("display_name"), key);
        List<TuningStringDto> strings = stringsForTuningId(tuningId);
        String instrument = normalizeFetchedInstrument(row.get("instrument"), key, strings);
        boolean custom = row.get("owner_username") != null;

        strings.sort(Comparator.comparingInt(TuningStringDto::id));
        return new TuningDefinitionDto(key, name, instrument, custom, strings);
    }

    private void ensureSchema() {
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS tuning_definitions (" +
                    "tuning_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "tuning_key VARCHAR(64) NOT NULL UNIQUE," +
                    "display_name VARCHAR(120) NOT NULL," +
                    "instrument VARCHAR(16) NOT NULL DEFAULT 'guitar'," +
                    "owner_username VARCHAR(50) NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            try {
                jdbcTemplate.execute(
                    "ALTER TABLE tuning_definitions ADD COLUMN instrument VARCHAR(16) NOT NULL DEFAULT 'guitar'"
                );
            } catch (Exception ignored) {
                // Column already exists on newer schemas.
            }

            jdbcTemplate.update(
                "UPDATE tuning_definitions SET instrument = 'bass' " +
                    "WHERE tuning_key IN ('bass_standard', 'bass_drop_d', 'bass_eb_standard')"
            );

            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS tuning_definition_strings (" +
                    "tuning_id INT NOT NULL," +
                    "string_number INT NOT NULL," +
                    "note_name VARCHAR(8) NOT NULL," +
                    "frequency_hz DECIMAL(7,3) NOT NULL," +
                    "PRIMARY KEY (tuning_id, string_number)," +
                    "FOREIGN KEY (tuning_id) REFERENCES tuning_definitions(tuning_id) ON DELETE CASCADE" +
                ")"
            );
        } catch (Exception ex) {
            // Tables likely already exist from init.sql — safe to continue.
            org.slf4j.LoggerFactory.getLogger(TuningCatalogService.class)
                .warn("ensureSchema skipped: {}", ex.getMessage());
        }
    }

    private String nextAvailableCustomKey(String username, String keyBase) {
        String prefix = username.toLowerCase(Locale.ROOT) + "_";
        String candidate = prefix + keyBase;
        int suffix = 2;

        // Append _2, _3, ... until the generated key is unique.
        while (Boolean.TRUE.equals(jdbcTemplate.query(
            "SELECT EXISTS(SELECT 1 FROM tuning_definitions WHERE tuning_key = ?) AS present",
            rs -> rs.next() && rs.getInt("present") == 1,
            candidate
        ))) {
            candidate = prefix + keyBase + "_" + suffix;
            suffix++;
        }

        return candidate;
    }

    private void seedStandardTunings() {
        seedStandard(
            "standard",
            "Standard (EADGBE)",
            "guitar",
            List.of("E2", "A2", "D3", "G3", "B3", "E4")
        );
        seedStandard(
            "eb_standard",
            "Eb Standard (Eb Ab Db Gb Bb Eb)",
            "guitar",
            List.of("Eb2", "Ab2", "Db3", "Gb3", "Bb3", "Eb4")
        );
        seedStandard(
            "drop_d",
            "Drop D (DADGBE)",
            "guitar",
            List.of("D2", "A2", "D3", "G3", "B3", "E4")
        );
        seedStandard(
            "drop_c_sharp",
            "Drop C# (C# G# C# F# A# D#)",
            "guitar",
            List.of("C#2", "G#2", "C#3", "F#3", "A#3", "D#4")
        );
        seedStandard(
            "drop_c",
            "Drop C (C G C F A D)",
            "guitar",
            List.of("C2", "G2", "C3", "F3", "A3", "D4")
        );
        seedStandard(
            "drop_g",
            "Drop G (G D G C E A)",
            "guitar",
            List.of("G1", "D2", "G2", "C3", "E3", "A3")
        );

        // Bass guitar tunings (4-string)
        seedStandard(
            "bass_standard",
            "Bass Standard (EADG)",
            "bass",
            List.of("E1", "A1", "D2", "G2")
        );
        seedStandard(
            "bass_drop_d",
            "Bass Drop D (DADG)",
            "bass",
            List.of("D1", "A1", "D2", "G2")
        );
        seedStandard(
            "bass_eb_standard",
            "Bass Eb Standard (Eb Ab Db Gb)",
            "bass",
            List.of("Eb1", "Ab1", "Db2", "Gb2")
        );
    }

    private void seedStandard(String key, String name, String instrument, List<String> notes) {
        // Seeding is idempotent so app startup can safely run it every time.
        Boolean exists = jdbcTemplate.query(
            "SELECT EXISTS(SELECT 1 FROM tuning_definitions WHERE tuning_key = ?) AS present",
            rs -> rs.next() && rs.getInt("present") == 1,
            key
        );
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        jdbcTemplate.update(
            "INSERT INTO tuning_definitions (tuning_key, display_name, instrument, owner_username) VALUES (?, ?, ?, NULL)",
            key,
            name,
            instrument
        );

        Integer id = jdbcTemplate.query(
            "SELECT tuning_id FROM tuning_definitions WHERE tuning_key = ?",
            rs -> rs.next() ? rs.getInt("tuning_id") : null,
            key
        );
        if (id == null) {
            return;
        }

        for (int i = 0; i < notes.size(); i++) {
            String note = notes.get(i);
            jdbcTemplate.update(
                "INSERT INTO tuning_definition_strings (tuning_id, string_number, note_name, frequency_hz) VALUES (?, ?, ?, ?)",
                id,
                i + 1,
                note,
                round2(noteToFrequency(note))
            );
        }
    }

    private double noteToFrequency(String rawNote) {
        int midi = noteToMidi(rawNote);
        if (midi < 23 || midi > 88) {
            throw new IllegalArgumentException("Note out of supported range: " + rawNote);
        }
        return midiToFrequency(midi);
    }

    private int noteToMidi(String rawNote) {
        String note = rawNote == null ? "" : rawNote.trim();
        if (!note.matches("^[A-Ga-g](#|b)?-?[0-9]+$")) {
            throw new IllegalArgumentException("Invalid note format: " + rawNote);
        }

        // Parse note names like C#4 or Eb2 into MIDI numbers for frequency math.
        char letter = Character.toUpperCase(note.charAt(0));
        int idx = 1;
        String accidental = "";
        if (idx < note.length() && (note.charAt(idx) == '#' || note.charAt(idx) == 'b')) {
            accidental = String.valueOf(note.charAt(idx));
            idx++;
        }
        int octave = Integer.parseInt(note.substring(idx));

        int semitoneFromC = switch (letter) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default -> throw new IllegalArgumentException("Invalid note: " + rawNote);
        };

        if ("#".equals(accidental)) semitoneFromC += 1;
        if ("b".equals(accidental)) semitoneFromC -= 1;

        return (octave + 1) * 12 + semitoneFromC;
    }

    private String midiToSharpNote(int midi) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int semitone = Math.floorMod(midi, 12);
        int octave = (midi / 12) - 1;
        return names[semitone] + octave;
    }

    private double midiToFrequency(int midi) {
        // MIDI note 69 is A4 = 440Hz.
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private List<TuningStringDto> stringsForTuningId(int tuningId) {
        return jdbcTemplate.query(
            "SELECT string_number, note_name, frequency_hz FROM tuning_definition_strings WHERE tuning_id = ? ORDER BY string_number",
            (rs, rowNum) -> {
                int id = rs.getInt("string_number");
                String note = rs.getString("note_name");
                double freq = rs.getDouble("frequency_hz");
                return new TuningStringDto(id, note, note, freq);
            },
            tuningId
        );
    }

    private String normalizeFetchedInstrument(Object storedInstrument, String tuningKey, List<TuningStringDto> strings) {
        // Prefer explicit/current data, then fall back to legacy naming and
        // string count heuristics.
        if (tuningKey != null && tuningKey.startsWith("bass_")) {
            return "bass";
        }
        if (storedInstrument != null) {
            String normalized = storedInstrument.toString().trim().toLowerCase(Locale.ROOT);
            if ("guitar".equals(normalized) || "bass".equals(normalized)) {
                return normalized;
            }
        }
        return strings.size() <= 4 ? "bass" : "guitar";
    }

    private String normalizeInstrument(String rawInstrument, int stringCount) {
        String normalized = rawInstrument == null ? "" : rawInstrument.trim().toLowerCase(Locale.ROOT);
        if ("guitar".equals(normalized) || "bass".equals(normalized)) {
            return normalized;
        }
        return stringCount <= 4 ? "bass" : "guitar";
    }
}
