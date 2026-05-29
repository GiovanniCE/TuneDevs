// Custom tuning builder for creating and deleting user-owned tunings; it provides aPI create/delete requests and callbacks that update the tuning catalog.

import React, { useMemo, useState } from "react";

export default function CustomTuningPage({
  apiUrl,
  username,
  noteOptions,
  tuningLibrary,
  onCreated,
  onDeleted,
  onBack,
}) {
  // These defaults seed the form when switching instrument type or adding
  // string rows, while the actual frequencies still come from noteOptions.
  const DEFAULT_NOTES = {
    guitar: ["E2", "A2", "D3", "G3", "B3", "E4", "A4", "D5"],
    bass: ["E1", "A1", "D2", "G2", "C3", "F3", "A#3", "D#4"],
  };
  const [displayName, setDisplayName] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [instrument, setInstrument] = useState("guitar");
  const [stringCount, setStringCount] = useState(6);
  const [stringNotes, setStringNotes] = useState(() => ({
    1: DEFAULT_NOTES.guitar[0],
    2: DEFAULT_NOTES.guitar[1],
    3: DEFAULT_NOTES.guitar[2],
    4: DEFAULT_NOTES.guitar[3],
    5: DEFAULT_NOTES.guitar[4],
    6: DEFAULT_NOTES.guitar[5],
  }));

  // Map notes to frequencies for the small readout beside each string select.
  const noteFreqMap = useMemo(() => {
    const map = new Map();
    for (const option of noteOptions) {
      map.set(option.note, option.freq);
    }
    return map;
  }, [noteOptions]);

  const customTunings = useMemo(
    // The catalog contains both standard and custom tunings; this page only
    // lists user-owned custom tunings for deletion.
    () => Object.entries(tuningLibrary)
      .filter(([, tuning]) => Boolean(tuning?.custom))
      .map(([key, tuning]) => ({ key, name: tuning.name }))
      .sort((a, b) => a.name.localeCompare(b.name)),
    [tuningLibrary]
  );

  const handleChangeString = (stringId, note) => {
    setStringNotes((prev) => ({ ...prev, [stringId]: note }));
  };

  const defaultNoteFor = (nextInstrument, stringId) =>
    DEFAULT_NOTES[nextInstrument][stringId - 1] ?? DEFAULT_NOTES.guitar[stringId - 1] ?? "E2";

  const handleInstrumentChange = (nextInstrument) => {
    setInstrument(nextInstrument);
    // Guitar and bass get sensible string counts by default, but the user can
    // still override the count afterward for extended-range instruments.
    setStringCount(nextInstrument === "bass" ? 4 : 6);
    setStringNotes((prev) => {
      const next = { ...prev };
      const targetCount = nextInstrument === "bass" ? 4 : 6;
      for (let id = 1; id <= targetCount; id += 1) {
        next[id] = defaultNoteFor(nextInstrument, id);
      }
      return next;
    });
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    const cleaned = displayName.trim();
    if (!cleaned) return;

    const payload = {
      displayName: cleaned,
      instrument,
      // The backend expects one entry per string number, using note names
      // rather than frequencies so it can validate and compute Hz itself.
      strings: Array.from({ length: stringCount }, (_, i) => i + 1).map((id) => ({
        stringNumber: id,
        noteName: stringNotes[id] || defaultNoteFor(instrument, id),
      })),
    };

    setIsSaving(true);
    try {
      const response = await fetch(`${apiUrl}/api/tunings/${encodeURIComponent(username)}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Failed to create custom tuning (${response.status})`);
      }

      const created = await response.json();
      // Bubble the created tuning back up so App can merge it into the catalog
      // without forcing a full page refresh.
      onCreated(created);
      setDisplayName("");
    } catch (error) {
      console.error("Could not create custom tuning:", error);
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (tuningKey, tuningName) => {
    // This confirmation is intentionally short because FavoritesPage has the
    // more detailed warning when songs/preferred tunings may also be removed.
    const confirmed = window.confirm(`Delete custom tuning "${tuningName}"?`);
    if (!confirmed) return;

    try {
      const response = await fetch(
        `${apiUrl}/api/tunings/${encodeURIComponent(username)}/key/${encodeURIComponent(tuningKey)}`,
        { method: "DELETE" }
      );

      if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Failed to delete custom tuning (${response.status})`);
      }

      onDeleted(tuningKey);
    } catch (error) {
      console.error("Could not delete custom tuning:", error);
    }
  };

  return (
    <div className="favorites-page">
      <div className="favorites-card">
        <header className="favorites-header">
          <h1>Custom Tuning Builder</h1>
          <p>Choose your instrument string count and notes, then save your tuning profile.</p>
        </header>

        <section className="favorites-section">
          <form onSubmit={handleCreate}>
            <div className="favorites-controls inline-form">
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Custom tuning name"
                maxLength={100}
                required
              />
            </div>

            <div className="favorites-controls inline-form">
              <label htmlFor="instrument-type">Instrument</label>
              <select
                id="instrument-type"
                value={instrument}
                onChange={(e) => handleInstrumentChange(e.target.value)}
              >
                <option value="guitar">Guitar</option>
                <option value="bass">Bass</option>
              </select>
            </div>

            <div className="favorites-controls inline-form">
              <label htmlFor="string-count">Number of strings</label>
              <select
                id="string-count"
                value={stringCount}
                onChange={(e) => setStringCount(Number(e.target.value))}
              >
                {[4, 5, 6, 7, 8].map((n) => (
                  <option key={n} value={n}>{n} strings{n === 4 ? " (bass)" : n === 6 ? " (guitar)" : ""}</option>
                ))}
              </select>
            </div>

            {Array.from({ length: stringCount }, (_, i) => i + 1).map((id) => (
              <div className="favorites-controls inline-form" key={`custom-string-${id}`}>
                <label htmlFor={`string-${id}`}>String {id}</label>
                <select
                  id={`string-${id}`}
                  value={stringNotes[id] || defaultNoteFor(instrument, id)}
                  onChange={(e) => handleChangeString(id, e.target.value)}
                >
                  {noteOptions.map((option) => (
                    <option key={`${id}-${option.note}`} value={option.note}>
                      {option.note} ({Number(option.freq).toFixed(2)} Hz)
                    </option>
                  ))}
                </select>
                <span>{Number(noteFreqMap.get(stringNotes[id] || defaultNoteFor(instrument, id)) ?? 0).toFixed(2)} Hz</span>
              </div>
            ))}

            <div className="favorites-controls">
              <button type="submit" disabled={isSaving}>
                {isSaving ? "Saving..." : "Save Custom Tuning"}
              </button>
              <button type="button" className="btn btn-secondary" onClick={onBack}>
                Back
              </button>
            </div>
          </form>
        </section>

        <section className="favorites-section">
          <h2>My Custom Tunings</h2>
          <ul className="favorites-list">
            {customTunings.length === 0 ? (
              <li className="favorites-empty">No custom tunings created yet.</li>
            ) : (
              customTunings.map((tuning) => (
                <li key={tuning.key}>
                  <span>{tuning.name}</span>
                  <button
                    type="button"
                    className="remove-item-btn"
                    onClick={() => handleDelete(tuning.key, tuning.name)}
                  >
                    Delete
                  </button>
                </li>
              ))
            )}
          </ul>
        </section>
      </div>
    </div>
  );
}
