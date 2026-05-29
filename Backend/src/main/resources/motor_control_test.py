# Manual test helper for checking motor-control behavior outside the full backend; it provides motor-control activity/logs for hardware verification.

#!/usr/bin/env python3
"""
Simple hardware test driver for motor_control.py.
Feeds known tuning states to each string motor so movement can be observed.
"""

import sys
import time

import motor_control

# Pause between commands so physical motion is easy to verify.
DELAY_BETWEEN_COMMANDS_S = 1.0

# (status, cents_off) test patterns applied to each string.
TEST_PATTERN = [
    ("FLAT", -15.0),
    ("FLAT", -15.0),
    ("FLAT", -15.0),
    ("FLAT", -15.0),
    ("FLAT", -15.0),
    ("FLAT", -15.0),
    ("FLAT", -15.0),
    ("FLAT", -15.0),
    ("FLAT", -15.0),
    ("SHARP", 15.0),
    ("IN-TUNE", 0.0),
]


def run_motor_test() -> None:
    """Run a basic movement test across all 6 motors."""
    print("[TEST] Starting motor_control test sequence", file=sys.stderr)
    print("[TEST] Pattern per string: FLAT -> SHARP -> IN-TUNE", file=sys.stderr)

    for string_number in range(1, 2):
        print(f"\n[TEST] String {string_number}", file=sys.stderr)

        for status, cents_off in TEST_PATTERN:
            print(
                f"[TEST] adjust_motor(string_number={string_number}, "
                f"status='{status}', cents_off={cents_off})",
                file=sys.stderr,
            )
            motor_control.adjust_motor(string_number, status, cents_off)
            time.sleep(DELAY_BETWEEN_COMMANDS_S)

    print("\n[TEST] Motor test sequence complete", file=sys.stderr)


def main() -> None:
    try:
        run_motor_test()
    except KeyboardInterrupt:
        print("\n[TEST] Interrupted by user", file=sys.stderr)


if __name__ == "__main__":
    main()
