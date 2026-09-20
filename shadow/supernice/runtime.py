"""Governed execution facade for the 150-Core registry."""
from __future__ import annotations
from dataclasses import asdict
from typing import Any,Mapping
from .catalog import CORE_BY_ID
from .contracts import CoreRequest,CoreResult,CoreSpec,Handler
from .providers import ProviderRouter

class CoreRuntime:
    def __init__(self,*,handler_map:Mapping[str,Handler]|None=None,free_first:bool=True):
        self.handlers=dict(handler_map or {})
        self.providers=ProviderRouter()
        self.free_first=free_first

    def list_cores(self)->list[dict[str,Any]]:
        return [asdict(s) for s in CORE_BY_ID.values()]

    def execute(self,request:CoreRequest)->CoreResult:
        spec=CORE_BY_ID.get(request.core_id)
        if spec is None: return CoreResult(request.core_id,False,"unknown_core")
        if spec.requires_confirmation and not request.confirmed:
            return CoreResult(spec.id,False,"confirmation_required",
                metadata={"risk":spec.risk.value,"core":spec.name})
        handler=self.handlers.get(spec.id)
        if handler is None:
            return CoreResult(spec.id,False,"adapter_required",
                metadata={"core":spec.name,"domain":spec.domain,
                          "contract":"recognized_and_governed",
                          "next_step":"attach a real implementation/provider adapter"})
        return handler(request,spec)

    def health(self)->dict[str,Any]:
        return {"architecture":"SHADOW Super Nice 150-Core","core_count":150,
                "free_first":self.free_first,"subscription_gate":False,
                "credit_meter":False,"online_ai_mode":True,
                "provider_count":len(self.providers.providers)}
