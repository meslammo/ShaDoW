"""MOD-37.2: local persistent memory with explicit forget support."""
from __future__ import annotations
import json
from pathlib import Path
from time import time

class MemoryStore:
    def __init__(self,path:str|Path): self.path=Path(path); self.path.parent.mkdir(parents=True,exist_ok=True)
    def _load(self):
        if not self.path.exists(): return []
        try: return json.loads(self.path.read_text(encoding="utf-8"))
        except Exception: return []
    def remember(self,text:str,kind:str="conversation",tags:tuple[str,...]=()):
        rows=self._load(); rows.append({"id":int(time()*1000),"kind":kind,"text":text,"tags":list(tags),"ts":time()})
        self.path.write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding="utf-8")
    def search(self,query:str,limit:int=20):
        q=query.lower(); rows=self._load(); return [r for r in reversed(rows) if q in r.get("text","").lower() or q in " ".join(r.get("tags",[])).lower()][:limit]
    def forget(self,record_id:int):
        rows=[r for r in self._load() if r.get("id")!=record_id]; self.path.write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding="utf-8")
    def clear(self): self.path.write_text("[]",encoding="utf-8")
    def status(self): return {"enabled":True,"records":len(self._load()),"path":str(self.path)}
