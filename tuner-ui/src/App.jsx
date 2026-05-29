// Top-level React component that manages authentication, catalogs, preferences, and page routing; it provides login/register screens or the active tuner/preferences page.

import React, { useEffect, useMemo, useRef, useState } from "react";
import "./App.css";
import Login from "./Login.jsx";
import Register from "./Register.jsx";
import FavoritesPage from "./components/FavoritesPage.jsx";
import CustomTuningPage from "./components/CustomTuningPage.jsx";
import TunerPage from "./components/TunerPage.jsx";
import {
  FALLBACK_NOTE_OPTIONS,
  FALLBACK_TUNING_LIBRARY,
  getDefaultPreferredKeys,
  normalizeFavoriteSong,
  tuningArrayToLibrary,
} from "./appUtils.js";

function App() {
  // Environment values let deployed builds point at remote services, while the
  // blank API URL keeps local Vite development on the same origin/proxy.
  const apiUrl = useMemo(() => {
    const url = import.meta.env.VITE_API_URL;
    return url && url.trim().length > 0 ? url : "";
  }, []);

  // SockJS expects an HTTP endpoint here, not a raw ws:// URL.
  const wsUrl = useMemo(() => {
    const url = import.meta.env.VITE_WS_URL;
    return url && url.trim().length > 0 ? url : "http://localhost:8080/ws";
  }, []);

  const [authMode, setAuthMode] = useState("logged_out");
  const [authScreen, setAuthScreen] = useState("login");
  const [activePage, setActivePage] = useState("tuner");
  const [username, setUsername] = useState("");
  const [favoriteSongs, setFavoriteSongs] = useState([]);
  const [preferencesLoaded, setPreferencesLoaded] = useState(false);
  const [tuningLibrary, setTuningLibrary] = useState(FALLBACK_TUNING_LIBRARY);
  const [validNoteOptions, setValidNoteOptions] = useState(FALLBACK_NOTE_OPTIONS);
  const [preferredTuningKeys, setPreferredTuningKeys] = useState(
    getDefaultPreferredKeys(FALLBACK_TUNING_LIBRARY)
  );
  // Refs let async callbacks read the latest state without re-running effects
  // just because an in-memory tuning catalog changed.
  const tuningLibraryRef = useRef(FALLBACK_TUNING_LIBRARY);
  const isSavingRef = useRef(false);

  useEffect(() => {
    tuningLibraryRef.current = tuningLibrary;
  }, [tuningLibrary]);

  const isGuest = authMode === "guest";
  const isLoggedInUser = authMode === "user";
  const tunerTuningKeys = useMemo(() => Object.keys(tuningLibrary), [tuningLibrary]);

  useEffect(() => {
    let isCancelled = false;

    const loadTunings = async () => {
      try {
        // Logged-in users receive standard tunings plus their custom entries.
        // Guests only need the shared standard catalog.
        const endpoint = isLoggedInUser && username
          ? `${apiUrl}/api/tunings/${encodeURIComponent(username)}`
          : `${apiUrl}/api/tunings/standard`;

        const response = await fetch(endpoint);
        if (!response.ok) {
          throw new Error(`Failed to load tuning catalog (${response.status})`);
        }

        const data = await response.json();
        if (isCancelled) return;

        const nextLibrary = tuningArrayToLibrary(data);
        if (Object.keys(nextLibrary).length > 0) {
          // Keep bundled tunings as a safety net if the API returns a partial
          // catalog during local setup or a database reset.
          const merged = { ...nextLibrary };
          for (const [key, value] of Object.entries(FALLBACK_TUNING_LIBRARY)) {
            if (!merged[key]) merged[key] = value;
          }
          setTuningLibrary(merged);
        }
      } catch (error) {
        console.error("Could not load tuning catalog:", error);
      }
    };

    const loadNotes = async () => {
      try {
        const response = await fetch(`${apiUrl}/api/tunings/notes`);
        if (!response.ok) {
          throw new Error(`Failed to load valid notes (${response.status})`);
        }

        const data = await response.json();
        if (isCancelled) return;

        if (Array.isArray(data) && data.length > 0) {
          setValidNoteOptions(data);
        }
      } catch (error) {
        console.error("Could not load valid note options:", error);
      }
    };

    loadTunings();
    loadNotes();

    return () => {
      isCancelled = true;
    };
  }, [apiUrl, isLoggedInUser, username]);

  useEffect(() => {
    if (!isLoggedInUser || !username) {
      // Guests do not persist preferences, so the save effect should stay off.
      setPreferencesLoaded(false);
      return;
    }

    let isCancelled = false;

    const loadPreferences = async () => {
      try {
        const response = await fetch(`${apiUrl}/api/preferences/${encodeURIComponent(username)}`);
        if (!response.ok) {
          throw new Error(`Failed to load preferences (${response.status})`);
        }

        const data = await response.json();
        if (isCancelled || isSavingRef.current) return;

        const lib = tuningLibraryRef.current;
        // Drop preferences that reference tunings no longer available to the
        // user, such as deleted custom tunings.
        const incomingTunings = Array.isArray(data.preferredTuningKeys)
          ? data.preferredTuningKeys.filter((key) => Boolean(lib[key]))
          : [];

        const incomingSongs = Array.isArray(data.favoriteSongs)
          ? data.favoriteSongs
            .filter((song) => song && typeof song.name === "string" && Boolean(lib[song.tuning]))
            .map((song) => normalizeFavoriteSong(song))
            .filter(Boolean)
          : [];

        setPreferredTuningKeys(incomingTunings.length > 0 ? incomingTunings : getDefaultPreferredKeys(lib));
        setFavoriteSongs(incomingSongs);
      } catch (error) {
        console.error("Could not load preferences:", error);
        if (!isCancelled) {
          setPreferredTuningKeys(getDefaultPreferredKeys(tuningLibraryRef.current));
          setFavoriteSongs([]);
        }
      } finally {
        if (!isCancelled) {
          setPreferencesLoaded(true);
        }
      }
    };

    loadPreferences();

    return () => {
      isCancelled = true;
    };
  }, [apiUrl, isLoggedInUser, username]);

  useEffect(() => {
    if (!isLoggedInUser || !username || !preferencesLoaded) {
      return;
    }

    const lib = tuningLibraryRef.current;
    // The backend stores only valid tuning keys. Filtering here prevents stale
    // deleted custom tunings from being saved back into the account.
    const payload = {
      preferredTuningKeys: preferredTuningKeys.filter((key) => Boolean(lib[key])),
      favoriteSongs,
    };

    const savePreferences = async () => {
      isSavingRef.current = true;
      try {
        const response = await fetch(`${apiUrl}/api/preferences/${encodeURIComponent(username)}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });

        if (!response.ok) {
          const body = await response.text();
          throw new Error(`Failed to save preferences (${response.status}): ${body}`);
        }
      } catch (error) {
        console.error("Could not save preferences:", error);
      } finally {
        isSavingRef.current = false;
      }
    };

    savePreferences();
  }, [apiUrl, favoriteSongs, isLoggedInUser, preferencesLoaded, preferredTuningKeys, username]);

  const handleCustomTuningCreated = (created) => {
    if (!created || !created.key || !Array.isArray(created.strings)) return;

    // The creation API returns a single tuning definition, so normalize it
    // through the same converter used by the full catalog load.
    const nextLibrary = tuningArrayToLibrary([created]);
    setTuningLibrary((prev) => ({ ...prev, ...nextLibrary }));
    setPreferredTuningKeys((prev) => (prev.includes(created.key) ? prev : [...prev, created.key]));
  };

  const handleCustomTuningDeleted = (tuningKey) => {
    // Removing a tuning must also clean up all references that would point at
    // a missing catalog entry on the tuner page.
    setTuningLibrary((prev) => {
      if (!prev[tuningKey]) return prev;
      const next = { ...prev };
      delete next[tuningKey];
      return next;
    });
    setPreferredTuningKeys((prev) => prev.filter((key) => key !== tuningKey));
    setFavoriteSongs((prev) => prev.filter((song) => song.tuning !== tuningKey));
  };

  const handleLoginSuccess = (loggedInUsername) => {
    const normalizedUsername = typeof loggedInUsername === "string" ? loggedInUsername.trim() : "";
    setAuthMode("user");
    setUsername(normalizedUsername || "User");
    setPreferencesLoaded(false);
    setActivePage("tuner");
  };

  const handleEnterAsGuest = () => {
    setAuthMode("guest");
    setUsername("Guest");
    setPreferencesLoaded(false);
    setActivePage("tuner");
  };

  const returnToLogin = () => {
    setAuthMode("logged_out");
    setUsername("");
    setPreferencesLoaded(false);
    setActivePage("tuner");
    setAuthScreen("login");
  };

  if (authMode === "logged_out") {
    if (authScreen === "register") {
      return <Register apiUrl={apiUrl} onSwitchToLogin={() => setAuthScreen("login")} />;
    }

    return (
      <Login
        apiUrl={apiUrl}
        onLoginSuccess={handleLoginSuccess}
        onSwitchToRegister={() => setAuthScreen("register")}
        onEnterAsGuest={handleEnterAsGuest}
      />
    );
  }

  return (
    <div className="app-shell">
      <header className="app-topbar">
        <div className="identity-block">
          <h2>Guitar Tuner</h2>
          <p>
            Signed in as <strong>{username}</strong> {isGuest ? "(Guest)" : ""}
          </p>
        </div>

        <nav className="app-nav" aria-label="Main navigation">
          <button
            type="button"
            className={activePage === "tuner" ? "active" : ""}
            onClick={() => setActivePage("tuner")}
          >
            Tuner
          </button>
          {isLoggedInUser && (
            <button
              type="button"
              className={activePage === "favorites" ? "active" : ""}
              onClick={() => setActivePage("favorites")}
            >
              Favorites
            </button>
          )}
          {isLoggedInUser && (
            <button
              type="button"
              className={activePage === "custom_tuning" ? "active" : ""}
              onClick={() => setActivePage("custom_tuning")}
            >
              Custom Tuning
            </button>
          )}
        </nav>

        <button type="button" className="logout-btn" onClick={returnToLogin}>
          {isLoggedInUser ? "Log Out" : "Exit Guest"}
        </button>
      </header>

      <main>
        {activePage === "custom_tuning" && isLoggedInUser ? (
          <CustomTuningPage
            apiUrl={apiUrl}
            username={username}
            noteOptions={validNoteOptions}
            tuningLibrary={tuningLibrary}
            onCreated={handleCustomTuningCreated}
            onDeleted={handleCustomTuningDeleted}
            onBack={() => setActivePage("favorites")}
          />
        ) : activePage === "favorites" && isLoggedInUser ? (
          <FavoritesPage
            tuningLibrary={tuningLibrary}
            preferredTuningKeys={preferredTuningKeys}
            setPreferredTuningKeys={setPreferredTuningKeys}
            favoriteSongs={favoriteSongs}
            setFavoriteSongs={setFavoriteSongs}
            onOpenCustomTuning={() => setActivePage("custom_tuning")}
            apiUrl={apiUrl}
            username={username}
            onDeleteCustomTuning={handleCustomTuningDeleted}
          />
        ) : (
          <TunerPage
            wsUrl={wsUrl}
            tuningLibrary={tuningLibrary}
            tuningKeys={tunerTuningKeys}
            preferredTuningKeys={preferredTuningKeys}
            isLoggedInUser={isLoggedInUser}
            favoriteSongs={favoriteSongs}
          />
        )}
      </main>
    </div>
  );
}

export default App;
