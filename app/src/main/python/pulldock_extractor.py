"""Typed bridge between PullDock Kotlin and the pinned yt-dlp runtime.

Only plain JSON-compatible values cross this boundary. No network fetch of executable
code happens at runtime: yt-dlp is bundled at build time by Chaquopy (ADR-0012).
"""

import json
import os

from yt_dlp import YoutubeDL


def _safe_title(value):
    return (value or "media").strip()[:160]


def _clean(value):
    if value is None:
        return None
    text = str(value).strip()
    # yt-dlp uses the literal string "none" for an absent codec. Treating it as a
    # real codec made video-only formats look audio-bearing in the format sheet.
    if not text or text.lower() in ("null", "none"):
        return None
    return text


def _format_entry(fmt, duration=0):
    width = int(fmt.get("width") or 0)
    height = int(fmt.get("height") or 0)
    vcodec = _clean(fmt.get("vcodec"))
    acodec = _clean(fmt.get("acodec"))

    resolution = None
    if height > 0:
        resolution = f"{height}p"
    elif width > 0:
        resolution = f"{width}x{height}"
    elif vcodec is None and acodec is not None:
        resolution = "Audio"

    size = fmt.get("filesize") or fmt.get("filesize_approx") or 0
    if not size:
        # tbr is kilobits/second; estimate bytes for the known duration.
        tbr = float(fmt.get("tbr") or 0)
        if tbr > 0 and duration > 0:
            size = int(tbr * 1000.0 / 8.0 * duration)
    return {
        "id": _clean(fmt.get("format_id")) or "",
        "resolution": resolution,
        "container": _clean(fmt.get("ext")) or "mp4",
        "videoCodec": vcodec,
        "audioCodec": acodec,
        "estimatedBytes": int(size) if size and int(size) > 0 else None,
        # Progressive formats only in v1: no FFmpeg binary is bundled yet.
        "mergeRequired": vcodec is not None and acodec is None,
    }


def _options_common():
    return {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "no_color": True,
        "retries": 3,
        "socket_timeout": 30,
        "fragment_retries": 3,
    }


def _sort_key(entry):
    resolution = entry.get("resolution") or ""
    height = 0
    if resolution.endswith("p") and resolution[:-1].isdigit():
        height = int(resolution[:-1])
    is_audio = height == 0
    return (1 if is_audio else 0, -height, -(entry.get("estimatedBytes") or 0))


def _format_score(entry):
    size = entry.get("estimatedBytes") or 0
    return (
        1 if size > 0 else 0,
        1 if entry.get("container") == "mp4" else 0,
        1 if entry.get("videoCodec") else 0,
        1 if entry.get("audioCodec") else 0,
        size,
    )


def probe(url):
    """Return metadata + clean, deduplicated progressive formats as JSON."""
    try:
        opts = _options_common()
        with YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
        if not info:
            return json.dumps({"error": "NO_INFO"})

        duration = int(info.get("duration") or 0)
        # One best entry per user-visible resolution. Different codecs/containers at
        # the same resolution are not useful choices in a compact format sheet.
        unique = {}
        for fmt in info.get("formats") or []:
            if not fmt.get("url"):
                continue
            entry = _format_entry(fmt, duration)
            if not entry["id"]:
                continue
            # Video-only formats are valid choices: the Kotlin layer downloads a
            # separate audio stream and muxes them with Android MediaMuxer.
            if entry["resolution"] is None:
                continue
            # Drop storyboard/thumbnail-like micro-formats; real video starts at 144p.
            if entry["resolution"].endswith("p") and entry["resolution"][:-1].isdigit():
                if int(entry["resolution"][:-1]) < 120:
                    continue
            key = entry["resolution"]
            current = unique.get(key)
            if current is None or _format_score(entry) > _format_score(current):
                unique[key] = entry

        formats = sorted(unique.values(), key=_sort_key)

        # Fall back to the root format when the site exposes no format list.
        if not formats and info.get("url"):
            entry = _format_entry(info, duration)
            if entry["id"] and not entry["mergeRequired"] and entry["resolution"] is not None:
                formats.append(entry)

        return json.dumps({
            "title": _safe_title(info.get("title")),
            "durationSeconds": int(info.get("duration") or 0) or None,
            "thumbnailUrl": info.get("thumbnail"),
            "formats": formats,
        })
    except Exception as exc:  # sanitized: message only, never credentials/URLs
        return json.dumps({"error": type(exc).__name__, "message": str(exc)[:200]})


