# WebSocket Message Schema (Tuning Updates)

## Endpoint
- `ws://<host>:<port>/ws`

## Message Type
All messages are JSON.

### `tuning_update`
Example:
```json
{
  "type": "tuning_update",
  "timestamp": 1710000000,
  "string": "A",
  "note": "A2",
  "targetHz": 110.0,
  "detectedHz": 109.2,
  "centsOff": -12.3,
  "status": "flat"
}