"""Lightweight persistent-independent scheduler primitives for background workers."""
from __future__ import annotations
from dataclasses import dataclass, field
import time
from typing import Callable

@dataclass
class ScheduledTask:
    id:str; run_at:float; callback:Callable[[],object]; interval:float|None=None; enabled:bool=True; metadata:dict=field(default_factory=dict)

class Scheduler:
    def __init__(self): self._items:dict[str,ScheduledTask]={}
    def schedule(self,item:ScheduledTask): self._items[item.id]=item; return item
    def cancel(self,item_id:str)->bool:
        if item_id not in self._items:return False
        self._items[item_id].enabled=False; return True
    def tick(self,now:float|None=None):
        now=time.time() if now is None else now; results=[]
        for item in list(self._items.values()):
            if not item.enabled or item.run_at>now: continue
            try: results.append((item.id,True,item.callback()))
            except Exception as exc: results.append((item.id,False,type(exc).__name__))
            if item.interval and item.enabled: item.run_at=now+item.interval
            else: item.enabled=False
        return results
