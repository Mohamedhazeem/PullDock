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
    if not text or text.lower() == "null":
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
            if not entry["id"] or entry["mergeRequired"]:
                continue
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


def download(url, format_id, output_path):
    """Download one progressive format; returns the actual published temp path."""
    try:
        template = output_path + ".%(ext)s"
        opts = _options_common()
        opts.update({
            "skip_download": False,
            "format": str(format_id),
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
            return json.dumps({"error": "NO_OUTPUT"})
        path = max(candidates, key=lambda candidate: os.path.getsize(candidate))
        return json.dumps({"path": path, "bytes": os.path.getsize(path)})
    except Exception as exc:
        return json.dumps({"error": type(exc).__name__, "message": str(exc)[:200]})