import logging
import time
import importlib

from app.core.config import settings

logger = logging.getLogger(__name__)
_recent_diagrams = {}
_redis_client = None
_stats = {
    "total": 0,
    "hits": 0,
    "misses": 0,
    "redis_backend": 0,
    "memory_backend": 0,
    "redis_failures": 0,
}


def reset_stats():
    _stats["total"] = 0
    _stats["hits"] = 0
    _stats["misses"] = 0
    _stats["redis_backend"] = 0
    _stats["memory_backend"] = 0
    _stats["redis_failures"] = 0


def get_stats():
    return dict(_stats)


def _record_result(skipped: bool, backend: str):
    _stats["total"] += 1
    if skipped:
        _stats["hits"] += 1
    else:
        _stats["misses"] += 1

    if backend == "redis":
        _stats["redis_backend"] += 1
    elif backend == "memory":
        _stats["memory_backend"] += 1

    if _stats["total"] % 100 == 0:
        logger.info(
            "dedupe_stats total=%s hits=%s misses=%s redis_backend=%s memory_backend=%s redis_failures=%s",
            _stats["total"],
            _stats["hits"],
            _stats["misses"],
            _stats["redis_backend"],
            _stats["memory_backend"],
            _stats["redis_failures"],
        )


def _get_redis_client():
    global _redis_client
    if _redis_client is not None:
        return _redis_client

    if not settings.REDIS_URL:
        return None

    try:
        redis_module = importlib.import_module("redis")
        _redis_client = redis_module.Redis.from_url(settings.REDIS_URL, decode_responses=True)
        return _redis_client
    except Exception as exc:
        logger.warning(f"Redis dedupe unavailable, falling back to in-memory cache: {exc}")
        return None


def _should_skip_duplicate_memory(diagram_id: str, dedupe_window_seconds: int) -> bool:
    now = time.time()
    expires_at = _recent_diagrams.get(diagram_id)
    if expires_at is not None and expires_at > now:
        return True

    _recent_diagrams[diagram_id] = now + dedupe_window_seconds
    stale_keys = [key for key, ttl in _recent_diagrams.items() if ttl <= now]
    for key in stale_keys:
        _recent_diagrams.pop(key, None)

    return False


def should_skip_duplicate(diagram_id: str, dedupe_window_seconds: int) -> bool:
    if dedupe_window_seconds <= 0:
        _record_result(False, "memory")
        return False

    redis_client = _get_redis_client()
    if redis_client is not None:
        try:
            key = f"dedupe:{settings.DEDUPE_KEY_PREFIX}:diagram:{diagram_id}"
            created = redis_client.set(key, "1", ex=dedupe_window_seconds, nx=True)
            skipped = not bool(created)
            _record_result(skipped, "redis")
            return skipped
        except Exception as exc:
            _stats["redis_failures"] += 1
            logger.warning(f"Redis dedupe failed, using in-memory fallback: {exc}")

    skipped = _should_skip_duplicate_memory(diagram_id, dedupe_window_seconds)
    _record_result(skipped, "memory")
    return skipped
