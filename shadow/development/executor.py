"""MOD-46: workspace execution primitives."""
from pathlib import Path
import hashlib

class WorkspaceExecutor:
    def __init__(self, root):
        self.root = Path(root).resolve()
        (self.root / '.shadow' / 'backups').mkdir(parents=True, exist_ok=True)

    def safe_path(self, relative):
        target = (self.root / relative).resolve()
        if target != self.root and self.root not in target.parents:
            raise PermissionError('workspace path escape')
        return target

    def digest(self, path):
        if not path.is_file():
            return None
        h = hashlib.sha256()
        with path.open('rb') as f:
            for chunk in iter(lambda: f.read(1024 * 1024), b''):
                h.update(chunk)
        return h.hexdigest()

    def write_text(self, relative, content, approved=False):
        if not approved:
            raise PermissionError('explicit approval required')
        target = self.safe_path(relative)
        before = self.digest(target)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding='utf-8')
        return {'ok': True, 'path': relative, 'before': before, 'after': self.digest(target)}

    def inspect(self):
        files = [str(p.relative_to(self.root)) for p in self.root.rglob('*') if p.is_file() and '.shadow' not in p.parts]
        return {'root': str(self.root), 'file_count': len(files), 'files': files[:500]}
