# Coordinates tuner frequency detection and motor-control decisions for hardware mode; it provides jSON tuning events consumed by the Java backend.

#!/usr/bin/env python3
"""
Guitar tuner for Raspberry Pi 5.

Reads an amplified guitar signal on GPIO pin 3, identifies the closest
string from a given tuning, and reports whether it is sharp, flat, or
in-tune.
"""

import math
import json
import os
import sys

# Select signal source based on TUNER_INPUT environment variable:
#   "gpio"  – Raspberry Pi GPIO pin (default, requires RPi.GPIO)
#   "audio" – USB audio interface (requires sounddevice + numpy)
_input_mode = os.environ.get("TUNER_INPUT", "gpio").lower()

if _input_mode == "audio":
    from audio_interface import measure_frequency, select_device as _select_audio_device
    # Auto-select the best available audio device on startup
    try:
        _dev = _select_audio_device()
        print(json.dumps({"event": "audio_device_selected", "device": _dev}))
        sys.stdout.flush()
    except Exception as _exc:
        print(json.dumps({"error": f"Audio device init failed: {_exc}"}))
        sys.stdout.flush()
else:
    from freqCounter import measure_frequency

import motor_control

# ── GPIO & timing constants ─────────────────────────────────────────
GPIO_PIN = 3
MEASUREMENT_WINDOW_S = 0.1      # passed to measure_frequency
IN_TUNE_THRESHOLD_CENTS = 5.0   # ±cents to be considered "in tune"
MIN_FUNDAMENTAL_HZ = 70.0
MAX_FUNDAMENTAL_HZ = 360.0
MAX_HARMONIC = 6
MAX_ASSIGNMENT_CENTS = 45.0
SWITCH_CONFIRMATION_CYCLES = 4
SMOOTHING_ALPHA = 0.35

# ── Standard tuning (E A D G B E) ───────────────────────────────────
# Each entry: (string_number, note_name, target_frequency_hz)
STANDARD_TUNING = [
    (6, "E2",  82.41),
    (5, "A2", 110.00),
    (4, "D3", 146.83),
    (3, "G3", 196.00),
    (2, "B3", 246.94),
    (1, "E4", 329.63),
]
NONSTANDARD_TUNING = [
    (6, "D2", 73.42),
    (5, "G2", 98.00),
    (4, "C3", 130.81),
    (3, "F3", 174.61),
    (2, "A#3", 233.08),
    (1, "D4", 293.66),
]

tuning = STANDARD_TUNING

last_retrieved_data = None

_auto_tune_enabled = False
_active_string_number = None
_switch_candidate = None
_switch_candidate_count = 0
_smoothed_frequency_by_string = {}


def set_auto_tune(enabled: bool) -> None:
    """Enable or disable automatic motor-driven tuning."""
    global _auto_tune_enabled
    _auto_tune_enabled = enabled


def is_auto_tune_enabled() -> bool:
    """Return whether automatic motor tuning is currently enabled."""
    return _auto_tune_enabled

def get_last_retrieved_data() -> tuple[int, str, float, float, float] | None:
    """Return the most recently identified string data, or None if not available."""
    return last_retrieved_data

def set_tuning(new_tuning: list[tuple[int, str, float]]) -> None:
    """Set a new tuning. Each entry should be (string_number, note_name, target_hz)."""
    global tuning
    tuning = new_tuning

def cents_difference(measured_hz: float, target_hz: float) -> float:
    """Return the difference in cents between measured and target frequencies."""
    return 1200.0 * math.log2(measured_hz / target_hz)


def _estimate_for_string(measured_hz: float, target_hz: float) -> tuple[float, float, int] | None:
    """Estimate a string fundamental from the measured signal, accounting for harmonics."""
    best_estimate = None
    for harmonic in range(1, MAX_HARMONIC + 1):
        fundamental_hz = measured_hz / harmonic
        if not (MIN_FUNDAMENTAL_HZ <= fundamental_hz <= MAX_FUNDAMENTAL_HZ):
            continue

        cents_off = cents_difference(fundamental_hz, target_hz)
        if best_estimate is None or abs(cents_off) < abs(best_estimate[1]):
            best_estimate = (fundamental_hz, cents_off, harmonic)

    return best_estimate


