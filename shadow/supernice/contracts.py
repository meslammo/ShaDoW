"""Shared contracts for SHADOW Super Nice 150-Core architecture.
Provider-neutral; no proprietary model weights, vendor secrets, or hidden reasoning traces.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Callable

class Risk(str, Enum):
    LOW="low"; MEDIUM="medium"; HIGH="high"; CRITICAL="critical"

@dataclass(frozen=True)
class CoreSpec:
    id:str; name:str; domain:str; purpose:str
    inputs:tuple[str,...]=("request","context","artifacts")
    outputs:tuple[str,...]=("result","evidence","metadata")
    dependencies:tuple[str,...]=()
    risk:Risk=Risk.LOW
    requires_confirmation:bool=False
    online_brain_required:bool=True
    implementation:str="shared-runtime"

@dataclass
class CoreRequest:
    core_id:str
    request:str
    context:dict[str,Any]=field(default_factory=dict)
    artifacts:list[dict[str,Any]]=field(default_factory=list)
    confirmed:bool=False

@dataclass
class CoreResult:
    core_id:str; ok:bool; status:str
    result:Any=None
    evidence:list[dict[str,Any]]=field(default_factory=list)
    metadata:dict[str,Any]=field(default_factory=dict)

Handler=Callable[[CoreRequest,CoreSpec],CoreResult]

class CoreContractError(ValueError): pass
