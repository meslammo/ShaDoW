"""MOD-33.5: local file/ZIP/code understanding primitives for Development Agent.

The analyzer inventories files and safely previews text/code. It never executes
uploaded code and never extracts ZIP entries outside the destination tree.
"""
from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import hashlib
import zipfile

TEXT_EXT={'.txt','.md','.json','.xml','.yaml','.yml','.toml','.ini','.cfg','.csv','.py','.js','.ts','.tsx','.jsx','.java','.kt','.kts','.dart','.swift','.go','.rs','.cpp','.c','.h','.hpp','.gradle','.properties','.sql','.html','.css','.sh'}
CODE_EXT=TEXT_EXT-{'.txt','.md','.csv','.json','.xml','.yaml','.yml','.toml','.ini','.cfg','.properties'}

@dataclass(frozen=True)
class FileSummary:
    path: str
    kind: str
    size: int
    sha256: str
    preview: str = ''


def _sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()


def summarize(path: str|Path, preview_chars: int=6000) -> FileSummary:
    p=Path(path)
    if not p.is_file(): raise FileNotFoundError(p)
    suffix=p.suffix.lower()
    if zipfile.is_zipfile(p):
        with zipfile.ZipFile(p) as z:
            names=[n for n in z.namelist() if not n.endswith('/')]
            preview='ZIP entries:\n'+'\n'.join(names[:200])
        kind='zip'
    elif suffix in CODE_EXT: kind='code'; preview=p.read_text(errors='replace')[:preview_chars]
    elif suffix in TEXT_EXT: kind='text'; preview=p.read_text(errors='replace')[:preview_chars]
    elif suffix in {'.png','.jpg','.jpeg','.webp','.heic','.gif'}: kind='image'; preview='Image file; route to multimodal vision analysis.'
    elif suffix in {'.pdf'}: kind='pdf'; preview='PDF file; route to document extraction.'
    else: kind='binary'; preview='Binary/unknown file; inspect metadata before processing.'
    return FileSummary(str(p),kind,p.stat().st_size,_sha256(p),preview)


def safe_extract_zip(zip_path: str|Path, destination: str|Path) -> list[str]:
    zpath=Path(zip_path); dest=Path(destination).resolve(); dest.mkdir(parents=True,exist_ok=True)
    extracted=[]
    with zipfile.ZipFile(zpath) as z:
        for info in z.infolist():
            target=(dest/info.filename).resolve()
            if target!=dest and dest not in target.parents: raise ValueError(f"unsafe zip path: {info.filename}")
            z.extract(info,dest); extracted.append(str(target))
    return extracted
