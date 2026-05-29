# Prototype motor-control script for tuner hardware experimentation; it provides gPIO/PWM signals intended to move the tuning motor.

#!/usr/bin/env python3
"""
Mock motor controller for the auto-tuning guitar.

Each of the 6 guitar strings has a dedicated motor.  In real hardware
the motor would physically turn the tuning peg; here we just print what
would happen so the rest of the system can be tested without hardware.

Public API
──────────
  adjust_motor(string_number, status, cents_off)
      Drive the motor for *string_number* (1-6) in the direction
      indicated by *status* ("SHARP", "FLAT", or "IN-TUNE").
"""


def adjust_motor(string_number: int, status: str, cents_off: float) -> None:
    """
    Command the motor for a given string based on its tuning status.

    Parameters
    ----------
    string_number : int   (1-6)
    status        : str   "SHARP", "FLAT", or "IN-TUNE"
    cents_off     : float signed cents offset from target
    """
    if status == "SHARP":
        _loosen(string_number, cents_off)
    elif status == "FLAT":
        _tighten(string_number, cents_off)
    else:
        _hold(string_number)


def _tighten(string_number: int, cents_off: float) -> None:
    """Simulate tightening the peg (pitch is flat → need to raise it)."""
    print(f"  [Motor {string_number}] TIGHTEN  — string is {abs(cents_off):.2f}¢ flat")


def _loosen(string_number: int, cents_off: float) -> None:
    """Simulate loosening the peg (pitch is sharp → need to lower it)."""
    print(f"  [Motor {string_number}] LOOSEN   — string is {abs(cents_off):.2f}¢ sharp")


def _hold(string_number: int) -> None:
    """String is in tune — no motor movement."""
    print(f"  [Motor {string_number}] HOLD     — string is in tune")
