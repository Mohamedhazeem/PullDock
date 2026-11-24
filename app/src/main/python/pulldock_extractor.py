"""Typed bridge between PullDock Kotlin and the pinned yt-dlp runtime.

Only plain JSON-compatible values cross this boundary. No network fetch of executable
code happens at runtime: yt-dlp is bundled at build time by Chaquopy (ADR-0012).
"""

import json
import os
import traceback

from yt_dlp import YoutubeDL


def _safe_title(value):
    return (value or "media").strip()[:160]


def _format_entry(fmt):
    width = fmt.get("width") or 0
    height = fmt.get("height") or 0
    resolution = None
    if height > 0:
        resolution = f"{height}p"
    elif width > 0:
        resolution = f"{width}x{height}"
    else:
        note = fmt.get("format_note") or ""
        if "audio only" in note.lower():
            resolution = None
    vcodec = fmt.get("vcodec") or "none"
    acodec = fmt.get("acodec") or "none"
    size = fmt.get("filesize") or fmt.get("filesize_approx") or 0
    return {
        "id": str(fmt.get("format_id") or ""),
        "resolution": resolution,
        "container": fmt.get("ext") or "mp4",
        "videoCodec": None if vcodec == "none" else vcodec,
        "audioCodec": None if acodec == "none" else acodec,
        "estimatedBytes": int(size) if size and int(size) > 0 else None,
        # Progressive formats only in v1: no FFmpeg binary is bundled yet.
        "mergeRequired": vcodec != "none" and acodec == "none",
        "protocol": fmt.get("protocol") or "",
    }


def _options_common():
    return {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "no_color": True,
        "retries": 2,
        "socket_timeout": 20,
    }


def probe(url):
    """Return metadata + selectable progressive formats as a JSON string."""
    try:
        opts = _options_common()
        with YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
        if not info:
            return json.dumps({"error": "NO_INFO"})
        formats = []
        for fmt in info.get("formats") or []:
            if not fmt.get("url"):
                continue
            entry = _format_entry(fmt)
            if entry["mergeRequired"] or not entry["id"]:
                continue
            formats.append(entry)
        # Fall back to the root format when the site exposes no format list.
        if not formats and info.get("url"):
            formats.append(_format_entry(info))
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
            path for path in os.listdir(os.path.dirname(output_path))
            if path.startswith(os.path.basename(output_path) + ".")
        ]
        if not candidates:
            return json.dumps({"error": "NO_OUTPUT"})
        path = os.path.join(os.path.dirname(output_path), candidates[0])
        return json.dumps({"path": path, "bytes": os.path.getsize(path)})
    except Exception as exc:
        return json.dumps({"error": type(exc).__name__, "message": str(exc)[:200]})
