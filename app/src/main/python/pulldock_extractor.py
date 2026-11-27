"""Typed bridge between PullDock Kotlin and the pinned yt-dlp runtime.

Only plain JSON-compatible values cross this boundary. No network fetch of executable
code happens at runtime: yt-dlp is bundled at build time by Chaquopy (ADR-0012).
"""

import json
import os
import re
import socket
import html as html_lib
import ipaddress
import urllib.parse
import urllib.request
import urllib.error

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


# A real media payload is far larger than an HTML/redirect error page (~110 bytes).
# Anything below this floor, or carrying HTML markers, is a failed extraction and must
# never be published as a "completed" download.
_MIN_MEDIA_BYTES = 100 * 1024
_HTML_MARKERS = (b"<!doctype html", b"<html", b"<head", b"<body", b"<?xml")


def _looks_like_html(path):
    try:
        with open(path, "rb") as handle:
            head = handle.read(1024).lower()
        return any(marker in head for marker in _HTML_MARKERS)
    except OSError:
        return False


def _reject_if_junk(path):
    """Return a reason string when the file is too small or HTML, else None."""
    try:
        if os.path.getsize(path) < _MIN_MEDIA_BYTES:
            return "too small"
    except OSError:
        return "missing"
    if _looks_like_html(path):
        return "html"
    return None


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
    # Some providers (notably X) hide codec metadata on progressive HTTP formats.
    # The file is muxed; mark it usable and let the download-time audio check verify it.
    if resolution and resolution != "Audio" and vcodec is None and acodec is None:
        vcodec = "unknown"
        acodec = "unknown"
    # Instagram/Facebook expose a default muxed format without resolution metadata.
    # Keep it as a selectable "Unknown" option instead of dropping it.
    if resolution is None and vcodec is None and acodec is None:
        note = str(fmt.get("format_note") or "").lower()
        is_audio = "audio" in note or str(fmt.get("format_id") or "").startswith("hls-audio")
        if not is_audio:
            resolution = "Unknown"
            vcodec = "unknown"
            acodec = "unknown"
    return {
        "id": _clean(fmt.get("format_id")) or "",
        "resolution": resolution,
        "container": _clean(fmt.get("ext")) or "mp4",
        "videoCodec": vcodec,
        "audioCodec": acodec,
        "estimatedBytes": int(size) if size and int(size) > 0 else None,
        "language": _clean(fmt.get("language")),
        "languagePreference": fmt.get("language_preference"),
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


def _options_playlist():
    # Flat extraction enumerates a playlist's entries without downloading or probing
    # each video's formats. `noplaylist` is intentionally omitted so a playlist URL is
    # expanded (the single-video `probe` path keeps `noplaylist: True`).
    return {
        "quiet": True,
        "no_warnings": True,
        "extract_flat": True,
        "skip_download": True,
        "no_color": True,
        "socket_timeout": 30,
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
    video = entry.get("videoCodec") or ""
    audio = entry.get("audioCodec") or ""
    return (
        1 if size > 0 else 0,
        1 if video and audio and not entry.get("mergeRequired") else 0,
        1 if (entry.get("languagePreference") or -1) > 0 else 0,
        1 if (entry.get("language") or "").lower().startswith("en") else 0,
        1 if "avc1" in video else 0,
        1 if "mp4a" in audio else 0,
        1 if entry.get("container") == "mp4" else 0,
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

        # yt-dlp produced nothing usable: try the generic HTML sniffer (ADR-0013).
        if not formats:
            return json.dumps(_sniff(url))

        return json.dumps({
            "title": _safe_title(info.get("title")),
            "durationSeconds": int(info.get("duration") or 0) or None,
            "thumbnailUrl": info.get("thumbnail"),
            "formats": formats,
        })
    except Exception as exc:  # sanitized: never leak credentials/URLs
        return json.dumps(_sniff(url, cause=type(exc).__name__))


def playlist(url):
    """Enumerate a playlist's entries (flat, no download). NOT_PLAYLIST for a single video."""
    try:
        opts = _options_playlist()
        with YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
        if not info:
            return json.dumps({"error": "NO_INFO"})
        entries = info.get("entries")
        if entries is None:
            return json.dumps({"error": "NOT_PLAYLIST"})
        items = []
        for index, entry in enumerate(entries):
            if not entry:
                continue
            entry_url = entry.get("url") or entry.get("webpage_url")
            if not entry_url and entry.get("id"):
                entry_url = "https://www.youtube.com/watch?v=" + str(entry.get("id"))
            if not entry_url:
                continue
            if entry_url.startswith("//"):
                entry_url = "https:" + entry_url
            items.append({
                "index": index,
                "title": _safe_title(entry.get("title")),
                "url": entry_url,
                "durationSeconds": int(entry.get("duration") or 0) or None,
                "thumbnailUrl": entry.get("thumbnail"),
            })
        return json.dumps({
            "title": _safe_title(info.get("title")),
            "entries": items,
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


# --- Fallback layer (ADR-0013): generic HTML sniffer + HLS/direct downloaders -----------
# Used only when yt-dlp cannot extract a URL. Sniffing/direct download use the stdlib;
# hlsfetch (lazy-imported) handles .m3u8. No credentials/cookies are ever forwarded.

_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
_SNIFF_MAX_BYTES = 5 * 1024 * 1024
_URL_RE = re.compile(r'(?:https?:)?//[^\s"\'<>\\]+?\.(?:mp4|m3u8)(?:\?[^\s"\'<>]*)?', re.I)
_OG_VIDEO_RE = re.compile(
    r'<meta[^>]+(?:property|name)=["\']og:video["\'][^>]+content=["\']([^"\']+)["\']', re.I
)
_SRC_RE = re.compile(r'<(?:video|source)[^>]+src=["\']([^"\']+)["\']', re.I)
_HEIGHT_RE = re.compile(r'(\d{3,4})p')


def _is_blocked_host(url):
    """Best-effort SSRF guard: reject loopback/private/link-local hosts before any request."""
    try:
        host = (urllib.parse.urlparse(url).hostname or "").lower()
    except ValueError:
        return True
    if not host or host == "localhost" or host.endswith(".localhost") or host == "0.0.0.0":
        return True
    try:
        ip = ipaddress.ip_address(host)
        return (ip.is_private or ip.is_loopback or ip.is_link_local
                or ip.is_reserved or ip.is_unspecified)
    except ValueError:
        return False


def _safe_remove(path):
    try:
        os.remove(path)
    except OSError:
        pass


class _SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Re-validate the host on every redirect hop (a public URL may 302 to an internal one)."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if _is_blocked_host(newurl):
            raise urllib.error.HTTPError(newurl, code, "blocked redirect", headers, fp)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


_OPENER = urllib.request.build_opener(_SafeRedirectHandler())


def _is_network_error(exc):
    """True for transport failures (offline/DNS/timeout), False for HTTP 4xx/5xx."""
    if isinstance(exc, urllib.error.HTTPError):
        return False
    if isinstance(exc, (urllib.error.URLError, socket.timeout, TimeoutError, ConnectionError)):
        return True
    message = str(exc).lower()
    return any(token in message for token in (
        "timed out", "connection reset", "connection refused",
        "name or service not known", "temporary failure in name resolution",
        "network is unreachable",
    ))


def _normalize_url(raw):
    url = html_lib.unescape(raw).replace("\\/", "/").replace("\\u002F", "/").strip()
    if url.startswith("//"):
        url = "https:" + url
    return url


def _http_get_text(url, referer=None):
    if _is_blocked_host(url):
        raise ValueError("blocked host")
    headers = {"User-Agent": _USER_AGENT, "Accept-Language": "en-US,en;q=0.9"}
    if referer:
        headers["Referer"] = referer
    req = urllib.request.Request(url, headers=headers)
    with _OPENER.open(req, timeout=20) as resp:
        data = resp.read(_SNIFF_MAX_BYTES + 1)
    return data[:_SNIFF_MAX_BYTES].decode("utf-8", "ignore")


def _sniff(url, cause=None):
    """Extract a direct .mp4 or .m3u8 URL from page HTML. Returns a probe-shaped dict."""
    try:
        page = _http_get_text(url)
    except Exception as exc:
        if _is_network_error(exc):
            return {"error": "NETWORK", "message": type(exc).__name__}
        return {"error": "SNIFF_FAILED", "message": type(exc).__name__}

    title = None
    meta_title = re.search(
        r'<meta[^>]+property=["\']og:title["\'][^>]+content=["\']([^"\']+)["\']', page, re.I
    )
    if meta_title:
        title = html_lib.unescape(meta_title.group(1))
    else:
        doc_title = re.search(r"<title[^>]*>(.*?)</title>", page, re.I | re.S)
        if doc_title:
            title = html_lib.unescape(doc_title.group(1))
    thumbnail = None
    meta_img = re.search(
        r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)["\']', page, re.I
    )
    if meta_img:
        thumbnail = _normalize_url(meta_img.group(1))

    candidates = []

    def _add(raw):
        normalized = _normalize_url(raw)
        if not normalized.lower().startswith(("http://", "https://")):
            return
        if _is_blocked_host(normalized):
            return
        if normalized not in candidates:
            candidates.append(normalized)

    for pattern in (_OG_VIDEO_RE, _SRC_RE, _URL_RE):
        for raw in pattern.findall(page):
            _add(raw)
    # JSON blobs often escape slashes (https:\/\/...); scan an unescaped copy too.
    unescaped = page.replace("\\/", "/").replace("\\u002F", "/")
    for raw in _URL_RE.findall(unescaped):
        _add(raw)

    mp4s = [c for c in candidates if ".mp4" in c.lower()]
    m3u8s = [c for c in candidates if ".m3u8" in c.lower()]

    def _height(c):
        match = _HEIGHT_RE.search(c)
        return int(match.group(1)) if match else 0

    if mp4s:
        # Prefer real video over preview/thumbnail clips, then the highest resolution.
        best = max(mp4s, key=lambda c: (0 if "preview" in c.lower() else 1, _height(c)))
        height = _HEIGHT_RE.search(best)
        fmt = {
            "id": "sniff-mp4:" + best,
            "resolution": (height.group(1) + "p") if height else "Unknown",
            "container": "mp4", "videoCodec": "unknown", "audioCodec": "unknown",
            "estimatedBytes": None, "language": None, "languagePreference": None,
            "mergeRequired": False,
        }
    elif m3u8s:
        fmt = {
            "id": "sniff-hls:" + m3u8s[0],
            "resolution": "Unknown",
            "container": "mp4", "videoCodec": "unknown", "audioCodec": "unknown",
            "estimatedBytes": None, "language": None, "languagePreference": None,
            "mergeRequired": False,
        }
    else:
        return {"error": "SNIFF_FAILED", "message": cause or "no media link"}
    return {
        "title": _safe_title(title),
        "durationSeconds": None,
        "thumbnailUrl": thumbnail,
        "formats": [fmt],
    }


def _download_direct(media_url, output_path):
    if _is_blocked_host(media_url):
        return json.dumps({"error": "SNIFF_FAILED"})
    # Stream to a .part sibling so the Kotlin progress poller (which matches "<name>.")
    # reports bytes, then atomically rename and validate.
    part_path = output_path + ".part"
    try:
        _clear_outputs(output_path)
        req = urllib.request.Request(media_url, headers={"User-Agent": _USER_AGENT})
        with _OPENER.open(req, timeout=30) as resp:
            if "text/html" in (resp.headers.get("Content-Type") or "").lower():
                return json.dumps({"error": "SNIFF_FAILED"})
            with open(part_path, "wb") as handle:
                while True:
                    chunk = resp.read(1 << 16)
                    if not chunk:
                        break
                    handle.write(chunk)
    except Exception as exc:
        _safe_remove(part_path)
        if _is_network_error(exc):
            return json.dumps({"error": "NETWORK", "message": type(exc).__name__})
        return json.dumps({"error": "SNIFF_FAILED", "message": type(exc).__name__})
    if _reject_if_junk(part_path) is not None:
        _safe_remove(part_path)
        return json.dumps({"error": "SNIFF_FAILED"})
    if not _has_audio(part_path):
        _safe_remove(part_path)
        return json.dumps({"error": "NO_AUDIO"})
    try:
        os.replace(part_path, output_path)
    except OSError as exc:
        _safe_remove(part_path)
        return json.dumps({"error": "SNIFF_FAILED", "message": type(exc).__name__})
    return json.dumps({"path": output_path, "bytes": os.path.getsize(output_path)})


def _download_hls(m3u8_url, output_path):
    if _is_blocked_host(m3u8_url):
        return json.dumps({"error": "HLS_FAILED"})
    try:
        import hlsfetch
    except ImportError:
        return json.dumps({"error": "HLS_FAILED"})
    raw_path = output_path + ".ts"
    try:
        _clear_outputs(raw_path)
        try:
            hlsfetch.download(
                m3u8_url,
                raw_path,
                remux=False,
                headers={"User-Agent": _USER_AGENT},
            )
        except TypeError:
            # Tolerate hlsfetch signatures that do not accept headers/remux kwargs.
            hlsfetch.download(m3u8_url, raw_path, remux=False)
    except Exception as exc:
        _safe_remove(raw_path)
        if _is_network_error(exc):
            return json.dumps({"error": "NETWORK", "message": type(exc).__name__})
        return json.dumps({"error": "HLS_FAILED", "message": type(exc).__name__})
    if not os.path.isfile(raw_path) or _reject_if_junk(raw_path) is not None:
        _safe_remove(raw_path)
        return json.dumps({"error": "HLS_FAILED"})
    return json.dumps({
        "path": raw_path,
        "bytes": os.path.getsize(raw_path),
        "raw": True,
        "container": "ts",
    })


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
        # First line of defense: abort before writing a sub-floor payload. The post-write
        # _reject_if_junk check below is the authoritative guard (Content-Length is not
        # always present, and some sites serve an HTML redirect in place of the stream).
        "min_filesize": _MIN_MEDIA_BYTES,
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
    best = max(candidates, key=lambda candidate: os.path.getsize(candidate))
    if _reject_if_junk(best) is not None:
        # A webpage/redirect or error page was saved instead of media. Discard every
        # candidate so the caller reports a typed failure, not a bogus success.
        for candidate in candidates:
            try:
                os.remove(candidate)
            except OSError:
                pass
        return None
    return best


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
    # Fallback-layer synthetic ids carry the real media URL (ADR-0013).
    if format_id.startswith("sniff-hls:"):
        return _download_hls(format_id[len("sniff-hls:"):], output_path)
    if format_id.startswith("sniff-mp4:"):
        return _download_direct(format_id[len("sniff-mp4:"):], output_path)
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