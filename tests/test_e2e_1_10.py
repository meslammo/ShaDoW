from pathlib import Path
import tempfile
from shadow.core.twelve_core_runtime import TwelveCoreRuntime, CORE_ORDER
from shadow.integration.engine import AdapterIntegrationEngine
from shadow.tasks.manager import TaskManager
from shadow.diagnostics.system import run_diagnostics

def test_e2e_master_route_covers_12_cores():
    runtime=TwelveCoreRuntime(".")
    plan=runtime.plan("راجع GitHub ثم نفذ خطة تطوير آمنة",authenticated=True)
    assert len(CORE_ORDER)==12
    assert plan["online_brain_required"] is True
    assert [s["core"] for s in plan["steps"]][:4]==["identity","conversation","reasoning","memory"]
    assert "development" in {s["core"] for s in plan["steps"]}
    assert "verification" in {s["core"] for s in plan["steps"]}

def test_e2e_action_security_blocks_without_confirmation():
    runtime=TwelveCoreRuntime(".")
    blocked=runtime.authorize("نفذ حذف ملف",capability="file.write",confirmed=False)
    allowed=runtime.authorize("نفذ حذف ملف",capability="file.write",confirmed=True)
    assert blocked["allowed"] is False and blocked["requires_confirmation"] is True
    assert allowed["allowed"] is True

def test_e2e_memory_save_recall_forget():
    with tempfile.TemporaryDirectory() as d:
        runtime=TwelveCoreRuntime(d)
        blocked=runtime.memory_save("E2E private token",explicit=False)
        assert blocked["saved"] is False
        saved=runtime.memory_save("E2E approved fact",explicit=True)
        assert saved["saved"] is True
        found=runtime.memory_search("E2E approved fact",8)
        assert any(x["id"]==saved["id"] for x in found)
        assert runtime.memory_forget(saved["id"],explicit=True)["forgotten"] is True

def test_e2e_companion_environment_spatial_integration():
    runtime=TwelveCoreRuntime(".")
    c=runtime.companion_discover("e2e-phone","android",["status"])
    assert c["state"]=="DISCOVERED"
    runtime.companions.authenticate("e2e-phone")
    runtime.companions.trust("e2e-phone",["status"])
    runtime.companions.activate("e2e-phone")
    assert runtime.companions.can("e2e-phone","status") is True
    env=runtime.device_observe("ble","device","e2e-sensor",0.9)
    assert env["label"]=="e2e-sensor"
    assert runtime.environment.snapshot()["raw_payload_retention"] is False
    spatial=runtime.spatial_observe("e2e-car","vehicle","near","north","near")
    assert spatial["direction"]=="north"
    assert runtime.spatial.snapshot()["raw_coordinates_retained"] is False
    with tempfile.TemporaryDirectory() as d:
        root=Path(d); module=root/"shadow"/"e2e_adapter.py"; module.parent.mkdir()
        module.write_text("VALUE = 100",encoding="utf-8")
        engine=AdapterIntegrationEngine(root); promoted=engine.promote("shadow/e2e_adapter.py")
        assert promoted.status=="PROMOTED"; assert engine.rollback_last()["status"]=="ROLLED_BACK"

def test_e2e_task_checkpoint_retry_recovery():
    tasks=TaskManager(max_steps=4,max_attempts=2); task=tasks.add("E2E recovery",steps=["plan","execute","verify"])
    tasks.start(task); tasks.checkpoint(task,step=1,result={"checkpoint":"ready"})
    assert tasks.fail(task,"transient",retry=True).status=="retry_pending"
    tasks.complete(task,{"ok":True}); assert task.status=="completed"

def test_e2e_diagnostics_confirm_online_master_contract():
    diag=run_diagnostics()
    assert diag["online_only"] is True and diag["offline_ai_fallback"] is False and len(diag["cores"])==12
