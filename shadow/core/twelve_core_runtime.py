"""MOD-100: single 12-Core composition root for SHADOW.

Individual cores stay modular, but one runtime owns the shared route, plan,
memory, device, companion, integration, verification, and security boundaries.
AI conversation remains online-only.
"""
from __future__ import annotations
from dataclasses import asdict
from typing import Any

from shadow.governance import Governance
from shadow.agent.brain import AgentBrain
from shadow.memory.governed import GovernedMemory
from shadow.memory.persistent import PersistentMemory
from shadow.security.permissions import PermissionManager
from shadow.devices.registry import DeviceRegistry
from shadow.devices.domains import DomainRegistry
from shadow.development.github_authorization import GitHubAuthorization
from shadow.planning.master_planner import MasterPlanner
from shadow.companions.protocol import CompanionProtocol
from shadow.environment.intelligence import EnvironmentIntelligence
from shadow.spatial.intelligence import SpatialIntelligence
from shadow.integration.engine import AdapterIntegrationEngine
from shadow.observability.audit import AuditLog
from shadow.diagnostics.system import run_diagnostics
from shadow.tasks.manager import TaskManager

CORE_ORDER = (
    "identity", "conversation", "reasoning", "memory", "personal",
    "web", "github_development", "integration", "companion",
    "device_environment", "spatial", "action_security",
)

class TwelveCoreRuntime:
    def __init__(self, workspace: str = "."):
        self.workspace = workspace
        self.governance = Governance()
        self.reasoning = AgentBrain()
        self.memory = GovernedMemory(PersistentMemory(f"{workspace}/.shadow/memory.jsonl"))
        self.permissions = PermissionManager()
        self.devices = DeviceRegistry()
        self.domains = DomainRegistry()
        self.github = GitHubAuthorization.pending()
        self.planner = MasterPlanner()
        self.companions = CompanionProtocol()
        self.environment = EnvironmentIntelligence()
        self.spatial = SpatialIntelligence()
        self.integration = AdapterIntegrationEngine(workspace)
        self.audit = AuditLog(f"{workspace}/.shadow/audit.jsonl")
        self.tasks = TaskManager()

    def plan(self, request: str, *, route: str | None = None, authenticated: bool = False) -> dict[str, Any]:
        plan = self.planner.plan(request, route=route, authenticated=authenticated)
        self.audit.record("master.plan", metadata={"route": plan.route, "steps": len(plan.steps)})
        task = self.tasks.add(request, steps=[s.action for s in plan.steps])
        self.tasks.checkpoint(task, step=0)
        return {**plan.to_dict(), "task": task.task_id or None, "task_steps": len(task.steps)}

    def authorize(self, request: str, *, capability: str = "general", confirmed: bool = False) -> dict[str, Any]:
        low = str(request or "").lower()
        score = 90 if any(k in low for k in ("delete", "payment", "private key", "password", "wipe", "حذف", "فلوس", "باسورد")) else 70 if any(k in low for k in ("commit", "push", "merge", "deploy", "message", "call", "اتصل", "رسالة", "نفذ")) else 10
        blast = 90 if capability in {"device.control", "home.control", "car.control", "file.write"} else 20
        risk = self.governance.risk(score, blast)
        permission = self.permissions.decide(capability, confirmed=confirmed)
        allowed = permission.allowed
        return {
            "allowed": allowed,
            "requires_confirmation": not allowed,
            "reason": permission.reason if not allowed else "policy_allow",
            "risk": risk.level,
        }

    def memory_search(self, query: str, limit: int = 8) -> list[dict[str, Any]]:
        return [asdict(x) for x in self.memory.search(query, limit)]

    def memory_save(self, text: str, *, explicit: bool = False) -> dict[str, Any]:
        return self.memory.save(text, explicit=explicit)

    def memory_forget(self, item_id: str, *, explicit: bool = False) -> dict[str, Any]:
        return self.memory.forget(item_id, explicit=explicit)

    def companion_discover(self, cid: str, kind: str, capabilities: list[str] | None = None) -> dict[str, Any]:
        return asdict(self.companions.discover(cid, kind, capabilities or []))

    def device_observe(self, source: str, kind: str, label: str, confidence: float = 0.0) -> dict[str, Any]:
        return asdict(self.environment.observe(source, kind, label, confidence))

    def spatial_observe(self, entity_id: str, kind: str, relation: str = "near",
                        direction: str | None = None, distance_bucket: str | None = None) -> dict[str, Any]:
        return asdict(self.spatial.observe(entity_id, kind, relation=relation, direction=direction, distance_bucket=distance_bucket))

    def status(self) -> dict[str, Any]:
        return {
            "architecture": "SHADOW Master / 12-Core",
            "online_brain_required": True,
            "core_order": list(CORE_ORDER),
            "governance": self.governance.status(),
            "memory": self.memory.status(),
            "github": self.github.summary(),
            "devices": self.devices.snapshot(),
            "companions": self.companions.snapshot(),
            "environment": self.environment.snapshot(),
            "spatial": self.spatial.snapshot(),
            "integration": self.integration.snapshot(),
            "tasks": self.tasks.snapshot(),
            "diagnostics": run_diagnostics(),
        }
