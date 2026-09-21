"""SHADOW Super Nice - full 12-step master pipeline.

This layer composes the existing TwelveCoreRuntime + 150-Core runtime into a
single deterministic execution contract. External AI providers, GitHub writes,
and physical-device actions stay behind their governed adapters; this module
never fabricates their success.
"""
from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import re
from typing import Any

from .catalog import CORE_BY_ID
from .contracts import CoreResult
from .providers import ProviderRouter
from .integration import SuperNiceRuntime
from .optional import OPTIONAL_DEVICE_CORE_IDS, device_integrations_enabled

_STAGE_NAMES = ("understand","model_route","voice_multimodal","memory","web_discovery","github_development","phone_devices","agent_loop","security_approval","execute","verify","deliver")
_FRESH_RE = re.compile(r"(latest|today|now|current|news|update|جديد|دلوقتي|حالي|آخر|اخر|النهارده|بحث|ابحث|دور)", re.I)
_DEV_RE = re.compile(r"(github|git|repo|repository|code|coding|build|apk|test|commit|push|pr|كود|برمجة|مستودع|جيت هب|ابنى|ابني|ابلكيشن|تطبيق)", re.I)
_VOICE_RE = re.compile(r"(voice|speech|mic|audio|صوت|اتكلم|سمعني|اسمع)", re.I)
_IMAGE_RE = re.compile(r"(image|photo|picture|vision|camera|صورة|صور|كاميرا|شوف)", re.I)
_DEVICE_RE = re.compile(r"(phone|mobile|android|device|screen|click|tap|type|open app|موبايل|تليفون|جهاز|الشاشة|اضغط|اكتب|افتح)", re.I)
_RISKY_RE = re.compile(r"(delete|remove|wipe|format|purchase|buy|send|pay|transfer|call|message|حذف|امسح|فورمات|اشتر|شراء|ابعت|ادفع|حوّل|اتصل)", re.I)

def _now(): return datetime.now(timezone.utc).isoformat()
def _safe_text(value, limit=4000): return str(value or "").strip()[:limit]
def _stage(name, status, **details): return {"stage":name,"status":status,"at":_now(),**details}

