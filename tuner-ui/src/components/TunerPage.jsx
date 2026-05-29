// Main tuner page for selecting instruments/tunings, reading live pitch, and displaying status; it provides interactive tuner UI with meter, string target, and live frequency/status updates.

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import headstockImage from "../../headstock.png";
import bassHeadstockImage from "../../bass_headstock.jpg";
import { createPitchDetector, listAudioDevices, requestAndListAudioDevices } from "../pitchDetector.js";
import {
  clamp,
  favoriteSongKey,
  favoriteSongLabel,
  FALLBACK_TUNING_LIBRARY,
  getColumnIds,
  getHeadstockLines,
  INSTRUMENTS,
  instrumentForTuning,
  prettyStatus,
  statusFromCents,
} from "../appUtils.js";

const METER_FULL_SCALE_CENTS = 100;
const DISPLAY_DEADZONE_CENTS = 2;
const DISPLAY_SMOOTHING = 0.2;
const AUTO_SELECT_MAX_CENTS = 50;

function centsBetween(frequency, targetFrequency) {
  // One octave is 1200 cents, so log2 gives a musical distance from target.
  return 1200 * Math.log2(frequency / targetFrequency);
}

function findClosestStringForFrequency(strings, frequency) {
  if (!Array.isArray(strings) || strings.length === 0 || !(frequency > 0)) {
    return null;
  }

  let bestMatch = null;
  for (const stringItem of strings) {
    if (!(stringItem?.freq > 0)) continue;

    // Choose by musical distance instead of raw Hz, since low strings have
    // tighter frequency spacing than high strings.
    const centsOff = centsBetween(frequency, stringItem.freq);
    if (!bestMatch || Math.abs(centsOff) < Math.abs(bestMatch.centsOff)) {
      bestMatch = { stringItem, centsOff };
    }
  }

  if (!bestMatch || Math.abs(bestMatch.centsOff) > AUTO_SELECT_MAX_CENTS) {
    return null;
  }

  return bestMatch;
}

