"""Free-first provider registry.
No billing or authentication bypass is implemented."""
from __future__ import annotations
from dataclasses import dataclass

@dataclass(frozen=True)
class Provider:
    name:str
    capabilities:frozenset[str]
    requires_key:bool
    potentially_billable:bool

PROVIDERS=(
    Provider("ollama",frozenset({"chat","reasoning","vision","embeddings"}),False,False),
    Provider("vllm",frozenset({"chat","reasoning","vision","embeddings"}),False,False),
    Provider("llamacpp",frozenset({"chat","reasoning","embeddings"}),False,False),
    Provider("openai",frozenset({"chat","reasoning","vision","image","audio"}),True,True),
    Provider("xai",frozenset({"chat","reasoning","image"}),True,True),
    Provider("deepseek",frozenset({"chat","reasoning"}),True,True),
    Provider("mistral",frozenset({"chat","reasoning","vision","embeddings"}),True,True),
    Provider("anthropic",frozenset({"chat","reasoning","vision"}),True,True),
    Provider("google",frozenset({"chat","reasoning","vision","image","audio"}),True,True),
)

class ProviderRouter:
    def __init__(self,providers=PROVIDERS): self.providers=tuple(providers)
    def route(self,capability:str,*,free_first:bool=True):
        out=[p for p in self.providers if capability in p.capabilities]
        if free_first: out.sort(key=lambda p:(p.requires_key,p.potentially_billable,p.name))
        return out