class SuperNiceMasterPipeline:
    STAGE_NAMES = _STAGE_NAMES

    def __init__(self, workspace="."):
        self.workspace = workspace
        self.runtime = SuperNiceRuntime(workspace)
        self.providers = ProviderRouter()

    def _classify(self, request):
        text = _safe_text(request)
        fresh = bool(_FRESH_RE.search(text))
        development = bool(_DEV_RE.search(text))
        voice = bool(_VOICE_RE.search(text))
        image = bool(_IMAGE_RE.search(text))
        device = bool(_DEVICE_RE.search(text))
        risky = bool(_RISKY_RE.search(text))
        if development: intent, selected = "development", "CORE-079"
        elif device: intent, selected = "device", "CORE-057"
        elif fresh: intent, selected = "research", "CORE-064"
        elif image: intent, selected = "multimodal", "CORE-099"
        else: intent, selected = "conversation", "CORE-024"
        return {"intent":intent,"selected_core":selected,"fresh":fresh,"development":development,"voice":voice,"image":image,"device":device,"risky":risky}

    def run(self, request, *, authenticated=False, confirmed=False, context=None, execute_selected=True):
        text = _safe_text(request, 12000)
        ctx = dict(context or {})
        trace = hashlib.sha256((text + "|" + self.workspace).encode()).hexdigest()[:16]
        cls = self._classify(text)
        stages = []
        understand = self.runtime.execute("CORE-003", text, context={"trace":trace, **ctx}, confirmed=confirmed)
        stages.append(_stage("understand", "executed" if understand.ok else understand.status, core="CORE-003", result=understand.result))
        providers = [{"name":p.name,"requires_key":p.requires_key,"potentially_billable":p.potentially_billable} for p in self.providers.route("chat")]
        stages.append(_stage("model_route","ready",providers=providers,free_first=True,network_call="delegated_to_cloud_agent"))
        modalities = [m for m,enabled in (("voice",cls["voice"]),("image",cls["image"])) if enabled]
        stages.append(_stage("voice_multimodal","adapter_ready",requested=modalities,voice_transport="android_speech_recognizer_or_cloud_stt",image_transport="camera_or_uploaded_image",note="Provider calls stay behind the cloud gateway."))
        mem = self.runtime.execute("CORE-025", text, context={"trace":trace, **ctx}, confirmed=confirmed)
        stages.append(_stage("memory",mem.status,core="CORE-025",result=mem.result))
        if cls["fresh"]:
            web = self.runtime.execute("CORE-065", text, context={"trace":trace,"fresh":True,**ctx}, confirmed=confirmed)
            stages.append(_stage("web_discovery",web.status,core="CORE-065",result=web.result))
        else: stages.append(_stage("web_discovery","not_required",core="CORE-065"))
        if cls["development"]:
            gh = self.runtime.execute("CORE-081", text, context={"trace":trace,"authenticated":authenticated,**ctx}, confirmed=confirmed)
            stages.append(_stage("github_development",gh.status,core="CORE-081",result=gh.result))
        else: stages.append(_stage("github_development","not_required",core="CORE-081"))
        if cls["device"]:
            stages.append(_stage("phone_devices","adapter_enabled" if device_integrations_enabled() else "adapter_pending",optional_cores=sorted(OPTIONAL_DEVICE_CORE_IDS),local_android_control="AccessibilityService"))
        else: stages.append(_stage("phone_devices","not_required"))
        stages.append(_stage("agent_loop","ready",algorithm=["understand","plan","route","execute","observe","verify","continue"],max_rounds=8))
        high_risk = cls["risky"] or CORE_BY_ID.get(cls["selected_core"]).requires_confirmation
        if high_risk and not confirmed:
            stages.append(_stage("security_approval","confirmation_required",authenticated=authenticated,selected_core=cls["selected_core"],risk="high"))
            return {"ok":False,"status":"confirmation_required","trace_id":trace,"request":text,"classification":cls,"stages":stages,"stages_completed":[s["stage"] for s in stages],"next_step":"provide explicit confirmation before guarded execution","startup_blocking":False}
        auth = self.runtime.execute("CORE-012", text, context={"capability":"master-pipeline","trace":trace,**ctx}, confirmed=confirmed)
        stages.append(_stage("security_approval",auth.status,core="CORE-012",authenticated=authenticated,result=auth.result))
        if not auth.ok: return {"ok":False,"status":auth.status,"trace_id":trace,"request":text,"classification":cls,"stages":stages,"stages_completed":[s["stage"] for s in stages],"startup_blocking":False}
        selected = cls["selected_core"]
        execution = self.runtime.execute(selected,text,context={"trace":trace,**ctx},confirmed=confirmed) if execute_selected else CoreResult(selected,True,"skipped_by_policy")
        stages.append(_stage("execute",execution.status,core=selected,result=execution.result,evidence=execution.evidence))
        verify = self.runtime.execute("CORE-057","verify pipeline trace " + trace,context={"trace":trace,"execution_status":execution.status},confirmed=True)
        stages.append(_stage("verify",verify.status,core="CORE-057",result=verify.result,evidence=verify.evidence))
        delivered = execution.ok and verify.ok
        stages.append(_stage("deliver","completed" if delivered else "degraded",trace_id=trace,verified=bool(verify.ok),legacy_root="TwelveCoreRuntime",public_surface="SHADOW Android / Cloud"))
        return {"ok":delivered,"status":"completed" if delivered else "degraded","trace_id":trace,"request":text,"classification":cls,"stages":stages,"stages_completed":[s["stage"] for s in stages],"startup_blocking":False,"contract":{"core_count":len(CORE_BY_ID),"online_only":True,"credit_meter":False,"subscription_gate":False}}

def run_master_pipeline(request, workspace=".", *, authenticated=False, confirmed=False, context=None):
    return SuperNiceMasterPipeline(workspace).run(request, authenticated=authenticated, confirmed=confirmed, context=context)
