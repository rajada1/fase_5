from app.services import dedupe_service


def test_should_skip_duplicate_returns_false_first_time(mocker):
    dedupe_service._recent_diagrams.clear()
    dedupe_service._redis_client = None
    dedupe_service.reset_stats()
    mocker.patch("app.services.dedupe_service.settings.REDIS_URL", "")
    mock_time = mocker.patch("app.services.dedupe_service.time.time", return_value=1000)

    result = dedupe_service.should_skip_duplicate("diag-1", 300)

    assert result is False
    assert dedupe_service._recent_diagrams["diag-1"] == 1300
    stats = dedupe_service.get_stats()
    assert stats["total"] == 1
    assert stats["hits"] == 0
    assert stats["misses"] == 1
    assert stats["memory_backend"] == 1
    mock_time.assert_called()


def test_should_skip_duplicate_returns_true_within_window(mocker):
    dedupe_service._recent_diagrams.clear()
    dedupe_service._redis_client = None
    mocker.patch("app.services.dedupe_service.settings.REDIS_URL", "")
    dedupe_service._recent_diagrams["diag-1"] = 1300
    mocker.patch("app.services.dedupe_service.time.time", return_value=1200)

    result = dedupe_service.should_skip_duplicate("diag-1", 300)

    assert result is True


def test_should_skip_duplicate_allows_after_expiration(mocker):
    dedupe_service._recent_diagrams.clear()
    dedupe_service._redis_client = None
    mocker.patch("app.services.dedupe_service.settings.REDIS_URL", "")
    dedupe_service._recent_diagrams["diag-1"] = 1100
    mocker.patch("app.services.dedupe_service.time.time", return_value=1200)

    result = dedupe_service.should_skip_duplicate("diag-1", 300)

    assert result is False
    assert dedupe_service._recent_diagrams["diag-1"] == 1500


def test_should_use_redis_set_nx_ex_when_configured(mocker):
    redis_client = mocker.Mock()
    redis_client.set.return_value = True
    dedupe_service.reset_stats()

    mocker.patch("app.services.dedupe_service.settings.REDIS_URL", "redis://localhost:6379/0")
    mocker.patch("app.services.dedupe_service._get_redis_client", return_value=redis_client)

    result = dedupe_service.should_skip_duplicate("diag-redis", 60)

    assert result is False
    redis_client.set.assert_called_once_with("dedupe:ai-analysis:diagram:diag-redis", "1", ex=60, nx=True)
    stats = dedupe_service.get_stats()
    assert stats["total"] == 1
    assert stats["redis_backend"] == 1
    assert stats["hits"] == 0
    assert stats["misses"] == 1


def test_should_detect_duplicate_when_redis_key_exists(mocker):
    redis_client = mocker.Mock()
    redis_client.set.return_value = None

    mocker.patch("app.services.dedupe_service.settings.REDIS_URL", "redis://localhost:6379/0")
    mocker.patch("app.services.dedupe_service._get_redis_client", return_value=redis_client)

    result = dedupe_service.should_skip_duplicate("diag-redis", 60)

    assert result is True
