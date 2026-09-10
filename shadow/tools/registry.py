"""Tool registry used by the runtime and future Android/device adapters."""
from __future__ import annotations
from dataclasses import dataclass
from typing import Any, Callable, Dict, Iterable, Optional

@dataclass(frozen=True)
class ToolSpec:
    name: str
    description: str
    handler: Callable[..., Any]
    requires_confirmation: bool = True
    category: str = "general"

class ToolRegistry:
    def __init__(self, tools: Optional[Iterable[ToolSpec]] = None):
        self._tools: Dict[str, ToolSpec] = {}
        for tool in tools or (): self.register(tool)

    def register(self, tool: ToolSpec) -> None:
        if not tool.name.strip(): raise ValueError("tool name is required")
        self._tools[tool.name] = tool

    def get(self, name: str) -> Optional[ToolSpec]: return self._tools.get(name)
    def names(self): return tuple(sorted(self._tools))

    def describe(self):
        return [{"name": t.name, "description": t.description,
                 "requires_confirmation": t.requires_confirmation, "category": t.category}
                for t in self._tools.values()]

    def invoke(self, name: str, *, confirmed: bool = False, **kwargs: Any) -> Any:
        tool = self.get(name)
        if tool is None: raise KeyError(f"unknown tool: {name}")
        if tool.requires_confirmation and not confirmed:
            raise PermissionError(f"confirmation required for tool: {name}")
        return tool.handler(**kwargs)
