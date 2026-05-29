// Shared frontend constants and helper functions for tunings, instruments, songs, and status labels; it provides normalized frontend data and display-friendly labels/statuses.

export const FALLBACK_TUNING_LIBRARY = {
  standard: {
    name: "Standard (EADGBE)",
    instrument: "guitar",
    strings: [
      { id: 1, label: "E", note: "E2", freq: 82.41 },
      { id: 2, label: "A", note: "A2", freq: 110.0 },
      { id: 3, label: "D", note: "D3", freq: 146.83 },
      { id: 4, label: "G", note: "G3", freq: 196.0 },
      { id: 5, label: "B", note: "B3", freq: 246.94 },
      { id: 6, label: "e", note: "E4", freq: 329.63 },
    ],
  },
  eb_standard: {
    name: "Eb Standard (Eb Ab Db Gb Bb Eb)",
    instrument: "guitar",
    strings: [
      { id: 1, label: "Eb", note: "Eb2", freq: 77.78 },
      { id: 2, label: "Ab", note: "Ab2", freq: 103.83 },
      { id: 3, label: "Db", note: "Db3", freq: 138.59 },
      { id: 4, label: "Gb", note: "Gb3", freq: 185.0 },
      { id: 5, label: "Bb", note: "Bb3", freq: 233.08 },
      { id: 6, label: "eb", note: "Eb4", freq: 311.13 },
    ],
  },
  drop_d: {
    name: "Drop D (DADGBE)",
    instrument: "guitar",
    strings: [
      { id: 1, label: "D", note: "D2", freq: 73.42 },
      { id: 2, label: "A", note: "A2", freq: 110.0 },
      { id: 3, label: "D", note: "D3", freq: 146.83 },
      { id: 4, label: "G", note: "G3", freq: 196.0 },
      { id: 5, label: "B", note: "B3", freq: 246.94 },
      { id: 6, label: "e", note: "E4", freq: 329.63 },
    ],
  },
  drop_c_sharp: {
    name: "Drop C# (C# G# C# F# A# D#)",
    instrument: "guitar",
    strings: [
      { id: 1, label: "C#", note: "C#2", freq: 69.3 },
      { id: 2, label: "G#", note: "G#2", freq: 103.83 },
      { id: 3, label: "C#", note: "C#3", freq: 138.59 },
      { id: 4, label: "F#", note: "F#3", freq: 185.0 },
      { id: 5, label: "A#", note: "A#3", freq: 233.08 },
      { id: 6, label: "d#", note: "D#4", freq: 311.13 },
    ],
  },
  drop_c: {
    name: "Drop C (C G C F A D)",
    instrument: "guitar",
    strings: [
      { id: 1, label: "C", note: "C2", freq: 65.41 },
      { id: 2, label: "G", note: "G2", freq: 98.0 },
      { id: 3, label: "C", note: "C3", freq: 130.81 },
      { id: 4, label: "F", note: "F3", freq: 174.61 },
      { id: 5, label: "A", note: "A3", freq: 220.0 },
      { id: 6, label: "d", note: "D4", freq: 293.66 },
    ],
  },
  drop_g: {
    name: "Drop G (G D G C E A)",
    instrument: "guitar",
    strings: [
      { id: 1, label: "G", note: "G1", freq: 49.0 },
      { id: 2, label: "D", note: "D2", freq: 73.42 },
      { id: 3, label: "G", note: "G2", freq: 98.0 },
      { id: 4, label: "C", note: "C3", freq: 130.81 },
      { id: 5, label: "E", note: "E3", freq: 164.81 },
      { id: 6, label: "a", note: "A3", freq: 220.0 },
    ],
  },
  bass_standard: {
    name: "Bass Standard (EADG)",
    instrument: "bass",
    strings: [
      { id: 1, label: "E", note: "E1", freq: 41.2 },
      { id: 2, label: "A", note: "A1", freq: 55.0 },
      { id: 3, label: "D", note: "D2", freq: 73.42 },
      { id: 4, label: "G", note: "G2", freq: 98.0 },
    ],
  },
  bass_drop_d: {
    name: "Bass Drop D (DADG)",
    instrument: "bass",
    strings: [
      { id: 1, label: "D", note: "D1", freq: 36.71 },
      { id: 2, label: "A", note: "A1", freq: 55.0 },
      { id: 3, label: "D", note: "D2", freq: 73.42 },
      { id: 4, label: "G", note: "G2", freq: 98.0 },
    ],
  },
  bass_eb_standard: {
    name: "Bass Eb Standard (Eb Ab Db Gb)",
    instrument: "bass",
    strings: [
      { id: 1, label: "Eb", note: "Eb1", freq: 38.89 },
      { id: 2, label: "Ab", note: "Ab1", freq: 51.91 },
      { id: 3, label: "Db", note: "Db2", freq: 69.3 },
      { id: 4, label: "Gb", note: "Gb2", freq: 92.5 },
    ],
  },
};

const HEADSTOCK_STRING_LINES_6 = [
  { id: 3, x1: 34, y1: 22, x2: 47, y2: 99 },
  { id: 2, x1: 35, y1: 45, x2: 42, y2: 99 },
  { id: 1, x1: 36, y1: 68, x2: 36, y2: 99 },
  { id: 4, x1: 65, y1: 22, x2: 53, y2: 99 },
  { id: 5, x1: 65, y1: 45, x2: 58, y2: 99 },
  { id: 6, x1: 65, y1: 68, x2: 63, y2: 99 },
];

