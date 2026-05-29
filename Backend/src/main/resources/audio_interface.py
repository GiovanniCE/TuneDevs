# Provides audio-input helpers for Python-based frequency detection; it provides frequency/sample data used by the tuner script.

#!/usr/bin/env python3
"""
Guitar audio interface module for USB audio devices.

Replaces the GPIO-based freqCounter for setups using a USB audio
interface (e.g. Focusrite Scarlett, Behringer U-Phoria, iRig, etc.)
instead of direct GPIO input.

Provides the same `measure_frequency(pin, window_s)` signature as
freqCounter so that tuner.py can swap in this module with no changes.

Dependencies:
    pip install sounddevice numpy

Supported interfaces:
    Any USB audio device recognised by the OS as an input device.
    Run `python -m sounddevice` to list available devices.

Usage in tuner.py:
    # Replace: from freqCounter import measure_frequency
    from audio_interface import measure_frequency
"""

import math
import time
from typing import Optional

import numpy as np

try:
    import sounddevice as sd
except ImportError as exc:
    raise SystemExit(
        "sounddevice is required for USB audio interface support.\n"
        "Install with: pip install sounddevice"
    ) from exc

# ── Configuration ────────────────────────────────────────────────────
SAMPLE_RATE = 44100          # Hz – standard audio rate
CHANNELS = 1                 # mono input
BLOCK_SIZE = 4096            # samples per block (≈93 ms at 44.1 kHz)
MIN_FREQ = 60.0              # lowest guitar fundamental we care about
MAX_FREQ = 1400.0            # well above highest guitar harmonic
SILENCE_THRESHOLD = 0.005    # RMS below this is "no signal"
YIN_THRESHOLD = 0.15         # YIN confidence threshold (lower = stricter)

# ── Device selection ─────────────────────────────────────────────────
_selected_device: Optional[int] = None


def list_audio_devices() -> list[dict]:
    """Return a list of available audio input devices with their IDs."""
    devices = sd.query_devices()
    inputs = []
    for i, dev in enumerate(devices):
        if dev["max_input_channels"] > 0:
            inputs.append({
                "id": i,
                "name": dev["name"],
                "channels": dev["max_input_channels"],
                "sample_rate": dev["default_samplerate"],
            })
    return inputs


def select_device(device_id: Optional[int] = None) -> dict:
    """
    Select an audio input device by ID.

    If device_id is None, picks the first available USB/external input
    device, falling back to the system default.

    Returns device info dict.
    Raises RuntimeError if no suitable device is found.
    """
    global _selected_device
    devices = list_audio_devices()

    if not devices:
        raise RuntimeError(
            "No audio input devices found. "
            "Connect a USB audio interface and try again."
        )

    if device_id is not None:
        for dev in devices:
            if dev["id"] == device_id:
                _selected_device = device_id
                return dev
        raise RuntimeError(f"Audio device {device_id} not found or has no inputs.")

    # Prefer external/USB devices over built-in mic
    builtin_keywords = ("built-in", "internal", "realtek", "hdmi")
    for dev in devices:
        name_lower = dev["name"].lower()
        if not any(kw in name_lower for kw in builtin_keywords):
            _selected_device = dev["id"]
            return dev

    # Fall back to first available
    _selected_device = devices[0]["id"]
    return devices[0]


def get_selected_device() -> Optional[int]:
    """Return the currently selected device ID, or None if not yet selected."""
    return _selected_device


# ── YIN pitch detection ─────────────────────────────────────────────

