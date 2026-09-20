"""Bridge the Super Nice 150-Core layer onto the existing 12-Core root."""
from __future__ import annotations

from typing import Any
from .contracts import CoreRequest, CoreResult, CoreSpec
from .runtime import CoreRuntime
from .catalog import CORE_BY_ID
from shadow.core.twelve_core_runtime import TwelveCoreRuntime


class SuperNiceRuntime:
    """One facade: original TwelveCoreRuntime remains authoritative for CORE-001..012."""

    def __init__(self, workspace: str = ".") -> None:
        self.master = TwelveCoreRuntime(workspace)
        self.cores = CoreRuntime(handler_map=self._handlers(), free_first=True)

    def _handlers(self):
        def result(spec: CoreSpec, payload: Any) -> CoreResult:
            return CoreResult(spec.id, True, "executed", result=payload,
                              metadata={"source": "TwelveCoreRuntime"})

        def h1(req, spec):
            return result(spec, {"identity": "master-bound", "request": req.request})

        def h2(req, spec):
            return result(spec, self.master.reasoning.plan(req.request).to_dict())

        def h3(req, spec):
            return result(spec, self.master.plan(req.request))

        def h4(req, spec):
            return result(spec, self.master.memory_search(req.request))

        def h5(req, spec):
            return result(spec, {"context_keys": sorted(req.context.keys())})

        def h6(req, spec):
            return result(spec, {"capability": "web", "handoff": True,
                                 "note": "Use the existing governed web discovery adapter."})

        def h7(req, spec):
            return result(spec, self.master.github.summary())

        def h8(req, spec):
            return result(spec, self.master.integration.snapshot())

        def h9(req, spec):
            cid=str(req.context.get("companion_id") or "pending")
            return result(spec, self.master.companion_discover(cid, str(req.context.get("kind") or "unknown"), []))

        def h10(req, spec):
            return result(spec, self.master.device_observe(
                str(req.context.get("source") or "unknown"),
                str(req.context.get("kind") or "unknown"),
                str(req.context.get("label") or req.request),
                float(req.context.get("confidence") or 0.0),
            ))

        def h11(req, spec):
            return result(spec, self.master.spatial_observe(
                str(req.context.get("entity_id") or "unknown"),
                str(req.context.get("kind") or "unknown"),
                relation=str(req.context.get("relation") or "near"),
                direction=req.context.get("direction"),
                distance_bucket=req.context.get("distance_bucket"),
            ))

        def h12(req, spec):
            return result(spec, self.master.authorize(
                req.request,
                capability=str(req.context.get("capability") or "general"),
                confirmed=req.confirmed,
            ))

        return {f"CORE-{i:03d}": handler for i, handler in enumerate(
            (lambda req,spec:h1(req,spec),lambda req,spec:h2(req,spec),lambda req,spec:h3(req,spec),
             lambda req,spec:h4(req,spec),lambda req,spec:h5(req,spec),lambda req,spec:h6(req,spec),
             lambda req,spec:h7(req,spec),lambda req,spec:h8(req,spec),lambda req,spec:h9(req,spec),
             lambda req,spec:h10(req,spec),lambda req,spec:h11(req,spec),lambda req,spec:h12(req,spec)), 1)}

    def status(self) -> dict[str, Any]:
        return {
            "architecture": "SHADOW Super Nice 150-Core",
            "legacy_root": "TwelveCoreRuntime",
            "core_count": len(CORE_BY_ID),
            "online_ai_mode": True,
            "subscription_gate": False,
            "credit_meter": False,
        }

    def execute(self, core_id: str, request: str, *, context: dict[str, Any] | None = None,
                confirmed: bool = False) -> CoreResult:
        return self.cores.execute(CoreRequest(core_id, request, context or {}, [], confirmed))