const HEADSTOCK_STRING_LINES_4 = [
  { id: 2, x1: 34, y1: 35, x2: 43, y2: 99 },
  { id: 1, x1: 36, y1: 65, x2: 36, y2: 99 },
  { id: 3, x1: 65, y1: 35, x2: 57, y2: 99 },
  { id: 4, x1: 65, y1: 65, x2: 63, y2: 99 },
];

const INITIAL_TUNING_KEYS = [
  "standard",
  "eb_standard",
  "drop_d",
  "bass_standard",
  "bass_drop_d",
  "bass_eb_standard",
];
const UNKNOWN_ARTIST = "Unknown";

// Bundled options keep the UI usable when the backend catalog has not loaded
// yet, then the API response can replace or extend them.
export const INSTRUMENTS = [
  { value: "guitar", label: "Guitar (6-string)", icon: "🎸" },
  { value: "bass", label: "Bass (4-string)", icon: "🎸" },
  { value: "all", label: "All Instruments", icon: "🎵" },
];

export function getHeadstockLines(stringCount) {
  // Bass headstocks have a different peg/string layout than 6-string guitars.
  return stringCount <= 4 ? HEADSTOCK_STRING_LINES_4 : HEADSTOCK_STRING_LINES_6;
}

export function getColumnIds(stringCount) {
  if (stringCount <= 4) {
    return { left: [2, 1], right: [3, 4] };
  }
  return { left: [3, 2, 1], right: [4, 5, 6] };
}

export function instrumentForKey(key) {
  if (key.startsWith("bass_")) return "bass";
  return "guitar";
}

export function instrumentForTuning(key, tuning) {
  // Prefer explicit metadata, but infer from key/string count for older rows
  // and fallback constants that may not carry an instrument field.
  if (typeof key === "string" && key.startsWith("bass_")) {
    return "bass";
  }
  const normalized = String(tuning?.instrument ?? "").trim().toLowerCase();
  if (normalized === "guitar" || normalized === "bass") {
    return normalized;
  }
  if (Array.isArray(tuning?.strings) && tuning.strings.length > 0) {
    return tuning.strings.length <= 4 ? "bass" : "guitar";
  }
  return instrumentForKey(key);
}

export function tuningArrayToLibrary(tunings) {
  if (!Array.isArray(tunings)) return {};

  const out = {};
  for (const tuning of tunings) {
    if (!tuning || !tuning.key || !Array.isArray(tuning.strings)) continue;

    // Normalize backend DTOs into the shape expected by tuner components.
    out[tuning.key] = {
      name: tuning.name ?? tuning.key,
      instrument: instrumentForTuning(tuning.key, tuning),
      custom: Boolean(tuning.custom),
      strings: tuning.strings
        .map((s) => ({
          id: Number(s.id),
          label: String(s.label ?? s.note ?? ""),
          note: String(s.note ?? ""),
          freq: Number(s.freq ?? 0),
        }))
        .filter((s) => Number.isFinite(s.id) && s.id >= 1 && s.id <= 8)
        .sort((a, b) => a.id - b.id),
    };
  }

  return out;
}

export function getDefaultPreferredKeys(library) {
  // Start users with a compact useful set instead of every tuning in the app.
  const keys = Object.keys(library);
  const preferred = INITIAL_TUNING_KEYS.filter((k) => keys.includes(k));
  if (preferred.length > 0) return preferred;
  return keys.slice(0, 3);
}

export const FALLBACK_NOTE_OPTIONS = Object.values(FALLBACK_TUNING_LIBRARY)
  .flatMap((t) => t.strings.map((s) => ({ note: s.note, freq: s.freq })))
  .filter((v, i, arr) => arr.findIndex((x) => x.note === v.note) === i);

export function clamp(n, lo, hi) {
  return Math.max(lo, Math.min(hi, n));
}

export function normalizeArtistName(value) {
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  // The database uses "Unknown" as a placeholder, but the UI treats that as
  // absent so labels do not show noisy fake artist names.
  if (!trimmed || trimmed.toLowerCase() === UNKNOWN_ARTIST.toLowerCase()) {
    return undefined;
  }
  return trimmed;
}

export function normalizeFavoriteSong(song) {
  if (!song || typeof song.name !== "string" || typeof song.tuning !== "string") {
    return null;
  }

  const name = song.name.trim();
  const tuning = song.tuning.trim();
  if (!name || !tuning) return null;

  const artist = normalizeArtistName(song.artist);
  return artist ? { name, tuning, artist } : { name, tuning };
}

export function favoriteSongKey(song) {
  const normalized = normalizeFavoriteSong(song);
  if (!normalized) return "";

  // Composite keys make duplicate checks stable without requiring database ids
  // in the frontend preference state.
  return [
    normalized.name.toLowerCase(),
    (normalized.artist ?? "").toLowerCase(),
    normalized.tuning.toLowerCase(),
  ].join("::");
}

export function favoriteSongLabel(song) {
  const artist = normalizeArtistName(song?.artist);
  return artist ? `${artist} - ${song.name}` : song.name;
}

export function statusFromCents(cents, deadzone = 5) {
  if (Math.abs(cents) <= deadzone) return "in_tune";
  return cents < 0 ? "flat" : "sharp";
}

export function prettyStatus(status) {
  if (status === "in_tune") return "In Tune";
  if (status === "flat") return "Flat";
  if (status === "sharp") return "Sharp";
  return "Unknown";
}