def _yin_pitch(signal: np.ndarray, sr: int) -> Optional[float]:
    """
    Estimate the fundamental frequency of a signal using the YIN algorithm.

    The YIN algorithm (de Cheveigné & Kawahara, 2002) is well-suited for
    monophonic musical instrument signals. It uses a differencing function
    followed by cumulative mean normalisation to find the period.

    Returns frequency in Hz, or None if no confident estimate.
    """
    # Tau range corresponds to our frequency limits
    tau_min = max(2, int(sr / MAX_FREQ))
    tau_max = min(len(signal) // 2, int(sr / MIN_FREQ))
    if tau_max <= tau_min:
        return None

    # Step 1: Difference function
    length = len(signal)
    diff = np.zeros(tau_max)
    for tau in range(1, tau_max):
        diff[tau] = np.sum((signal[:length - tau] - signal[tau:length]) ** 2)

    # Step 2: Cumulative mean normalised difference
    cmnd = np.ones(tau_max)
    running_sum = 0.0
    for tau in range(1, tau_max):
        running_sum += diff[tau]
        if running_sum == 0:
            cmnd[tau] = 1.0
        else:
            cmnd[tau] = diff[tau] * tau / running_sum

    # Step 3: Absolute threshold – find first tau below threshold
    best_tau = None
    for tau in range(tau_min, tau_max):
        if cmnd[tau] < YIN_THRESHOLD:
            # Look for local minimum following this point
            while tau + 1 < tau_max and cmnd[tau + 1] < cmnd[tau]:
                tau += 1
            best_tau = tau
            break

    if best_tau is None:
        return None

    # Step 4: Parabolic interpolation for sub-sample accuracy
    if 1 <= best_tau < tau_max - 1:
        alpha = cmnd[best_tau - 1]
        beta = cmnd[best_tau]
        gamma = cmnd[best_tau + 1]
        denom = 2 * (2 * beta - alpha - gamma)
        if abs(denom) > 1e-10:
            peak = best_tau + (alpha - gamma) / denom
        else:
            peak = float(best_tau)
    else:
        peak = float(best_tau)

    if peak <= 0:
        return None

    freq = sr / peak
    if MIN_FREQ <= freq <= MAX_FREQ:
        return freq
    return None


def _rms(signal: np.ndarray) -> float:
    """Compute the root-mean-square of a signal."""
    return float(np.sqrt(np.mean(signal ** 2)))


# ── Public API (compatible with freqCounter) ─────────────────────────

def measure_frequency(
    pin: int = 0,
    window_s: float = 0.1,
) -> tuple[int, int, float]:
    """
    Capture audio from the selected USB interface and estimate pitch.

    This function mirrors the freqCounter.measure_frequency signature so
    tuner.py can use either module interchangeably.

    Parameters
    ----------
    pin : int
        Ignored (kept for API compatibility with GPIO freqCounter).
    window_s : float
        Recording duration in seconds. More samples → more accuracy.

    Returns
    -------
    (periods, elapsed_ns, frequency_hz)
        periods: estimated number of complete periods in the window
        elapsed_ns: recording duration in nanoseconds
        frequency_hz: detected fundamental frequency

    Raises
    ------
    TimeoutError
        If no signal or pitch is detected.
    RuntimeError
        If the audio device is unavailable.
    """
    if _selected_device is None:
        select_device()

    num_samples = max(BLOCK_SIZE, int(SAMPLE_RATE * window_s))

    start_ns = time.perf_counter_ns()
    try:
        recording = sd.rec(
            num_samples,
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            device=_selected_device,
            dtype="float32",
        )
        sd.wait()
    except Exception as exc:
        raise RuntimeError(
            f"Failed to read from audio device {_selected_device}: {exc}"
        ) from exc
    end_ns = time.perf_counter_ns()

    signal = recording.flatten()

    # Check for silence
    level = _rms(signal)
    if level < SILENCE_THRESHOLD:
        raise TimeoutError("No signal detected (silence).")

    # Detect pitch with YIN
    freq = _yin_pitch(signal, SAMPLE_RATE)
    if freq is None:
        raise TimeoutError("Could not detect pitch from audio input.")

    elapsed_ns = end_ns - start_ns
    periods = max(1, int(freq * window_s))

    return periods, elapsed_ns, freq


# ── CLI helper ───────────────────────────────────────────────────────

if __name__ == "__main__":
    import json
    import sys

    print("=== Guitar Audio Interface ===")
    print()

    devices = list_audio_devices()
    if not devices:
        print("ERROR: No audio input devices found.")
        print("Connect a USB audio interface and try again.")
        sys.exit(1)

    print("Available input devices:")
    for dev in devices:
        print(f"  [{dev['id']}] {dev['name']} "
              f"({dev['channels']}ch, {dev['sample_rate']:.0f}Hz)")
    print()

    selected = select_device()
    print(f"Selected: [{selected['id']}] {selected['name']}")
    print()

    print("Listening for guitar signal... (Ctrl+C to stop)")
    print()

    try:
        while True:
            try:
                periods, elapsed_ns, freq = measure_frequency(window_s=0.15)
                print(json.dumps({
                    "frequency": round(freq, 2),
                    "periods": periods,
                    "elapsed_ms": round(elapsed_ns / 1_000_000, 1),
                    "rms": round(_rms(sd.rec(256, samplerate=SAMPLE_RATE,
                                             channels=1, device=_selected_device,
                                             dtype="float32").flatten()), 4),
                }))
                sys.stdout.flush()
            except TimeoutError:
                pass  # silence – no signal
            except Exception as exc:
                print(json.dumps({"error": str(exc)}))
                sys.stdout.flush()
    except KeyboardInterrupt:
        print("\nStopped.")
