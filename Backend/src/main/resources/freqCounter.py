# Reads or calculates frequency values from the hardware frequency counter path; it provides measured frequency values for tuner decisions.

#!/usr/bin/env python3
"""
High-accuracy frequency counter for Raspberry Pi 5.

Uses reciprocal counting: syncs to a rising edge, then counts as many
complete periods as possible within a 1-second window.  Timestamps are
captured with perf_counter_ns at the first and last observed edges,
giving sub-hertz accuracy for any signal ≥ 1 Hz.
"""

import fcntl
import os
import time

try:
	import RPi.GPIO as GPIO
except ImportError as exc:
	raise SystemExit(
		"RPi.GPIO is required. Install the Raspberry Pi GPIO package for your OS image."
	) from exc

GPIO_PIN = 3
MEASUREMENT_WINDOW_S = 1.0
LOCK_FILE = "/tmp/freqCounter_gpio3.lock"


def measure_frequency(pin: int, window_s: float = 1.0) -> tuple[int, int, float]:
	"""
	Measure frequency via reciprocal counting within a fixed time window.

	1. Sync to first rising edge  →  record start_ns
	2. Count every subsequent rising edge until the window expires
	3. Record end_ns on the *last* successfully detected edge

	Frequency = periods / ((end_ns − start_ns) / 1e9)

	Returns (periods, elapsed_ns, frequency_hz).
	Raises TimeoutError if no signal or < 1 complete period in the window.
	"""
	GPIO.setmode(GPIO.BCM)
	try:
		GPIO.setup(pin, GPIO.IN)
	except Exception as exc:
		if "GPIO busy" in str(exc):
			raise RuntimeError(
				f"GPIO {pin} is busy. Another process may be using this pin."
			) from exc
		raise

	try:
		timeout_ms = max(1, int(window_s * 1000))

		# ── align to a rising edge so timing starts cleanly ──
		if GPIO.input(pin) == GPIO.HIGH:
			ch = GPIO.wait_for_edge(pin, GPIO.FALLING, timeout=timeout_ms)
			if ch is None:
				raise TimeoutError("No signal detected (waiting for falling edge).")

		ch = GPIO.wait_for_edge(pin, GPIO.RISING, timeout=timeout_ms)
		if ch is None:
			raise TimeoutError("No signal detected (waiting for first rising edge).")

		start_ns = time.perf_counter_ns()
		deadline_ns = start_ns + int(window_s * 1_000_000_000)

		periods = 0
		end_ns = start_ns  # updated on every successful edge

		# ── count rising edges until the window expires ──
		while True:
			now_ns = time.perf_counter_ns()
			remaining_ns = deadline_ns - now_ns
			if remaining_ns <= 0:
				break

			remaining_ms = max(1, int(remaining_ns // 1_000_000))
			ch = GPIO.wait_for_edge(pin, GPIO.RISING, timeout=remaining_ms)
			if ch is None:
				break  # window expired while waiting

			end_ns = time.perf_counter_ns()
			periods += 1

		if periods == 0:
			raise TimeoutError(
				f"Could not complete one full period within {window_s:.1f}s. "
				f"Signal must be ≥ {1 / window_s:.1f} Hz."
			)

		elapsed_ns = end_ns - start_ns
		frequency_hz = periods / (elapsed_ns / 1_000_000_000)
		return periods, elapsed_ns, frequency_hz

	finally:
		try:
			GPIO.cleanup(pin)
		except Exception:
			pass  # don't mask the original exception


def main() -> None:
	lock_fd = os.open(LOCK_FILE, os.O_CREAT | os.O_RDWR, 0o644)
	try:
		try:
			fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
		except BlockingIOError:
			raise SystemExit(
				"Another freqCounter instance is already running on GPIO 3. "
				"Stop it first (Ctrl-C) and rerun."
			)

		print(f"Frequency counter — GPIO {GPIO_PIN}  "
		      f"(window {MEASUREMENT_WINDOW_S:.1f}s, Ctrl-C to stop)\n")
		print(f"{'Periods':>10}  {'Elapsed (ms)':>14}  {'Frequency (Hz)':>18}")
		print("-" * 48)

		while True:
			try:
				periods, elapsed_ns, freq = measure_frequency(
					GPIO_PIN, window_s=MEASUREMENT_WINDOW_S
				)
				elapsed_ms = elapsed_ns / 1_000_000
				print(f"{periods:>10d}  {elapsed_ms:>14.3f}  {freq:>18.6f}")
			except TimeoutError as exc:
				print(f"  ** {exc}")

	except KeyboardInterrupt:
		print("\nStopped.")
	except RuntimeError as exc:
		raise SystemExit(f"Measurement failed: {exc}")
	finally:
		try:
			fcntl.flock(lock_fd, fcntl.LOCK_UN)
		finally:
			os.close(lock_fd)


if __name__ == "__main__":
	main()
