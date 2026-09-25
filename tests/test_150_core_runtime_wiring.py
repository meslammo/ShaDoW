from shadow.supernice.catalog import CORE_BY_ID
from shadow.supernice.runtime import CoreRuntime


def test_all_150_cores_have_registry_and_handlers(tmp_path):
    assert len(CORE_BY_ID) == 150
    runtime = CoreRuntime(workspace=str(tmp_path))
    assert len(runtime.handlers) == 150
    result = runtime.self_test()
    assert result['core_count'] == 150
    assert result['failed'] == 0
    assert result['all_passed'] is True


def test_unified_150_status_is_online_first(tmp_path):
    runtime = CoreRuntime(workspace=str(tmp_path))
    status = runtime.health()
    assert status['architecture'] == 'SHADOW Super Nice 150-Core'
    assert status['core_count'] == 150
    assert status['registered_handlers'] == 150
    assert status['online_ai_mode'] is True