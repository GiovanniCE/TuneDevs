# Controls the tuning motor from the Python hardware tuner scripts; it provides gPIO/PWM signals that drive the motor hardware.

#!/usr/bin/env python3
"""
ULN2003 stepper motor controller for 6-string auto-tuning guitar.
Controls stepper motors via GPIO on Raspberry Pi 5.
"""

import sys
import time
import threading

try:
    import RPi.GPIO as GPIO
    GPIO_AVAILABLE = True
except (ImportError, RuntimeError):
    GPIO_AVAILABLE = False
    print("[WARNING] RPi.GPIO not available - motor control disabled", file=sys.stderr)

# ============================================================================
# MODULE-WIDE CONSTANTS - ADJUSTABLE DEFAULT DIRECTIONS
# ============================================================================
# Set these to CLOCKWISE or COUNTERCLOCKWISE to define default tightening direction
MOTOR_1_DEFAULT_DIRECTION = "CLOCKWISE"      # String 1 (High E)
MOTOR_2_DEFAULT_DIRECTION = "CLOCKWISE"      # String 2 (B)
MOTOR_3_DEFAULT_DIRECTION = "CLOCKWISE"      # String 3 (G)
MOTOR_4_DEFAULT_DIRECTION = "COUNTERCLOCKWISE"  # String 4 (D)
MOTOR_5_DEFAULT_DIRECTION = "COUNTERCLOCKWISE"  # String 5 (A)
MOTOR_6_DEFAULT_DIRECTION = "COUNTERCLOCKWISE"  # String 6 (Low E)

# ============================================================================
# PIN MAPPINGS FOR ULN2003 STEPPER DRIVERS (GPIO pins, avoiding GPIO3)
# ============================================================================
# Each motor uses 4 GPIO pins connected to IN1, IN2, IN3, IN4 of ULN2003
MOTOR_PINS = {
    1: [4, 17, 27, 22],        # Motor 1: GPIO4, GPIO17, GPIO27, GPIO22
    2: [10, 9, 11, 5],         # Motor 2: GPIO10, GPIO9, GPIO11, GPIO5
    3: [6, 12, 13, 19],        # Motor 3: GPIO6, GPIO12, GPIO13, GPIO19
    4: [26, 16, 20, 21],       # Motor 4: GPIO26, GPIO16, GPIO20, GPIO21
    5: [2, 7, 8, 14],          # Motor 5: GPIO2, GPIO7, GPIO8, GPIO14
    6: [15, 18, 23, 24],       # Motor 6: GPIO15, GPIO18, GPIO23, GPIO24
}

DEFAULT_DIRECTIONS = {
    1: MOTOR_1_DEFAULT_DIRECTION,
    2: MOTOR_2_DEFAULT_DIRECTION,
    3: MOTOR_3_DEFAULT_DIRECTION,
    4: MOTOR_4_DEFAULT_DIRECTION,
    5: MOTOR_5_DEFAULT_DIRECTION,
    6: MOTOR_6_DEFAULT_DIRECTION,
}

# Motor control parameters
STEP_DELAY = 0.01  # Delay between steps (seconds)
STEPS_PER_CENT = 2  # Steps to rotate per cent of detuning
MAX_STEPS_PER_COMMAND = 200

# Half-step sequence for 28BYJ-48 stepper motor with ULN2003
STEPPER_SEQUENCE = [
    [1, 0, 0, 0],
    [1, 1, 0, 0],
    [0, 1, 0, 0],
    [0, 1, 1, 0],
    [0, 0, 1, 0],
    [0, 0, 1, 1],
    [0, 0, 0, 1],
    [1, 0, 0, 1],
]

# Global motor state
_motors_initialized = False
_motor_step_counters = {i: 0 for i in range(1, 7)}
_motor_commands = {i: ("CLOCKWISE", 0) for i in range(1, 7)}
_active_motor = None
_command_lock = threading.Lock()
_worker_thread = None
_worker_stop_event = threading.Event()