def identify_string(
    measured_hz: float,
) -> tuple[int, str, float, float, float, int] | None:
    """
    Find the tuning entry whose target frequency is closest (in cents)
    to the measured frequency.

    Returns (string_number, note_name, target_hz, estimated_fundamental_hz, cents_off, harmonic).
    A positive cents_off means SHARP; negative means FLAT.
    """
    global last_retrieved_data
    best = None
    for string_num, note, target_hz in tuning:
        estimate = _estimate_for_string(measured_hz, target_hz)
        if estimate is None:
            continue

        fundamental_hz, off, harmonic = estimate
        if best is None or abs(off) < abs(best[4]):
            best = (string_num, note, target_hz, fundamental_hz, off, harmonic)

    if best is None or abs(best[4]) > MAX_ASSIGNMENT_CENTS:
        return None

    last_retrieved_data = (best[0], best[1], best[2], best[3], best[4])
    return best


def _select_active_string(candidate_string_number: int) -> int:
    """Keep one active string until another candidate is consistently detected."""
    global _active_string_number, _switch_candidate, _switch_candidate_count

    if _active_string_number is None:
        _active_string_number = candidate_string_number
        _switch_candidate = None
        _switch_candidate_count = 0
        return _active_string_number

    if candidate_string_number == _active_string_number:
        _switch_candidate = None
        _switch_candidate_count = 0
        return _active_string_number

    if _switch_candidate != candidate_string_number:
        _switch_candidate = candidate_string_number
        _switch_candidate_count = 1
    else:
        _switch_candidate_count += 1

    if _switch_candidate_count >= SWITCH_CONFIRMATION_CYCLES:
        _active_string_number = candidate_string_number
        _switch_candidate = None
        _switch_candidate_count = 0

    return _active_string_number


def _smooth_frequency(string_number: int, estimated_hz: float) -> float:
    """Apply light smoothing to improve stability under noisy guitar input."""
    previous = _smoothed_frequency_by_string.get(string_number)
    if previous is None:
        smoothed = estimated_hz
    else:
        smoothed = (SMOOTHING_ALPHA * estimated_hz) + ((1.0 - SMOOTHING_ALPHA) * previous)

    _smoothed_frequency_by_string[string_number] = smoothed
    return smoothed


def _get_tuning_entry(string_number: int) -> tuple[int, str, float] | None:
    """Return a tuning tuple for a given string number."""
    for entry in tuning:
        if entry[0] == string_number:
            return entry
    return None


def status_label(cents_off: float, threshold: float = IN_TUNE_THRESHOLD_CENTS) -> str:
    """Return a human-readable status: SHARP, FLAT, or IN-TUNE."""
    if cents_off > threshold:
        return "SHARP"
    elif cents_off < -threshold:
        return "FLAT"
    return "IN-TUNE"


def tuner_loop() -> None:
    """
    Main loop: measure frequency, identify string, report status.
    Repeats every LOOP_INTERVAL_S seconds.
    """

    cycle = 0
    motor_control.initialize_motors()  # Ensure motors are ready before starting loop
    while True:
        cycle += 1

        try:
            _, _, measured_hz = measure_frequency(
                GPIO_PIN, window_s=MEASUREMENT_WINDOW_S
            )

            identified = identify_string(measured_hz)
            if identified is None:
                data = {
                    "cycle": cycle,
                    "frequency": measured_hz,
                    "status": "NO-SIGNAL",
                    "reason": "no_guitar_string_match"
                }
                print(json.dumps(data))
                sys.stdout.flush()
                continue

            candidate_string, _, _, _, _, _ = identified
            active_string = _select_active_string(candidate_string)

            active_entry = _get_tuning_entry(active_string)
            if active_entry is None:
                continue

            string_num, note, target_hz = active_entry
            active_estimate = _estimate_for_string(measured_hz, target_hz)
            if active_estimate is None:
                continue

            estimated_hz, _, harmonic = active_estimate
            filtered_hz = _smooth_frequency(string_num, estimated_hz)
            cents_off = cents_difference(filtered_hz, target_hz)
            status = status_label(cents_off)

            data = {
                "cycle": cycle,
                "frequency": filtered_hz,
                "raw_frequency": measured_hz,
                "note": note,
                "cents": cents_off,
                "status": status,
                "target_frequency": target_hz,
                "string_number": string_num,
                "harmonic": harmonic,
                "candidate_string": candidate_string,
                "active_string": active_string,
            }
            print(json.dumps(data))
            sys.stdout.flush()

            if _auto_tune_enabled:
                motor_control.adjust_motor(string_num, status, cents_off)

        #debug info
        except TimeoutError as exc:
            pass # Silence timeout errors in JSON stream
        except Exception as exc:
            print(json.dumps({"error": str(exc)}))
            sys.stdout.flush()



def main() -> None:
    set_tuning(STANDARD_TUNING)
    try:
        tuner_loop()
    except KeyboardInterrupt:
        print("\nTuner stopped.")


if __name__ == "__main__":
    main()
