from shadow.supernice.catalog import CORE_BY_ID, CORE_SPECS
from shadow.supernice.contracts import CoreRequest
from shadow.supernice.integration import SuperNiceRuntime
from shadow.supernice.runtime import CoreRuntime


def test_catalog_is_exactly_150_and_unique():
    assert len(CORE_SPECS) == 150
    assert len(CORE_BY_ID) == 150
    assert set(CORE_BY_ID) == {f"CORE-{i:03d}" for i in range(1, 151)}


def test_every_core_has_a_non_blocking_builtin_handler():
    runtime = CoreRuntime()
    for i in range(1, 151):
        result = runtime.execute(
            CoreRequest(
                f"CORE-{i:03d}",
                "health check",
                context={"source": "test"},
                confirmed=True,
            )
        )
        assert result.ok is True, (i, result.status, result.metadata)
        assert result.status not in {"adapter_required", "internal_handler_missing", "handler_error"}


def test_optional_device_cores_never_block_startup():
    runtime = CoreRuntime()
    for i in range(113, 132):
        result = runtime.execute(
            CoreRequest(f"CORE-{i:03d}", "health check", confirmed=True)
        )
        assert result.ok is True
        assert result.status == "disabled_optional"
        assert result.metadata["startup_blocking"] is False


def test_supernice_facade_keeps_twelve_core_root_and_exposes_150(tmp_path):
    runtime = SuperNiceRuntime(str(tmp_path))
    status = runtime.status()
    assert status["core_count"] == 150
    assert status["registered_handlers"] == 150
    assert status["legacy_root"] == "TwelveCoreRuntime"
    assert status["startup_blocking_integrations"] == []

    result = runtime.execute("CORE-150", "legacy continuity check", confirmed=True)
    assert result.ok is True
    assert result.status == "executed"


def test_live_self_test_runs_all_150(tmp_path):
    runtime = CoreRuntime(workspace=str(tmp_path))
    report = runtime.self_test()
    assert report["core_count"] == 150
    assert report["passed"] == 150
    assert report["failed"] == 0
    assert report["all_passed"] is True