def _init_gpio():
    """Initialize GPIO pins for all motors."""
    global _motors_initialized

    if not GPIO_AVAILABLE or _motors_initialized:
        return

    try:
        GPIO.setmode(GPIO.BCM)
        GPIO.setwarnings(False)

        for motor_num, pins in MOTOR_PINS.items():
            for pin in pins:
                GPIO.setup(pin, GPIO.OUT, initial=GPIO.LOW)

        _motors_initialized = True
        print("[INFO] GPIO initialized for 6 stepper motors", file=sys.stderr)
    except Exception as e:
        print(f"[ERROR] Failed to initialize GPIO: {e}", file=sys.stderr)
        _motors_initialized = False


def _cleanup_gpio():
    """Clean up GPIO resources."""
    global _motors_initialized, _worker_thread

    _worker_stop_event.set()
    if _worker_thread is not None and _worker_thread.is_alive():
        _worker_thread.join(timeout=1.0)
    _worker_thread = None

    if not GPIO_AVAILABLE or not _motors_initialized:
        return

    try:
        GPIO.cleanup()
        _motors_initialized = False
    except Exception as e:
        print(f"[ERROR] Failed to cleanup GPIO: {e}", file=sys.stderr)


def _motor_worker_loop() -> None:
    """Background loop that executes pending motor commands one motor at a time."""
    global _active_motor

    while not _worker_stop_event.is_set():
        did_work = False
        motor_num = None
        direction = "CLOCKWISE"

        with _command_lock:
            if _active_motor is not None:
                active_direction, active_steps = _motor_commands[_active_motor]
                if active_steps > 0:
                    _motor_commands[_active_motor] = (active_direction, active_steps - 1)
                    motor_num = _active_motor
                    direction = active_direction
                else:
                    _active_motor = None

            if motor_num is None:
                for candidate in range(1, 7):
                    candidate_direction, candidate_steps = _motor_commands[candidate]
                    if candidate_steps > 0:
                        _active_motor = candidate
                        _motor_commands[candidate] = (candidate_direction, candidate_steps - 1)
                        motor_num = candidate
                        direction = candidate_direction
                        break

        if motor_num is not None:
            _step_motor(motor_num, direction)
            did_work = True

        if not did_work:
            time.sleep(0.005)


def _start_worker_if_needed() -> None:
    """Start motor worker thread if not already running."""
    global _worker_thread

    if _worker_thread is not None and _worker_thread.is_alive():
        return

    _worker_stop_event.clear()
    _worker_thread = threading.Thread(target=_motor_worker_loop, daemon=True)
    _worker_thread.start()


def initialize_motors() -> None:
    """Initialize GPIO and start the non-blocking motor worker."""
    _init_gpio()
    _start_worker_if_needed()


def _queue_motor_command(string_number: int, direction: str, num_steps: int) -> None:
    """Set latest command for one motor and clear movement for all others."""
    global _active_motor

    initialize_motors()
    with _command_lock:
        for motor_num in _motor_commands:
            if motor_num != string_number:
                _motor_commands[motor_num] = ("CLOCKWISE", 0)
        _motor_commands[string_number] = (direction, max(0, num_steps))
        _active_motor = string_number


def _step_motor(motor_num: int, direction: str) -> None:
    """
    Execute one stepper step for the specified motor.

    Parameters
    ----------
    motor_num : int   Motor number (1-6)
    direction : str   "CLOCKWISE" or "COUNTERCLOCKWISE"
    """
    if not GPIO_AVAILABLE or motor_num not in MOTOR_PINS:
        return

    _init_gpio()

    try:
        pins = MOTOR_PINS[motor_num]
        step_idx = _motor_step_counters[motor_num]

        # Select sequence direction
        if direction == "CLOCKWISE":
            sequence = STEPPER_SEQUENCE[step_idx]
        else:
            sequence = STEPPER_SEQUENCE[-(step_idx + 1)]

        # Apply sequence to GPIO pins
        for pin, state in zip(pins, sequence):
            GPIO.output(pin, state)

        # Advance step counter
        _motor_step_counters[motor_num] = (step_idx + 1) % len(STEPPER_SEQUENCE)
        time.sleep(STEP_DELAY)

    except Exception as e:
        print(f"[ERROR] Motor {motor_num} step failed: {e}", file=sys.stderr)