export default function TunerPage({
  wsUrl,
  tuningLibrary,
  tuningKeys,
  preferredTuningKeys,
  isLoggedInUser,
  favoriteSongs,
}) {
  const socketClientRef = useRef(null);
  const [selectedInstrument, setSelectedInstrument] = useState("guitar");
  const [selectedTuningKey, setSelectedTuningKey] = useState(tuningKeys[0]);
  const [selectedFavoriteTuningKey, setSelectedFavoriteTuningKey] = useState("");
  const [selectedStringId, setSelectedStringId] = useState(1);
  const [autoSelectString, setAutoSelectString] = useState(false);
  const [songSearch, setSongSearch] = useState("");
  const [artistSearch, setArtistSearch] = useState("");
  const [selectedSongKey, setSelectedSongKey] = useState("");
  const [isListening, setIsListening] = useState(false);
  const [statusMessage, setStatusMessage] = useState("Stopped");
  const [detectedFreq, setDetectedFreq] = useState(0);
  const [centsOff, setCentsOff] = useState(0);
  const [displayCentsOff, setDisplayCentsOff] = useState(0);
  const [wsConnected, setWsConnected] = useState(false);
  const [vibratingStringId, setVibratingStringId] = useState(null);
  const lastPitchSampleRef = useRef({ freq: 0, centsoff: 0 });
  const lastPitchChangeRef = useRef(0);
  const vibrationTimeoutRef = useRef(null);
  const [audioDevices, setAudioDevices] = useState([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [audioActive, setAudioActive] = useState(false);
  const [inputLevel, setInputLevel] = useState(0);
  const detectorRef = useRef(null);
  const selectedStringRef = useRef(null);
  const activeStringsRef = useRef([]);
  const autoSelectStringRef = useRef(false);
  // WebSocket/audio callbacks outlive individual renders, so refs mirror the
  // latest selection state without forcing each callback to be recreated.
  const tuningInstrument = useCallback(
    (key) => instrumentForTuning(key, tuningLibrary[key]),
    [tuningLibrary]
  );

  const filteredTuningKeys = useMemo(() => {
    // The instrument filter changes the visible options, but the full catalog
    // remains available for favorites and song lookups.
    if (selectedInstrument === "all") return tuningKeys;
    return tuningKeys.filter((key) => tuningInstrument(key) === selectedInstrument);
  }, [tuningKeys, selectedInstrument, tuningInstrument]);
  const filteredPreferredTuningKeys = useMemo(() => {
    if (selectedInstrument === "all") {
      return preferredTuningKeys.filter((key) => Boolean(tuningLibrary[key]));
    }
    return preferredTuningKeys.filter((key) => {
      if (!tuningLibrary[key]) return false;
      return tuningInstrument(key) === selectedInstrument;
    });
  }, [preferredTuningKeys, selectedInstrument, tuningLibrary, tuningInstrument]);

  const effectiveSelectedTuningKey =
    // Keep the controlled select valid when the catalog or instrument filter
    // changes underneath the currently selected tuning.
    !tuningKeys.includes(selectedTuningKey) ? (tuningKeys[0] ?? "") :
    filteredTuningKeys.length > 0 && !filteredTuningKeys.includes(selectedTuningKey) ? filteredTuningKeys[0] :
    selectedTuningKey;

  const effectiveSelectedFavoriteTuningKey =
    selectedFavoriteTuningKey &&
    preferredTuningKeys.includes(selectedFavoriteTuningKey) &&
    filteredPreferredTuningKeys.includes(selectedFavoriteTuningKey)
      ? selectedFavoriteTuningKey
      : "";

  const activeTuning = tuningLibrary[effectiveSelectedTuningKey] ?? tuningLibrary[tuningKeys[0]] ?? {
    name: "Standard (EADGBE)",
    strings: FALLBACK_TUNING_LIBRARY.standard.strings,
  };
  const headstockSrc = selectedInstrument === "bass" ? bassHeadstockImage : headstockImage;
  const headstockAlt = selectedInstrument === "bass"
    ? "Bass headstock"
    : "Gibson style 3x3 headstock";
  const activeStrings = activeTuning.strings;
  const stringCount = activeStrings.length;
  const headstockLines = getHeadstockLines(stringCount);
  const { left: leftColumnIds, right: rightColumnIds } = getColumnIds(stringCount);

  const filteredSongs = useMemo(() => {
    const q = songSearch.trim().toLowerCase();
    const a = artistSearch.trim().toLowerCase();
    // Song search checks both title and artist, while the artist field can be
    // used as a more precise second filter.
    return favoriteSongs.filter((song) => {
      const songInstrument = tuningInstrument(song.tuning);
      const instrumentMatch = selectedInstrument === "all" || songInstrument === selectedInstrument;
      const songName = (song.name ?? "").toLowerCase();
      const songArtist = (song.artist ?? "").toLowerCase();
      const nameSearchMatch = !q || songName.includes(q) || songArtist.includes(q);
      const artistSearchMatch = !a || songArtist.includes(a);
      return instrumentMatch && nameSearchMatch && artistSearchMatch;
    });
  }, [songSearch, artistSearch, favoriteSongs, selectedInstrument, tuningInstrument]);

  const effectiveSelectedSongKey =
    selectedSongKey && filteredSongs.some((song) => favoriteSongKey(song) === selectedSongKey)
      ? selectedSongKey
      : "";

  const leftColumnStrings = leftColumnIds
    .map((id) => activeStrings.find((s) => s.id === id))
    .filter(Boolean);
  const rightColumnStrings = rightColumnIds
    .map((id) => activeStrings.find((s) => s.id === id))
    .filter(Boolean);

  const effectiveSelectedStringId = activeStrings.length > 0 && !activeStrings.some((s) => s.id === selectedStringId)
    ? activeStrings[0].id
    : selectedStringId;

  const selectedString = activeStrings.find((s) => s.id === effectiveSelectedStringId) ?? activeStrings[0];

  useEffect(() => {
    activeStringsRef.current = activeStrings;
  }, [activeStrings]);

  useEffect(() => {
    selectedStringRef.current = selectedString ?? null;
  }, [selectedString]);

  useEffect(() => {
    autoSelectStringRef.current = autoSelectString;
  }, [autoSelectString]);

  useEffect(() => {
    if (!isListening) {
      // Stopping listening tears down the STOMP client so reconnects start from
      // a fresh subscription.
      if (socketClientRef.current) {
        try {
          socketClientRef.current.deactivate();
        } catch {
          // ignore deactivation errors when stopping
        }
        socketClientRef.current = null;
      }
      return;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 3000,
      debug: () => {},
      onConnect: () => {
        setWsConnected(true);
        setStatusMessage("Connected. Waiting for tuning data...");

        client.subscribe("/topic/tuning", (message) => {
          try {
            const msg = JSON.parse(message.body);
            if (msg.type !== "tuning_update") return;

            const nextDetectedHz = Number(msg.detectedHz ?? 0);
            // In auto-select mode, a live pitch can move the target string if it
            // is close enough to another string in the active tuning.
            const autoMatch = autoSelectStringRef.current
              ? findClosestStringForFrequency(activeStringsRef.current, nextDetectedHz)
              : null;
            if (autoMatch && autoMatch.stringItem.id !== selectedStringRef.current?.id) {
              setSelectedStringId(autoMatch.stringItem.id);
            }

            const selectedTarget = autoMatch?.stringItem ?? selectedStringRef.current;
            // Prefer recalculating cents against the selected target so browser,
            // mock, and hardware updates all display consistently.
            const nextCents = nextDetectedHz > 0 && selectedTarget?.freq
              ? centsBetween(nextDetectedHz, selectedTarget.freq)
              : Number(msg.centsOff ?? 0);

            const roundedCents = Math.round(nextCents * 100) / 100;
            setDetectedFreq(nextDetectedHz);
            setCentsOff(roundedCents);
            setDisplayCentsOff((prev) => {
              // Smooth the needle to reduce jitter from small pitch variations.
              const target = Math.abs(roundedCents) < DISPLAY_DEADZONE_CENTS ? 0 : roundedCents;
              const next = prev + ((target - prev) * DISPLAY_SMOOTHING);
              return Math.abs(next) < 0.1 ? 0 : Math.round(next * 100) / 100;
            });

            const nextStatus = msg.status ?? statusFromCents(nextCents);
            const displayStatus = nextDetectedHz > 0 && selectedTarget?.freq
              ? statusFromCents(nextCents)
              : nextStatus;
            const targetLabel = selectedTarget?.note ? ` vs ${selectedTarget.note}` : "";
            setStatusMessage(`Live: ${prettyStatus(displayStatus)} (${nextCents.toFixed(1)} cents${targetLabel})`);
          } catch {
            // ignore malformed WebSocket messages
          }
        });
      },
      onStompError: () => {
        setWsConnected(false);
        setStatusMessage("STOMP error");
      },
      onWebSocketClose: () => {
        setWsConnected(false);
        setStatusMessage("Disconnected");
      },
      onWebSocketError: () => {
        setWsConnected(false);
        setStatusMessage("WebSocket error");
      },
    });

    socketClientRef.current = client;
    client.activate();

    return () => {
      try {
        client.deactivate();
      } catch {
        // ignore deactivation errors on cleanup
      }
    };
  }, [activeStrings, isListening, wsUrl]);

  const effectiveWsConnected = isListening && wsConnected;

  const tuningState = useMemo(() => prettyStatus(statusFromCents(displayCentsOff)), [displayCentsOff]);
  const meterRatio = useMemo(
    () => clamp(displayCentsOff / METER_FULL_SCALE_CENTS, -1, 1),
    [displayCentsOff]
  );

  const handleSelectString = (stringItem) => {
    // Reset live readings when the target changes so old pitch data does not
    // appear to describe the newly selected string.
    setSelectedStringId(stringItem.id);
    setDetectedFreq(0);
    setCentsOff(0);
    setDisplayCentsOff(0);
    lastPitchSampleRef.current = { freq: 0, centsoff: 0 };
    lastPitchChangeRef.current = 0;
    setVibratingStringId(null);
    if (isListening) {
      setStatusMessage(`Connected. Selected target: ${stringItem.note}`);
    }
  };

  const handleSelectTuning = (event) => {
    const nextTuningKey = event.target.value;
    setSelectedTuningKey(nextTuningKey);
    // Selecting a tuning should also move the instrument filter to match it.
    const inst = tuningInstrument(nextTuningKey);
    if (inst === "guitar" || inst === "bass") {
      setSelectedInstrument(inst);
    }
    if (isListening) {
      setStatusMessage(`Connected. Tuning set to ${tuningLibrary[nextTuningKey].name}`);
    }
  };

  const handleSelectSong = (song) => {
    if (!tuningKeys.includes(song.tuning)) {
      setStatusMessage(`Song tuning not available in current tuning list: ${song.name}`);
      return;
    }

    setSelectedTuningKey(song.tuning);
    setSelectedFavoriteTuningKey("");
    setSelectedSongKey(favoriteSongKey(song));
    setStatusMessage(`Loaded song tuning: ${favoriteSongLabel(song)} -> ${tuningLibrary[song.tuning].name}`);
  };

  const handleSongSelectChange = (event) => {
    const nextSongKey = event.target.value;
    setSelectedSongKey(nextSongKey);
    const song = filteredSongs.find((item) => favoriteSongKey(item) === nextSongKey);
    if (song) {
      handleSelectSong(song);
    }
  };

  const handleFavoriteTuningChange = (event) => {
    const nextTuningKey = event.target.value;
    setSelectedFavoriteTuningKey(nextTuningKey);
    if (!nextTuningKey) return;

    setSelectedTuningKey(nextTuningKey);
    setSongSearch("");
    setArtistSearch("");
    setSelectedSongKey("");

    if (isListening) {
      setStatusMessage(`Connected. Favorite tuning set to ${tuningLibrary[nextTuningKey].name}`);
    }
  };

  const handleToggleWs = () => {
    if (isListening) {
      setIsListening(false);
      setStatusMessage("Stopped");
    } else {
      setIsListening(true);
      setStatusMessage("Connecting...");
    }
  };

  useEffect(() => {
    const builtinKeywords = ["built-in", "internal", "realtek", "hdmi"];
    requestAndListAudioDevices()
      .then((devices) => {
        setAudioDevices(devices);
        if (devices.length > 0) {
          // Prefer likely external instrument interfaces over laptop mics.
          const external = devices.find(
            (d) => !builtinKeywords.some((kw) => d.label.toLowerCase().includes(kw))
          );
          setSelectedDeviceId((external ?? devices[0]).deviceId);
        }
      })
      .catch(() => {});
  }, []);

  const handleToggleAudio = async () => {
    if (audioActive && detectorRef.current) {
      detectorRef.current.stop();
      detectorRef.current = null;
      setAudioActive(false);
      setInputLevel(0);
      setStatusMessage("Audio input stopped");
      return;
    }

    try {
      setStatusMessage("Starting audio input...");
      const detector = await createPitchDetector({
        deviceId: selectedDeviceId || undefined,
      });

      detector.onLevel = (level) => setInputLevel(level);

      detector.onPitch = ({ frequency }) => {
        const currentStrings = activeStringsRef.current;
        // Browser audio follows the same target-selection rules as WebSocket
        // updates, so local and backend-driven modes behave alike.
        const autoMatch = autoSelectStringRef.current
          ? findClosestStringForFrequency(currentStrings, frequency)
          : null;
        if (autoMatch && autoMatch.stringItem.id !== selectedStringRef.current?.id) {
          setSelectedStringId(autoMatch.stringItem.id);
        }

        const target = autoMatch?.stringItem ?? selectedStringRef.current;
        if (!target?.freq) {
          return;
        }

        if (socketClientRef.current?.connected) {
          // When connected, send raw frequency to the backend so the shared
          // /topic/tuning pipeline stays the source of truth.
          socketClientRef.current.publish({
            destination: "/app/audio-frequency",
            body: JSON.stringify({
              detectedHz: frequency,
              targetHz: target.freq,
              stringLabel: target.label,
              note: target.note,
              stringNumber: target.id,
            }),
          });
        } else {
          // Local-only mode updates the meter directly when no backend socket
          // is connected.
          const nextCentsOff = centsBetween(frequency, target.freq);
          const roundedCentsOff = Math.round(nextCentsOff * 100) / 100;
          setDetectedFreq(frequency);
          setCentsOff(roundedCentsOff);
          setDisplayCentsOff((prev) => {
            const target2 = Math.abs(roundedCentsOff) < DISPLAY_DEADZONE_CENTS ? 0 : roundedCentsOff;
            const next = prev + ((target2 - prev) * DISPLAY_SMOOTHING);
            return Math.abs(next) < 0.1 ? 0 : Math.round(next * 100) / 100;
          });
          const status = statusFromCents(nextCentsOff);
          setStatusMessage(`Audio: ${prettyStatus(status)} (${nextCentsOff.toFixed(1)} cents)`);
        }
      };

      detector.start();
      detectorRef.current = detector;
      setAudioActive(true);
      setStatusMessage("Audio input active - play a string");

      const devices = await listAudioDevices();
      setAudioDevices(devices);
    } catch (err) {
      console.error("Audio input error:", err);
      setStatusMessage(`Audio error: ${err.message}`);
    }
  };

  useEffect(() => () => {
    if (detectorRef.current) {
      detectorRef.current.stop();
      detectorRef.current = null;
    }
  }, []);

  useEffect(() => {
    const prev = lastPitchSampleRef.current;
    const freqDelta = Math.abs(detectedFreq - prev.freq);
    const centsDelta = Math.abs(centsOff - prev.centsoff);

    if (freqDelta > 0.1 || centsDelta > 1.5) {
      // The headstock animation should pulse only while the input is changing.
      lastPitchChangeRef.current = Date.now();
    }

    lastPitchSampleRef.current = { freq: detectedFreq, centsoff: centsOff };
  }, [detectedFreq, centsOff]);

  useEffect(() => {
    if (!selectedString?.id) return;

    const vibrate = () => {
      // A short interval pulse creates visible movement without permanently
      // animating an idle string.
      const now = Date.now();
      const changeRecently = now - lastPitchChangeRef.current < 1500;
      if (!changeRecently) return;

      setVibratingStringId(selectedString.id);

      if (vibrationTimeoutRef.current) {
        window.clearTimeout(vibrationTimeoutRef.current);
      }

      vibrationTimeoutRef.current = window.setTimeout(() => {
        setVibratingStringId((current) => (current === selectedString.id ? null : current));
      }, 300);
    };

    const intervalId = window.setInterval(vibrate, 2500);

    return () => {
      window.clearInterval(intervalId);
      if (vibrationTimeoutRef.current) {
        window.clearTimeout(vibrationTimeoutRef.current);
      }
      setVibratingStringId(null);
    };
  }, [selectedString?.id]);

  return (
    <div className="tuner-page">
      <div className="tuner-card">
        <header className="header">
          <h1>{selectedInstrument === "bass" ? "Bass Tuner" : "Guitar Tuner"}</h1>
          <p className="subtext">Existing UI wired to WebSocket (dev uses mock server)</p>
          <p className="subtext">
            WS URL: <code>{wsUrl}</code>
          </p>
        </header>

        <div className={`status-banner ${effectiveWsConnected ? "listening" : ""}`}>{statusMessage}</div>

        <section className="section">
          <h2 className="section-title">Instrument</h2>
          <div className="tuning-profile-row">
            <select
              className="tuning-select"
              value={selectedInstrument}
              onChange={(e) => setSelectedInstrument(e.target.value)}
            >
              {INSTRUMENTS.map((inst) => (
                <option key={inst.value} value={inst.value}>
                  {inst.icon} {inst.label}
                </option>
              ))}
            </select>
          </div>

          <h2 className="section-title">Choose Tuning</h2>
          <div className="tuning-profile-row">
            <select className="tuning-select" value={effectiveSelectedTuningKey} onChange={handleSelectTuning}>
              <optgroup label="Guitar">
                {tuningKeys.filter((k) => tuningInstrument(k) === "guitar").map((key) => (
                  <option key={key} value={key}>{tuningLibrary[key].name}</option>
                ))}
              </optgroup>
              <optgroup label="Bass">
                {tuningKeys.filter((k) => tuningInstrument(k) === "bass").map((key) => (
                  <option key={key} value={key}>{tuningLibrary[key].name}</option>
                ))}
              </optgroup>
            </select>
          </div>

          {isLoggedInUser && (
            <>
              <h2 className="section-title">Favorite Tunings</h2>
              <div className="tuning-profile-row">
                <select
                  className="tuning-select"
                  value={effectiveSelectedFavoriteTuningKey}
                  onChange={handleFavoriteTuningChange}
                >
                  <option value="">Select a favorite tuning</option>
                  {filteredPreferredTuningKeys.map((key) => (
                    <option key={`fav-${key}`} value={key}>
                      {tuningLibrary[key].name}
                    </option>
                  ))}
                </select>
              </div>
            </>
          )}

          {isLoggedInUser && (
            <>
              <h2 className="section-title">Favorite Songs</h2>
              <div className="song-search-row">
                <input
                  type="text"
                  value={songSearch}
                  onChange={(e) => {
                    setSongSearch(e.target.value);
                    setSelectedSongKey("");
                  }}
                  placeholder="Search by song name"
                  className="song-search-input"
                />
                <input
                  type="text"
                  value={artistSearch}
                  onChange={(e) => {
                    setArtistSearch(e.target.value);
                    setSelectedSongKey("");
                  }}
                  placeholder="Search by artist"
                  className="song-search-input"
                />
              </div>
              <div className="song-search-row">
                <select
                  className="tuning-select song-select"
                  value={effectiveSelectedSongKey}
                  onChange={handleSongSelectChange}
                  disabled={filteredSongs.length === 0}
                >
                  <option value="" disabled>
                    {filteredSongs.length === 0
                      ? "No matching songs"
                      : `Select from ${filteredSongs.length} favorite song(s)`}
                  </option>
                  {filteredSongs.map((song, index) => (
                    <option key={`${favoriteSongKey(song)}-${index}`} value={favoriteSongKey(song)}>
                      {favoriteSongLabel(song)} ({tuningLibrary[song.tuning]?.name ?? song.tuning})
                    </option>
                  ))}
                </select>
              </div>
              {(songSearch.trim().length > 0 || artistSearch.trim().length > 0) && filteredSongs.length === 0 && (
                <p className="song-empty">No matching favorite songs.</p>
              )}
            </>
          )}

          <h2 className="section-title">Select String</h2>
          <div className="tuning-profile-row">
            <label className="auto-select-toggle">
              <input
                type="checkbox"
                checked={autoSelectString}
                onChange={(e) => setAutoSelectString(e.target.checked)}
              />
              <span>Auto-select closest string</span>
            </label>
          </div>
          <div className="headstock-layout">
            <div className="string-column">
              {leftColumnStrings.map((s) => {
                const isSelected = s.id === selectedString.id;
                return (
                  <button
                    key={`${s.id}-${s.note}`}
                    type="button"
                    className={`string-btn ${isSelected ? "selected" : ""}`}
                    onClick={() => handleSelectString(s)}
                  >
                    <span className="string-label">{s.label}</span>
                    <span className="string-note">{s.note}</span>
                  </button>
                );
              })}
            </div>

            <div className="headstock-image-wrap">
              <div className="headstock-image-stage">
                <img
                  src={headstockSrc}
                  alt={headstockAlt}
                  className="headstock-image"
                />

                <svg
                  className="headstock-overlay"
                  viewBox="0 0 100 100"
                  preserveAspectRatio="none"
                  aria-hidden="true"
                >
                  {headstockLines.map((line) => (
                    <line
                      key={line.id}
                      x1={line.x1}
                      y1={line.y1}
                      x2={line.x2}
                      y2={line.y2}
                      className={[
                        "headstock-string-line",
                        line.id === selectedString.id ? "active" : "",
                        line.id === vibratingStringId ? "vibrating" : "",
                      ].filter(Boolean).join(" ")}
                    />
                  ))}
                </svg>
              </div>
            </div>

            <div className="string-column">
              {rightColumnStrings.map((s) => {
                const isSelected = s.id === selectedString.id;
                return (
                  <button
                    key={`${s.id}-${s.note}`}
                    type="button"
                    className={`string-btn ${isSelected ? "selected" : ""}`}
                    onClick={() => handleSelectString(s)}
                  >
                    <span className="string-label">{s.label}</span>
                    <span className="string-note">{s.note}</span>
                  </button>
                );
              })}
            </div>
          </div>
        </section>

        <section className="section status-display">
          <div className="note-display">
            <div className="note-label">Target</div>
            <div className="note-value">{selectedString.note}</div>
          </div>

          <div className="tuning-status">
            <div className="note-label">Status</div>
            <div
              className={`status-pill ${
                tuningState === "In Tune" ? "in-tune" : tuningState === "Flat" ? "flat" : "sharp"
              }`}
            >
              {tuningState}
            </div>
          </div>
        </section>

        <section className="section">
          <h2 className="section-title">Tuning Meter</h2>
          <div className="meter-wrapper">
            <div className="meter-track">
              <div className="meter-center-line" />
              <div
                className="meter-needle"
                style={{ left: `calc(50% + ${meterRatio * 45}%)` }}
                aria-label="Tuning needle"
              />
            </div>
            <div className="meter-labels">
              <span>Flat</span>
              <span>In Tune</span>
              <span>Sharp</span>
            </div>
          </div>
        </section>

        <section className="section frequency-row">
          <div className="frequency-card">
            <div className="note-label">Detected Frequency</div>
            <div className="frequency-value">{detectedFreq ? `${detectedFreq.toFixed(2)} Hz` : "--"}</div>
          </div>
          <div className="frequency-card">
            <div className="note-label">Reference Frequency</div>
            <div className="frequency-value">{selectedString.freq.toFixed(2)} Hz</div>
          </div>
        </section>

        <section className="section action-row">
          <button className="listen-btn" type="button" onClick={handleToggleWs}>
            {isListening ? "Disconnect WS" : "Connect WS"}
          </button>
        </section>

        <section className="section">
          <h2 className="section-title">Audio Input (Instrument Interface)</h2>
          <p className="subtext">
            Capture audio from your USB interface or microphone. You can test locally with Start Audio alone, or pair it with the WebSocket when the backend is not in mock mode.
          </p>
          <div className="audio-input-controls">
            <select
              className="tuning-select"
              value={selectedDeviceId}
              onChange={(e) => setSelectedDeviceId(e.target.value)}
              disabled={audioActive}
            >
              {audioDevices.length === 0 ? (
                <option value="">No audio devices found</option>
              ) : (
                audioDevices.map((d) => (
                  <option key={d.deviceId} value={d.deviceId}>{d.label}</option>
                ))
              )}
            </select>
            <button
              className={`listen-btn ${audioActive ? "audio-active" : ""}`}
              type="button"
              onClick={handleToggleAudio}
              disabled={audioDevices.length === 0}
            >
              {audioActive ? "Stop Audio" : "Start Audio"}
            </button>
          </div>
          {audioActive && (
            <div className="audio-status">
              <span>Listening - play a string on your instrument</span>
              <div className="level-meter-row">
                <span className="level-label">Input level:</span>
                <div className="level-meter-track">
                  <div
                    className="level-meter-fill"
                    style={{ width: `${Math.round(inputLevel * 100)}%` }}
                  />
                </div>
                <span className="level-value">{inputLevel > 0 ? `${(inputLevel * 100).toFixed(1)}%` : "silent"}</span>
              </div>
            </div>
          )}
        </section>

        <section className="section">
          <h2 className="section-title">{activeTuning.name} Reference</h2>
          <ul className="reference-list">
            {activeStrings.map((s) => (
              <li key={`ref-${s.id}-${s.note}`}>
                <span>{s.note}</span>
                <span>{s.freq.toFixed(2)} Hz</span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
