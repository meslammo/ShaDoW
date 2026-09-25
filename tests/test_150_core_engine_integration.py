from shadow.supernice.orchestrator import Unified150Orchestrator


def test_orchestrator_owns_engine_and_live_update_manager(tmp_path):
    def fake_brain(_request, _context):
        return {'answer': 'ok', 'provider': 'test-online', 'actions': []}

    orchestrator = Unified150Orchestrator(str(tmp_path), online_brain=fake_brain)
    result = orchestrator.run('hello')
    assert result.ok is True
    assert result.metadata['core_count'] == 150
    state = orchestrator.engine.snapshot()
    assert state['active_turn_id'] is None
    assert state['provider_state'] == 'ONLINE'
    mesh = orchestrator.mesh_status()
    assert mesh['core_count'] == 150
    assert mesh['registered_handlers'] == 150
    assert mesh['all_registered'] is True
    assert mesh['online_only'] is True


def test_orchestrator_live_update_uses_engine_queue_and_version(tmp_path):
    orchestrator = Unified150Orchestrator(str(tmp_path), online_brain=lambda *_: {'answer': 'ok'})
    result = orchestrator.live_update('0.56.2', {'shadow/core/probe_update.py': 'VALUE = 150\\n'})
    assert result['ok'] is True, result
    state = orchestrator.engine.snapshot()
    assert state['active_version'] == '0.56.2'
    assert state['pending_updates'] == []