def _rotate_motor(motor_num: int, direction: str, num_steps: int) -> None:
    """
    Rotate motor by specified number of steps.

    Parameters
    ----------
    motor_num : int   Motor number (1-6)
    direction : str   "CLOCKWISE" or "COUNTERCLOCKWISE"
    num_steps : int   Number of steps to rotate
    """
    for _ in range(num_steps):
        _step_motor(motor_num, direction)


def adjust_motor(string_number: int, status: str, cents_off: float) -> None:
    """
    Command the motor for a given string based on its tuning status.

    Parameters
    ----------
    string_number : int   (1-6)
    status        : str   "SHARP", "FLAT", or "IN-TUNE"
    cents_off     : float signed cents offset from target
    """
    if string_number not in range(1, 7):
        print(f"[ERROR] Invalid string number: {string_number}", file=sys.stderr)
        return

    default_direction = DEFAULT_DIRECTIONS[string_number]
    num_steps = _compute_step_count(cents_off)

    if status == "SHARP":
        _loosen(string_number, cents_off, default_direction, num_steps)
    elif status == "FLAT":
        _tighten(string_number, cents_off, default_direction, num_steps)
    else:
        _hold(string_number)


def _compute_step_count(cents_off: float) -> int:
    """Compute a proportional, bounded step count from tuning error in cents."""
    absolute_cents = abs(cents_off)
    base_steps = absolute_cents * STEPS_PER_CENT

    # Smaller gains near target reduce oscillation; larger gains speed coarse correction.
    if absolute_cents < 12.0:
        base_steps *= 0.6
    elif absolute_cents > 40.0:
        base_steps *= 1.3

    return max(0, min(MAX_STEPS_PER_COMMAND, int(base_steps)))


def _tighten(string_number: int, cents_off: float, default_direction: str, num_steps: int) -> None:
    """
    Tighten the string (raise pitch when flat).
    Rotates motor in the default tightening direction.
    """
    print(f"  [Motor {string_number}] TIGHTEN  — string is {abs(cents_off):.2f}¢ flat", file=sys.stderr)

    if num_steps > 0:
        _queue_motor_command(string_number, default_direction, num_steps)
        print(f"    → Queued {num_steps} steps {default_direction}", file=sys.stderr)


def _loosen(string_number: int, cents_off: float, default_direction: str, num_steps: int) -> None:
    """
    Loosen the string (lower pitch when sharp).
    Rotates motor in the opposite direction of default.
    """
    print(f"  [Motor {string_number}] LOOSEN   — string is {abs(cents_off):.2f}¢ sharp", file=sys.stderr)

    if num_steps > 0:
        opposite_direction = "COUNTERCLOCKWISE" if default_direction == "CLOCKWISE" else "CLOCKWISE"
        _queue_motor_command(string_number, opposite_direction, num_steps)
        print(f"    → Queued {num_steps} steps {opposite_direction}", file=sys.stderr)


def _hold(string_number: int) -> None:
    """String is in tune — no motor movement."""
    global _active_motor

    initialize_motors()
    with _command_lock:
        _motor_commands[string_number] = (DEFAULT_DIRECTIONS[string_number], 0)
        if _active_motor == string_number:
            _active_motor = None

    print(f"  [Motor {string_number}] HOLD     — string is in tune", file=sys.stderr)


# Ensure GPIO is cleaned up on exit
import atexit
atexit.register(_cleanup_gpio)
