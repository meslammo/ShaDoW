"""MOD-39: real development workspace executor."""
from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import hashlib, shutil, subprocess, time

@dataclass
class Change:
    path: str
    action: str
    content: str = ""

class DevelopmentExecutor:
    def __init__(self, workspace: str | Path):
        self.workspace=Path(workspace).resolve(); self.pending=[]
    def safe(self, rel):
        p=(self.workspace/rel).resolve()
        if p!=self.workspace and self.workspace not in p.parents: raise PermissionError("path escapes workspace")
        return p
    def inventory(self, limit=5000):
        out=[]
        for p in self.workspace.rglob("*"):
            if p.is_file() and ".shadow" not in p.parts and ".git" not in p.parts:
                out.append({"path":str(p.relative_to(self.workspace)),"bytes":p.stat().st_size,"sha256":hashlib.sha256(p.read_bytes()).hexdigest()})
                if len(out)>=limit: break
        return out
    def queue(self, path, action, content=""):
        self.pending.append(Change(path,action,content)); return len(self.pending)
    def apply(self):
        stamp=time.strftime("%Y%m%d-%H%M%S"); backup=self.workspace/".shadow"/"backups"/stamp; backup.mkdir(parents=True,exist_ok=True); done=[]
        for c in self.pending:
            p=self.safe(c.path)
            if p.exists() and p.is_file():
                b=backup/c.path;b.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,b)
            if c.action=="delete":
                if p.exists():p.unlink()
            else:
                p.parent.mkdir(parents=True,exist_ok=True);p.write_text(c.content,encoding="utf-8")
            done.append(c.path)
        self.pending=[];return {"ok":True,"changed":done,"backup":str(backup)}
    def test_python(self):
        p=subprocess.run(["python","-m","compileall","-q",str(self.workspace/"shadow")],cwd=self.workspace,capture_output=True,text=True)
        return {"ok":p.returncode==0,"returncode":p.returncode,"stderr":p.stderr[-4000:]}
