"""Persistent, privacy-aware SHADOW memory with lexical retrieval and export/delete."""
from __future__ import annotations
from dataclasses import asdict, dataclass
from pathlib import Path
import json, threading, time
from typing import Any, Iterable

@dataclass
class MemoryItem:
    id: str
    text: str
    kind: str = "fact"
    tags: tuple[str, ...] = ()
    created_at: float = 0.0
    updated_at: float = 0.0
    source: str = "runtime"

class PersistentMemory:
    def __init__(self, path: str | Path = ".shadow/memory.jsonl"):
        self.path = Path(path); self.path.parent.mkdir(parents=True, exist_ok=True); self._lock = threading.RLock()
    def _read(self) -> list[MemoryItem]:
        if not self.path.exists(): return []
        out=[]
        for line in self.path.read_text(encoding="utf-8").splitlines():
            try:
                d=json.loads(line); out.append(MemoryItem(**d, tags=tuple(d.get("tags", ()))))
            except Exception: continue
        return out
    def put(self, text: str, *, kind="fact", tags: Iterable[str]=(), source="runtime", item_id: str|None=None) -> MemoryItem:
        now=time.time(); item=MemoryItem(item_id or f"m-{int(now*1000000)}", text.strip(), kind, tuple(tags), now, now, source)
        with self._lock:
            with self.path.open("a", encoding="utf-8") as f: f.write(json.dumps(asdict(item), ensure_ascii=False)+"\n")
        return item
    def search(self, query: str, limit: int=10) -> list[MemoryItem]:
        terms={x.lower() for x in query.split() if x.strip()}; scored=[]
        for item in self._read():
            hay=(item.text+" "+" ".join(item.tags)).lower(); score=sum(1 for t in terms if t in hay)
            if score: scored.append((score,item.updated_at,item))
        return [x[2] for x in sorted(scored,key=lambda x:(x[0],x[1]),reverse=True)[:limit]]
    def all(self) -> list[MemoryItem]: return self._read()
    def delete(self, item_id: str) -> bool:
        with self._lock:
            items=[x for x in self._read() if x.id != item_id]
            if len(items)==len(self._read()): return False
            tmp=self.path.with_suffix(".tmp"); tmp.write_text("\n".join(json.dumps(asdict(x),ensure_ascii=False) for x in items)+("\n" if items else ""),encoding="utf-8"); tmp.replace(self.path); return True
    def export(self) -> str: return json.dumps([asdict(x) for x in self._read()],ensure_ascii=False,indent=2)
