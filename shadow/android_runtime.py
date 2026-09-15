"""Android entry point for the embedded SHADOW Python runtime.

MOD-58..64: carry identity into the 12-Core and expose the final runtime services.
"""
from __future__ import annotations
import os, sys, types
from typing import Any, Dict, Optional
_runtime = None
_core = None
_final = None

def _install_shadow_package_alias() -> None:
    if "shadow" in sys.modules: return
    package = types.ModuleType("shadow"); package.__path__ = [os.path.dirname(os.path.abspath(__file__))]; package.__package__ = "shadow"; sys.modules["shadow"] = package

def _seed_owner_memory(runtime) -> None:
    if any("shadow-owner-profile-v1" in m.tags for m in runtime.memory.all()): return
    facts=["Owner name: محمد.","Owner prefers Egyptian colloquial Arabic, concise direct practical replies, and masculine addressing.","Assistant identity: SHADOW, with a JARVIS-inspired personal-assistant experience.","Primary project context: SHADOW Android assistant with native Android UI and embedded Python runtime.","Related project context: NEXO is an important software/game project for the owner.","Development workflow: owner often works from an Android phone and browser-based development tools.","Product preference: simple uncluttered interfaces with clear visible controls and useful automation."]
    for idx,text in enumerate(facts,1): runtime.memory.put(text,kind="profile",tags=("shadow-owner-profile-v1",f"profile-{idx}"),source="seed")

def _get_runtime(home: Optional[str]=None):
    global _runtime
    if home: os.chdir(home)
    _install_shadow_package_alias()
    if _runtime is None:
        from runtime import ShadowRuntime
        _runtime=ShadowRuntime(); _seed_owner_memory(_runtime)
    return _runtime

def _get_core(home: Optional[str]=None):
    global _core
    if home: os.chdir(home)
    _install_shadow_package_alias()
    if _core is None:
        from shadow.core.orchestrator import ShadowOrchestrator
        _core=ShadowOrchestrator()
    return _core

def _get_final(home: Optional[str]=None):
    global _final
    if home: os.chdir(home)
    _install_shadow_package_alias()
    if _final is None:
        from shadow.final_runtime import FinalRuntime
        _final=FinalRuntime(os.getcwd())
    return _final

def configure_online(api_key: str, model: str="gpt-5.6", home: Optional[str]=None)->Dict[str,Any]:
    _get_runtime(home); key=str(api_key or "").strip(); selected_model=str(model or "gpt-5.6").strip() or "gpt-5.6"
    if key:
        os.environ["OPENAI_API_KEY"]=key; os.environ["SHADOW_MODEL_PROVIDER"]="openai"; os.environ["SHADOW_MODEL"]=selected_model
        return {"status":"ready","provider":"openai","model":selected_model}
    os.environ.pop("OPENAI_API_KEY",None); os.environ["SHADOW_MODEL_PROVIDER"]="offline"; os.environ.pop("SHADOW_MODEL",None)
    return {"status":"offline","provider":"offline","model":"local-safe"}

def authorize(request: str, home: Optional[str]=None, authenticated: bool = False, authorized: bool = False, source: str="android")->str:
    text=str(request or "").strip(); core=_get_core(home)
    from shadow.core.runtime_governance import Intent
    task_id="android-"+str(abs(hash(text+"|"+source)))
    identity_context = {"identity": "master-authenticated"} if authenticated else {"identity": "not-authenticated"}
    intent=Intent(intent=text or "empty",goal=text,constraints={"channel":source,"execution":"local-device",**identity_context},expected_result="governed Android action or safe response")
    task=core.submit(task_id,intent)
    result=core.run(task_id,authenticated=bool(authenticated),authorized=bool(authorized),executor=lambda _intent:{"accepted":True,"request":text,"identity":"master-authenticated" if authenticated else "anonymous","source":source},verifier=lambda _intent,value:bool(value and value.get("accepted")),impact="local-device")
    risk=core.gov.classify_risk(text,"local-device").value
    if result.state.value=="Done": return f"ALLOW|{risk}|governed|{result.state.value}|identity={'master' if authenticated else 'unverified'}|source={source}"
    reason=(result.error or {}).get("reason","governance_blocked"); return f"BLOCK|{risk}|{reason}|{result.state.value}|identity={'master' if authenticated else 'unverified'}|source={source}"

def core_status(home: Optional[str]=None)->str:
    core=_get_core(home); audit=core.gov.export_audit()
    return "SHADOW MOD-58..64 12-Core Runtime: ACTIVE\n"+f"tasks={len(core.tasks)} journal={len(audit['journal'])} checkpoints={len(audit['checkpoints'])}\n"+f"retry_budget={audit['retry_budget']} time_budget_s={audit['time_budget_s']}"

def final_status(home: Optional[str]=None)->Dict[str,Any]: return _get_final(home).status()

def discover(home: Optional[str]=None)->Dict[str,Any]: return _get_final(home).discovery.snapshot()

def companion_snapshot(home: Optional[str]=None): return _get_final(home).companions.snapshot()

def voiceprint_status(home: Optional[str]=None): return _get_final(home).voiceprint.status()

def handle(request: str, home: Optional[str]=None)->Dict[str,Any]:
    runtime=_get_runtime(home); result=runtime.handle(str(request or ""),context={"device_id":"android-local"})
    return {"answer":result.answer,"confidence":result.confidence,"verified":result.verified,"requires_confirmation":result.requires_confirmation,"plan":list(result.plan),"actions":list(result.actions),"metadata":dict(result.metadata)}

def health(home: Optional[str]=None)->Dict[str,Any]:
    _get_runtime(home)
    from runtime.health import health_report
    report=health_report(); report["final_runtime"]=_get_final(home).status(); return report
