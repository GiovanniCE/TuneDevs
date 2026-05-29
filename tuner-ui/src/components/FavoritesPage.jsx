// Preferences page for choosing favorite tunings and favorite songs; it provides updated preferred tunings/song lists and custom-tuning delete requests.

import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  favoriteSongKey,
  favoriteSongLabel,
  instrumentForTuning,
  normalizeArtistName,
  normalizeFavoriteSong,
} from "../appUtils.js";

export default function FavoritesPage({
  tuningLibrary,
  preferredTuningKeys,
  setPreferredTuningKeys,
  favoriteSongs,
  setFavoriteSongs,
  onOpenCustomTuning,
  apiUrl,
  username,
  onDeleteCustomTuning,
}) {
  const allTuningKeys = Object.keys(tuningLibrary);
  const tuningInstrument = useCallback(
    (key) => instrumentForTuning(key, tuningLibrary[key]),
    [tuningLibrary]
  );
  const [preferredInstrument, setPreferredInstrument] = useState("guitar");
  // Split tuning choices by instrument so guitar and bass preferences do not
  // get mixed in the add dropdown.
  const instrumentTuningKeys = useMemo(
    () => allTuningKeys.filter((key) => tuningInstrument(key) === preferredInstrument),
    [allTuningKeys, preferredInstrument, tuningInstrument]
  );
  const remainingKeys = useMemo(
    () => instrumentTuningKeys.filter((k) => !preferredTuningKeys.includes(k)),
    [instrumentTuningKeys, preferredTuningKeys]
  );
  const guitarPreferredTuningKeys = useMemo(
    () => preferredTuningKeys.filter((key) => tuningInstrument(key) === "guitar"),
    [preferredTuningKeys, tuningInstrument]
  );
  const bassPreferredTuningKeys = useMemo(
    () => preferredTuningKeys.filter((key) => tuningInstrument(key) === "bass"),
    [preferredTuningKeys, tuningInstrument]
  );
  const [keyToAdd, setKeyToAdd] = useState("");
  const [songName, setSongName] = useState("");
  const [artistName, setArtistName] = useState("");
  const [songInstrument, setSongInstrument] = useState("guitar");
  const [songTuningKey, setSongTuningKey] = useState("");
  const [editingArtistIndex, setEditingArtistIndex] = useState(null);
  const [editingArtistValue, setEditingArtistValue] = useState("");
  const songTuningKeys = useMemo(
    () => allTuningKeys.filter((key) => tuningInstrument(key) === songInstrument),
    [allTuningKeys, songInstrument, tuningInstrument]
  );
  const guitarFavoriteSongs = useMemo(
    () => favoriteSongs.filter((song) => tuningInstrument(song.tuning) === "guitar"),
    [favoriteSongs, tuningInstrument]
  );
  const bassFavoriteSongs = useMemo(
    () => favoriteSongs.filter((song) => tuningInstrument(song.tuning) === "bass"),
    [favoriteSongs, tuningInstrument]
  );

  useEffect(() => {
    // Keep the add-select pointed at an available option as preferences change.
    if (remainingKeys.length === 0) {
      setKeyToAdd("");
      return;
    }
    if (!remainingKeys.includes(keyToAdd)) setKeyToAdd(remainingKeys[0]);
  }, [remainingKeys, keyToAdd]);

  useEffect(() => {
    // When the user switches between guitar and bass songs, choose a valid
    // default tuning for the new instrument automatically.
    if (songTuningKeys.length === 0) {
      setSongTuningKey("");
      return;
    }
    if (!songTuningKeys.includes(songTuningKey)) setSongTuningKey(songTuningKeys[0]);
  }, [songTuningKey, songTuningKeys]);

  const addPreferredTuning = () => {
    if (!keyToAdd || preferredTuningKeys.includes(keyToAdd)) return;
    setPreferredTuningKeys((prev) => [...prev, keyToAdd]);
  };

  const removePreferredTuning = (key) => {
    if (preferredTuningKeys.length <= 1) return;
    setPreferredTuningKeys((prev) => prev.filter((k) => k !== key));
  };

  const saveArtistEdit = (index) => {
    const newArtist = normalizeArtistName(editingArtistValue);
    setFavoriteSongs((prev) =>
      prev.map((song, i) =>
        // Use undefined instead of "Unknown" so display labels stay clean.
        i === index
          ? { ...song, ...(newArtist ? { artist: newArtist } : { artist: undefined }) }
          : song
      )
    );
    setEditingArtistIndex(null);
    setEditingArtistValue("");
  };

  const cancelArtistEdit = () => {
    setEditingArtistIndex(null);
    setEditingArtistValue("");
  };

  const addFavoriteSong = () => {
    const nextSong = normalizeFavoriteSong({ name: songName, tuning: songTuningKey, artist: artistName });
    if (!nextSong) return;

    setFavoriteSongs((prev) => {
      // Song identity includes title, optional artist, and tuning so the same
      // song can be saved in different tunings when needed.
      if (prev.some((song) => favoriteSongKey(song) === favoriteSongKey(nextSong))) {
        return prev;
      }
      return [...prev, nextSong];
    });
    setSongName("");
    setArtistName("");
  };

  const removeFavoriteSong = (index) => {
    setFavoriteSongs((prev) => prev.filter((_, i) => i !== index));
  };

  const handleDeleteCustomTuning = async (tuningKey, tuningName) => {
    // Deleting a custom tuning also removes preferences and songs that depend
    // on it, so ask for confirmation before calling the backend.
    const confirmed = window.confirm(`Delete custom tuning "${tuningName}"? This will also remove it from your preferred tunings and any songs using it.`);
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

      onDeleteCustomTuning(tuningKey);
    } catch (error) {
      console.error("Could not delete custom tuning:", error);
    }
  };

  return (
    <div className="favorites-page">
      <div className="favorites-card">
        <header className="favorites-header">
          <h1>My Preferences</h1>
          <p>Save your preferred tunings and the songs you play in each.</p>
        </header>

        <section className="favorites-section">
          <h2>Preferred Tunings</h2>
          <div className="favorites-controls">
            <select value={preferredInstrument} onChange={(e) => setPreferredInstrument(e.target.value)}>
              <option value="guitar">Guitar</option>
              <option value="bass">Bass</option>
            </select>
            <select
              value={keyToAdd}
              onChange={(e) => setKeyToAdd(e.target.value)}
              disabled={remainingKeys.length === 0}
            >
              {remainingKeys.length === 0 ? (
                <option value="">All tunings added</option>
              ) : (
                remainingKeys.map((key) => (
                  <option key={key} value={key}>{tuningLibrary[key].name}</option>
                ))
              )}
            </select>
            <button type="button" onClick={addPreferredTuning} disabled={remainingKeys.length === 0}>
              Add
            </button>
          </div>

          <div className="favorites-controls">
            <button type="button" className="custom-tuning-btn" onClick={onOpenCustomTuning}>
              Open Custom Tuning Builder
            </button>
          </div>

          <h3 className="favorites-subtitle">Guitar Tunings</h3>
          <ul className="favorites-list">
            {guitarPreferredTuningKeys.length === 0 ? (
              <li className="favorites-empty">No preferred guitar tunings yet.</li>
            ) : (
              guitarPreferredTuningKeys.filter((key) => Boolean(tuningLibrary[key])).map((key) => (
                <li key={key}>
                  <span>{tuningLibrary[key].name}{tuningLibrary[key].custom ? " (Custom)" : ""}</span>
                  <div style={{ display: "flex", gap: "6px" }}>
                    {tuningLibrary[key].custom && (
                      <button
                        type="button"
                        className="remove-item-btn"
                        style={{ borderColor: "#f87171", background: "#fef2f2", color: "#dc2626" }}
                        onClick={() => handleDeleteCustomTuning(key, tuningLibrary[key].name)}
                      >
                        Delete
                      </button>
                    )}
                    <button
                      type="button"
                      className="remove-item-btn"
                      onClick={() => removePreferredTuning(key)}
                      disabled={preferredTuningKeys.length <= 1}
                    >
                      Remove
                    </button>
                  </div>
                </li>
              ))
            )}
          </ul>

          <h3 className="favorites-subtitle">Bass Tunings</h3>
          <ul className="favorites-list">
            {bassPreferredTuningKeys.length === 0 ? (
              <li className="favorites-empty">No preferred bass tunings yet.</li>
            ) : (
              bassPreferredTuningKeys.filter((key) => Boolean(tuningLibrary[key])).map((key) => (
                <li key={key}>
                  <span>{tuningLibrary[key].name}{tuningLibrary[key].custom ? " (Custom)" : ""}</span>
                  <div style={{ display: "flex", gap: "6px" }}>
                    {tuningLibrary[key].custom && (
                      <button
                        type="button"
                        className="remove-item-btn"
                        style={{ borderColor: "#f87171", background: "#fef2f2", color: "#dc2626" }}
                        onClick={() => handleDeleteCustomTuning(key, tuningLibrary[key].name)}
                      >
                        Delete
                      </button>
                    )}
                    <button
                      type="button"
                      className="remove-item-btn"
                      onClick={() => removePreferredTuning(key)}
                      disabled={preferredTuningKeys.length <= 1}
                    >
                      Remove
                    </button>
                  </div>
                </li>
              ))
            )}
          </ul>
        </section>

        <section className="favorites-section">
          <h2>Favorite Songs</h2>
          <div className="favorites-controls inline-form">
            <select value={songInstrument} onChange={(e) => setSongInstrument(e.target.value)}>
              <option value="guitar">Guitar</option>
              <option value="bass">Bass</option>
            </select>
            <input
              type="text"
              value={songName}
              onChange={(e) => setSongName(e.target.value)}
              placeholder="Song name"
            />
            <input
              type="text"
              value={artistName}
              onChange={(e) => setArtistName(e.target.value)}
              placeholder="Artist (optional)"
            />
            <select value={songTuningKey} onChange={(e) => setSongTuningKey(e.target.value)} disabled={songTuningKeys.length === 0}>
              {songTuningKeys.length === 0 ? (
                <option value="">No {songInstrument} tunings available</option>
              ) : songTuningKeys.map((key) => (
                <option key={`s-${key}`} value={key}>{tuningLibrary[key].name}</option>
              ))}
            </select>
            <button type="button" onClick={addFavoriteSong} disabled={!songTuningKey}>Add Song</button>
          </div>

          <h3 className="favorites-subtitle">Guitar Songs</h3>
          <ul className="favorites-list">
            {guitarFavoriteSongs.length === 0 ? (
              <li className="favorites-empty">No favorite guitar songs yet.</li>
            ) : (
              favoriteSongs.map((song, index) => (
                tuningInstrument(song.tuning) === "guitar" && (
                <li key={`${favoriteSongKey(song)}-${index}`}>
                  <span>{favoriteSongLabel(song)}</span>
                  <strong>{tuningLibrary[song.tuning]?.name ?? song.tuning}</strong>
                  {editingArtistIndex === index ? (
                    <span className="artist-edit-inline">
                      <input
                        type="text"
                        value={editingArtistValue}
                        onChange={(e) => setEditingArtistValue(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") saveArtistEdit(index);
                          if (e.key === "Escape") cancelArtistEdit();
                        }}
                        placeholder="Artist name"
                        autoFocus
                      />
                      <button type="button" className="remove-item-btn" onClick={() => saveArtistEdit(index)}>Save</button>
                      <button type="button" className="remove-item-btn" onClick={cancelArtistEdit}>Cancel</button>
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="remove-item-btn"
                      onClick={() => {
                        setEditingArtistIndex(index);
                        setEditingArtistValue(normalizeArtistName(song.artist) ?? "");
                      }}
                    >
                      {normalizeArtistName(song.artist) ? "Edit Artist" : "Add Artist"}
                    </button>
                  )}
                  <button
                    type="button"
                    className="remove-item-btn"
                    onClick={() => removeFavoriteSong(index)}
                  >
                    Remove
                  </button>
                </li>
                )
              ))
            )}
          </ul>

          <h3 className="favorites-subtitle">Bass Songs</h3>
          <ul className="favorites-list">
            {bassFavoriteSongs.length === 0 ? (
              <li className="favorites-empty">No favorite bass songs yet.</li>
            ) : (
              favoriteSongs.map((song, index) => (
                tuningInstrument(song.tuning) === "bass" && (
                <li key={`${favoriteSongKey(song)}-${index}`}>
                  <span>{favoriteSongLabel(song)}</span>
                  <strong>{tuningLibrary[song.tuning]?.name ?? song.tuning}</strong>
                  {editingArtistIndex === index ? (
                    <span className="artist-edit-inline">
                      <input
                        type="text"
                        value={editingArtistValue}
                        onChange={(e) => setEditingArtistValue(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") saveArtistEdit(index);
                          if (e.key === "Escape") cancelArtistEdit();
                        }}
                        placeholder="Artist name"
                        autoFocus
                      />
                      <button type="button" className="remove-item-btn" onClick={() => saveArtistEdit(index)}>Save</button>
                      <button type="button" className="remove-item-btn" onClick={cancelArtistEdit}>Cancel</button>
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="remove-item-btn"
                      onClick={() => {
                        setEditingArtistIndex(index);
                        setEditingArtistValue(normalizeArtistName(song.artist) ?? "");
                      }}
                    >
                      {normalizeArtistName(song.artist) ? "Edit Artist" : "Add Artist"}
                    </button>
                  )}
                  <button
                    type="button"
                    className="remove-item-btn"
                    onClick={() => removeFavoriteSong(index)}
                  >
                    Remove
                  </button>
                </li>
                )
              ))
            )}
          </ul>
        </section>
      </div>
    </div>
  );
}
