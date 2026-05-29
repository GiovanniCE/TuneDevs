# Runs a mock WebSocket server that emits simulated tuner updates; it provides fake tuning messages for testing without backend hardware.

import asyncio
import json
import math
import random
import time
from typing import Dict, Any

import websockets
from websockets.server import WebSocketServerProtocol

# Usage:
#   pip install websockets
#   python tools/mock_ws_server.py
#
# WebSocket endpoint:
#   ws://localhost:8765/ws

TARGETS = [
    {"string": "E", "note": "E2", "hz": 82.41},
    {"string": "A", "note": "A2", "hz": 110.00},
    {"string": "D", "note": "D3", "hz": 146.83},
    {"string": "G", "note": "G3", "hz": 196.00},
    {"string": "B", "note": "B3", "hz": 246.94},
    {"string": "e", "note": "E4", "hz": 329.63},
]


def cents_from_ratio(detected_hz: float, target_hz: float) -> float:
    # cents = 1200 * log2(detected/target)
    return 1200.0 * math.log(detected_hz / target_hz, 2)


def ratio_from_cents(cents: float) -> float:
    # ratio = 2^(cents/1200)
    return 2 ** (cents / 1200.0)


def status_from_cents(cents: float, deadzone: float = 5.0) -> str:
    if abs(cents) <= deadzone:
        return "in_tune"
    return "flat" if cents < 0 else "sharp"


def make_message(target: Dict[str, Any], cents: float) -> Dict[str, Any]:
    detected_hz = target["hz"] * ratio_from_cents(cents)
    return {
        "type": "tuning_update",
        "timestamp": int(time.time()),
        "string": target["string"],
        "note": target["note"],
        "targetHz": float(target["hz"]),
        "detectedHz": round(float(detected_hz), 2),
        "centsOff": round(float(cents), 2),
        "status": status_from_cents(cents),
    }


async def ws_handler(ws: WebSocketServerProtocol) -> None:
    # Each client gets its own simulation
    target = random.choice(TARGETS)

    # Start somewhat out of tune
    cents = random.uniform(-30.0, 30.0)

    # Pick a "tuning style": sometimes user overshoots a bit
    overshoot_bias = random.uniform(-2.0, 2.0)

    print(f"[client] connected -> target {target['string']} {target['note']} ({target['hz']}Hz)")

    try:
        while True:
            await asyncio.sleep(0.10)

            # Move toward 0 cents (simulate tuning peg adjustments)
            # Multiplicative decay toward 0
            cents *= 0.93

            # Add mild overshoot tendency
            cents += overshoot_bias * 0.02

            # Add small random noise (simulates detection jitter)
            cents += random.uniform(-0.8, 0.8)

            msg = make_message(target, cents)
            await ws.send(json.dumps(msg))

            # Occasionally switch to a new string to simulate user changing strings
            if random.random() < 0.002:
                target = random.choice(TARGETS)
                cents = random.uniform(-25.0, 25.0)
                overshoot_bias = random.uniform(-2.0, 2.0)
    except websockets.ConnectionClosed:
        print("[client] disconnected")


async def router(ws: WebSocketServerProtocol, path: str) -> None:
    # Only serve /ws; reject others to make debugging easier.
    if path != "/ws":
        await ws.close(code=1008, reason="Use /ws")
        return
    await ws_handler(ws)


async def main() -> None:
    host = "0.0.0.0"
    port = 8765
    print(f"Mock WS server: ws://localhost:{port}/ws")
    async with websockets.serve(router, host, port, ping_interval=20):
        await asyncio.Future()  # run forever


if __name__ == "__main__":
    asyncio.run(main())