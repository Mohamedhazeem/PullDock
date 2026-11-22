# Contract: Torrent Streaming Bridge

**Modules**: `:domain:torrent` (handle) · `:feature:player` (lifecycle owner) · `:data:torrent` (bridge)
**Source**: [plan.md](../plan.md) §Storage and Media Architecture

The bridge lets a user play an eligible media file while its torrent is still downloading.
It is a local-only HTTP server; it is not a public or LAN service.

## Lifecycle

```kotlin
interface StreamingBridgeOwner {
    /** Called when playback starts; bridge is created if the file is eligible. */
    suspend fun open(handle: StreamingHandle): StreamingSession

    /** Called when playback stops or the player is destroyed. */
    fun close(session: StreamingSession)
}

interface StreamingSession {
    val url: String                 // http://127.0.0.1:<port>/<token>/<fileIndex>
    val fileIndex: Int
    val token: String               // unpredictable, per-session
    fun byteRangeSupported(): Boolean
    fun close()
}
```

## Security requirements

- Bind to `127.0.0.1` (loopback) only. MUST NOT bind `0.0.0.0` or a LAN address.
- Per-session unpredictable token in the path; requests without it are rejected.
- Support HTTP byte-range for seeking.
- Reject path traversal (`..`, absolute paths, encoded separators).
- Serve only the requested file index from the torrent's file list.
- Enforce a bounded read ahead; never serve unwritten pieces.
- Shut down when playback stops, the player is destroyed, or the torrent is removed.
- Release the handle before the engine session closes.

## Behavior

- Eligibility: the file must be a supported media type and the torrent must have sequential
  data available at the requested offset.
- Seeking beyond available data returns a range error; the player shows buffering.
- Peer disconnect or missing pieces pause playback; the bridge does not fabricate data.
- No authentication beyond the loopback binding and token; no external exposure.