def _has_audio(path):
    """Detect an audio track without relying on ffprobe.

    WebM/Matroska stores codec ids near the start. MP4 may put the `moov` box at the
    end, so inspect both ends (and the whole file when it is small enough).
    """
    audio_tags = (b"A_OPUS", b"A_VORBIS", b"A_AAC", b"A_MPEG", b"mp4a", b"ac-3", b"ec-3")
    try:
        size = os.path.getsize(path)
        chunks = []
        with open(path, "rb") as handle:
            chunks.append(handle.read(8_000_000))
            if size > 8_000_000:
                handle.seek(max(0, size - 16_000_000))
                chunks.append(handle.read())
            if size <= 32_000_000:
                handle.seek(0)
                chunks.append(handle.read())
        return any(tag in chunk for chunk in chunks for tag in audio_tags)
    except Exception:
        return False


def _clear_outputs(output_path):
    directory = os.path.dirname(output_path)
    base = os.path.basename(output_path) + "."
    try:
        for name in os.listdir(directory):
            if name.startswith(base) and not name.endswith(".part"):
                try:
                    os.remove(os.path.join(directory, name))
                except OSError:
                    pass
    except OSError:
        pass


def _download_format(url, format_selector, output_path):
    template = output_path + ".%(ext)s"
    opts = _options_common()
    opts.update({
        "skip_download": False,
        "format": str(format_selector),
        "outtmpl": template,
        "overwrites": True,
        "noprogress": True,
        "concurrent_fragment_downloads": 2,
    })
    with YoutubeDL(opts) as ydl:
        ydl.extract_info(url, download=True)
    candidates = [
        os.path.join(os.path.dirname(output_path), path)
        for path in os.listdir(os.path.dirname(output_path))
        if path.startswith(os.path.basename(output_path) + ".")
        and not path.endswith(".part")
    ]
    if not candidates:
        return None
    return max(candidates, key=lambda candidate: os.path.getsize(candidate))


def download_muxed(url, video_format_id, audio_format_id, video_path, audio_path, height=0):
    """Download separate video and audio streams for MediaMuxer."""
    try:
        _clear_outputs(video_path)
        _clear_outputs(audio_path)
        video = _download_format(url, video_format_id, video_path)
        if not video:
            return json.dumps({"error": "NO_VIDEO"})
        audio = _download_format(url, audio_format_id, audio_path)
        if not audio:
            return json.dumps({"error": "NO_AUDIO"})
        return json.dumps({
            "video": video,
            "audio": audio,
            "videoBytes": os.path.getsize(video),
            "audioBytes": os.path.getsize(audio),
        })
    except Exception as exc:
        return json.dumps({"error": type(exc).__name__, "message": str(exc)[:200]})


def download(url, format_id, output_path, height=0):
    """Download a format and verify the result actually contains audio."""
    try:
        # A repeated download of the same URL must never pick a stale video-only
        # file from a previous attempt. Start from a clean temp path.
        _clear_outputs(output_path)
        path = _download_format(url, format_id, output_path)
        if path and not _has_audio(path):
            # The selected id may have resolved to a video-only variant. Retry with
            # a muxed audio+video format at the same or lower height.
            try:
                os.remove(path)
            except OSError:
                pass
            _clear_outputs(output_path)
            fallback = (
                f"best[vcodec!=none][acodec!=none][height<={int(height)}]/"
                "best[vcodec!=none][acodec!=none]/best[acodec!=none]"
                if int(height or 0) > 0
                else "best[vcodec!=none][acodec!=none]/best[acodec!=none]"
            )
            path = _download_format(url, fallback, output_path)
        if not path:
            return json.dumps({"error": "NO_OUTPUT"})
        if not _has_audio(path):
            return json.dumps({"error": "NO_AUDIO"})
        return json.dumps({"path": path, "bytes": os.path.getsize(path)})
    except Exception as exc:
        return json.dumps({"error": type(exc).__name__, "message": str(exc)[:200]})