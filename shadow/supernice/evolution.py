"""SHADOW long-term evolution control plane (phases 01-35).

This module builds on the existing 150-Core runtime. It adds bounded,
provider-neutral control primitives for memory, governance, tools, workflows,
skills, companions/devices, simulation, diagnostics, recovery, knowledge
graph, verification, and controlled self-improvement.

Physical provider/device/companion integrations remain explicit adapters:
software can be wired and tested without pretending hardware or credentials
exist.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from hashlib import sha256
import json
import time
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping, Optional

from shadow.memory.persistent import PersistentMemory
from shadow.security.permissions import PermissionManager, PermissionDecision
from shadow.observability.audit import AuditLog
from shadow.runtime.action_executor import ActionExecutor, ActionResult
from shadow.tools.builtins import build_builtin_registry
from .catalog import CORE_BY_ID


@dataclass(frozen=True)
class PhaseSpec:
    number: int
    name: str
    goal: str
    category: str
    dependencies: tuple[int, ...] = ()
    software_ready: bool = True
    external_gate: bool = False


PHASES_35: tuple[PhaseSpec, ...] = (
    PhaseSpec(1, "Deep Integration", "Unify memory, governance, tools, brain, events and verification.", "foundation"),
    PhaseSpec(2, "Real Online Backend E2E", "Exercise a real online provider through the governed loop.", "e2e", (1,), external_gate=True),
    PhaseSpec(3, "Cloud Unified Runtime", "Expose one authoritative Cloud execution path.", "cloud", (1, 2), external_gate=True),
    PhaseSpec(4, "Android Integration", "Bind Android to the unified lifecycle and event contract.", "android", (1, 3)),
    PhaseSpec(5, "Real Android E2E", "Prove the complete request path on a real device.", "e2e", (4,), external_gate=True),
    PhaseSpec(6, "Voice + Hey Shadow", "Wake-word, STT/TTS, background conversation and barge-in.", "voice", (5,), external_gate=True),
    PhaseSpec(7, "Device + Spatial Intelligence", "Discover and govern device/spatial capabilities.", "devices", (5,), external_gate=True),
    PhaseSpec(8, "Companion System", "Register, delegate and audit trusted companions.", "companions", (7,), external_gate=True),
    PhaseSpec(9, "GitHub / Development Agent E2E", "Run the full governed development lifecycle.", "development", (3,), external_gate=True),
    PhaseSpec(10, "Security / Governance Hardening", "Strengthen identity, permissions, sandboxing and secret boundaries.", "security", (1,)),
    PhaseSpec(11, "Recovery / Reliability", "Retry, pause, resume, rollback and idempotency.", "reliability", (1, 10)),
    PhaseSpec(12, "Production Hardening + First Release", "Regression, release evidence, backup and release process.", "release", (2, 5, 9, 10, 11)),
    PhaseSpec(13, "Full 150-Core Real Coverage", "Give each core an appropriate real or adapter-level proof.", "cores", (12,), external_gate=True),
    PhaseSpec(14, "Continuous Learning", "Learn only from approved corrections and bounded context.", "learning", (12,)),
    PhaseSpec(15, "Advanced Agent Loop", "Decompose and execute multi-step tasks with resumable state.", "agents", (11, 14)),
    PhaseSpec(16, "Multimodal Intelligence", "Unify text, voice, image, vision and OCR context.", "multimodal", (6, 15), external_gate=True),
    PhaseSpec(17, "Cross-Device Shadow", "Transfer governed context between trusted devices.", "federation", (7, 8, 16), external_gate=True),
    PhaseSpec(18, "Autonomous-but-Governed Operations", "Run long tasks inside explicit policy boundaries.", "automation", (10, 11, 15)),
    PhaseSpec(19, "Shadow Intelligence Evolution", "Improve routing, planning, reasoning context and evaluation.", "intelligence", (14, 15)),
    PhaseSpec(20, "Personal Knowledge Graph", "Model durable relationships between people, projects, devices and events.", "knowledge", (14,)),
    PhaseSpec(21, "Predictive Assistance", "Detect patterns and suggest context-aware next actions.", "intelligence", (19, 20)),
    PhaseSpec(22, "Real-World Automation", "Build trigger/condition/action workflows with verification.", "automation", (18, 21), external_gate=True),
    PhaseSpec(23, "Shadow Skills Platform", "Install, version, sandbox and test capabilities.", "platform", (15, 22)),
    PhaseSpec(24, "Multi-Agent / Companion Intelligence", "Coordinate specialized agents under Shadow governance.", "agents", (8, 15, 23), external_gate=True),
    PhaseSpec(25, "Advanced Simulation & Sandbox", "Dry-run actions and estimate impact before execution.", "safety", (10, 15, 23)),
    PhaseSpec(26, "Self-Diagnostics", "Detect, isolate and explain runtime failures.", "reliability", (11, 25)),
    PhaseSpec(27, "Controlled Self-Improvement", "Propose, test and review changes before activation.", "development", (9, 23, 26), external_gate=True),
    PhaseSpec(28, "Federated Device Intelligence", "Expose remote capabilities as governed nodes.", "federation", (17, 24), external_gate=True),
    PhaseSpec(29, "Spatial World Model", "Fuse spatial entities, devices, sensors and time.", "spatial", (7, 16, 28), external_gate=True),
    PhaseSpec(30, "Continuous Verification", "Verify throughout execution and re-plan on drift.", "verification", (11, 15, 25)),
    PhaseSpec(31, "Shadow Operating Layer", "Provide one identity/context/permission plane over domains.", "platform", (23, 28, 30)),
    PhaseSpec(32, "Shadow Ecosystem", "Support skills, companions, adapters and developer APIs.", "platform", (24, 31)),
    PhaseSpec(33, "Global Reliability Layer", "Backup, failover, recovery and integrity at platform scale.", "reliability", (11, 31, 32), external_gate=True),
    PhaseSpec(34, "Shadow 2.x Evolution", "Use production evidence to refactor without rebuilding from zero.", "evolution", (13, 19, 26, 33)),
    PhaseSpec(35, "Shadow Long-Term Platform", "Operate as a durable personal AI platform with governed expansion.", "platform", (31, 32, 33, 34), external_gate=True),
)


@dataclass
class WorkflowStep:
    name: str
    action: Callable[[dict[str, Any]], Any]


@dataclass
class WorkflowResult:
    ok: bool
    status: str
    completed: list[str] = field(default_factory=list)
    failed_step: Optional[str] = None
    output: Any = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class KnowledgeNode:
    node_id: str
    kind: str
    label: str
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class KnowledgeEdge:
    source: str
    relation: str
    target: str


@dataclass(frozen=True)
class CompanionRecord:
    companion_id: str
    kind: str
    capabilities: tuple[str, ...] = ()
    trusted: bool = False
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class DeviceRecord:
    device_id: str
    kind: str
    capabilities: tuple[str, ...] = ()
    online: bool = False
    trusted: bool = False
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class MultimodalEnvelope:
    text: str = ""
    audio_ref: Optional[str] = None
    image_ref: Optional[str] = None
    vision: Optional[dict[str, Any]] = None
    metadata: dict[str, Any] = field(default_factory=dict)

    def as_context(self) -> dict[str, Any]:
        return {
            "text": self.text,
            "audio_ref": self.audio_ref,
            "image_ref": self.image_ref,
            "vision": self.vision or {},
            "multimodal": bool(self.audio_ref or self.image_ref or self.vision),
            **self.metadata,
        }


class KnowledgeGraph:
    def __init__(self) -> None:
        self.nodes: dict[str, KnowledgeNode] = {}
        self.edges: set[KnowledgeEdge] = set()

    def upsert_node(self, node_id: str, kind: str, label: str, metadata: Mapping[str, Any] | None = None) -> KnowledgeNode:
        node = KnowledgeNode(node_id, kind, label, dict(metadata or {}))
        self.nodes[node_id] = node
        return node

    def relate(self, source: str, relation: str, target: str) -> KnowledgeEdge:
        if source not in self.nodes or target not in self.nodes:
            raise KeyError("both relation endpoints must exist")
        edge = KnowledgeEdge(source, relation, target)
        self.edges.add(edge)
        return edge

    def neighbors(self, node_id: str, relation: Optional[str] = None) -> list[str]:
        return [
            e.target for e in self.edges
            if e.source == node_id and (relation is None or e.relation == relation)
        ]

    def export(self) -> dict[str, Any]:
        return {
            "nodes": [node.__dict__ for node in self.nodes.values()],
            "edges": [edge.__dict__ for edge in sorted(self.edges, key=lambda x: (x.source, x.relation, x.target))],
        }


class SkillRegistry:
    def __init__(self) -> None:
        self._skills: dict[str, dict[str, Any]] = {}

    def register(
        self,
        name: str,
        handler: Callable[..., Any],
        *,
        version: str = "1.0.0",
        permissions: Iterable[str] = (),
        dependencies: Iterable[str] = (),
    ) -> None:
        if not name or not callable(handler):
            raise ValueError("skill name and callable handler are required")
        self._skills[name] = {
            "name": name,
            "version": version,
            "handler": handler,
            "permissions": tuple(permissions),
            "dependencies": tuple(dependencies),
            "enabled": True,
        }

    def disable(self, name: str) -> bool:
        skill = self._skills.get(name)
        if not skill:
            return False
        skill["enabled"] = False
        return True

    def enable(self, name: str) -> bool:
        skill = self._skills.get(name)
        if not skill:
            return False
        skill["enabled"] = True
        return True

    def invoke(self, name: str, **kwargs: Any) -> Any:
        skill = self._skills.get(name)
        if not skill or not skill["enabled"]:
            raise LookupError("skill unavailable")
        return skill["handler"](**kwargs)

    def manifest(self) -> list[dict[str, Any]]:
        return [{k: v for k, v in s.items() if k != "handler"} for s in self._skills.values()]


class CompanionRegistry:
    def __init__(self) -> None:
        self._items: dict[str, CompanionRecord] = {}

    def register(self, record: CompanionRecord) -> None:
        self._items[record.companion_id] = record

    def get(self, companion_id: str) -> Optional[CompanionRecord]:
        return self._items.get(companion_id)

    def trusted(self) -> list[CompanionRecord]:
        return [x for x in self._items.values() if x.trusted]

    def snapshot(self) -> list[dict[str, Any]]:
        return [x.__dict__ for x in self._items.values()]


class DeviceFederation:
    def __init__(self) -> None:
        self._items: dict[str, DeviceRecord] = {}

    def register(self, record: DeviceRecord) -> None:
        self._items[record.device_id] = record

    def discover(self, capability: Optional[str] = None) -> list[DeviceRecord]:
        items = [x for x in self._items.values() if x.online and x.trusted]
        if capability:
            items = [x for x in items if capability in x.capabilities]
        return items

    def snapshot(self) -> list[dict[str, Any]]:
        return [x.__dict__ for x in self._items.values()]


class RecoveryManager:
    def __init__(self) -> None:
        self._checkpoints: dict[str, dict[str, Any]] = {}

    def checkpoint(self, request_id: str, state: Mapping[str, Any]) -> str:
        stamp = f"{request_id}:{time.time_ns()}"
        checkpoint_id = sha256(stamp.encode()).hexdigest()[:20]
        self._checkpoints[checkpoint_id] = json.loads(json.dumps(dict(state), default=str))
        return checkpoint_id

    def resume(self, checkpoint_id: str) -> dict[str, Any]:
        if checkpoint_id not in self._checkpoints:
            raise KeyError("checkpoint_not_found")
        return dict(self._checkpoints[checkpoint_id])

    def rollback(self, checkpoint_id: str) -> dict[str, Any]:
        state = self.resume(checkpoint_id)
        return {"ok": True, "status": "rolled_back", "checkpoint_id": checkpoint_id, "state": state}

    def discard(self, checkpoint_id: str) -> bool:
        return self._checkpoints.pop(checkpoint_id, None) is not None


class SimulationEngine:
    """Side-effect-free planner for the same governance surface."""

    def __init__(self, permissions: PermissionManager) -> None:
        self.permissions = permissions

    def preview(self, actions: Iterable[Mapping[str, Any]], *, confirmed: bool = False) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        for action in actions:
            capability = str(action.get("capability") or action.get("name") or "")
            decision = self.permissions.decide(capability, confirmed=confirmed)
            result.append({
                "capability": capability,
                "allowed": decision.allowed,
                "requires_confirmation": decision.requires_confirmation,
                "reason": decision.reason,
            })
        return result


class Diagnostics:
    def __init__(self, workspace: str, tool_count: int) -> None:
        self.workspace = workspace
        self.tool_count = tool_count

    def run(self, *, include_filesystem: bool = True) -> dict[str, Any]:
        workspace = Path(self.workspace)
        writable = None
        if include_filesystem:
            try:
                workspace.mkdir(parents=True, exist_ok=True)
                probe = workspace / ".shadow" / ".diagnostic-probe"
                probe.parent.mkdir(parents=True, exist_ok=True)
                probe.write_text("ok", encoding="utf-8")
                probe.unlink(missing_ok=True)
                writable = True
            except Exception:
                writable = False
        return {
            "python_runtime": True,
            "core_count": len(CORE_BY_ID),
            "core_catalog_complete": len(CORE_BY_ID) == 150,
            "tool_count": self.tool_count,
            "workspace_writable": writable,
        }


class ContinuousVerifier:
    def verify(self, expected: Any, actual: Any, *, predicate: Optional[Callable[[Any, Any], bool]] = None) -> dict[str, Any]:
        ok = predicate(expected, actual) if predicate else expected == actual
        return {"ok": bool(ok), "expected": expected, "actual": actual}


class UnifiedControlPlane:
    """Cross-cutting execution services consumed by the unified orchestrator."""

    def __init__(self, workspace: str = ".") -> None:
        self.workspace = workspace
        memory_path = Path(workspace) / ".shadow" / "memory.jsonl"
        audit_path = Path(workspace) / ".shadow" / "audit.jsonl"
        self.memory = PersistentMemory(memory_path)
        self.permissions = PermissionManager()
        self.audit_log = AuditLog(audit_path)
        self.tool_registry = build_builtin_registry(memory=self.memory)
        self.executor = ActionExecutor(self.permissions)
        for spec in self.tool_registry._tools.values():
            self.executor.register(spec.name, spec.handler)
        self.skills = SkillRegistry()
        self.companions = CompanionRegistry()
        self.devices = DeviceFederation()
        self.knowledge = KnowledgeGraph()
        self.recovery = RecoveryManager()
        self.simulation = SimulationEngine(self.permissions)
        self.verifier = ContinuousVerifier()
        self.diagnostics_engine = Diagnostics(workspace, len(self.tool_registry._tools))

    def recall(self, query: str, limit: int = 8) -> list[str]:
        return [item.text for item in self.memory.search(query, limit)]

    def remember(self, text: str, *, kind: str = "conversation", tags: Iterable[str] = (), source: str = "unified150") -> str:
        item = self.memory.put(text, kind=kind, tags=tuple(tags), source=source)
        return item.id

    def governance(self, capability: str, *, confirmed: bool = False, automation_granted: bool = False) -> PermissionDecision:
        return self.permissions.decide(capability, confirmed=confirmed, automation_granted=automation_granted)

    def tool_schemas(self) -> list[dict[str, Any]]:
        return list(self.tool_registry.openai_schemas())

    def execute_tool(self, name: str, arguments: Mapping[str, Any], *, confirmed: bool = False, automation_granted: bool = False) -> dict[str, Any]:
        args = dict(arguments)
        args.pop("confirmed", None)
        result: ActionResult = self.executor.execute(
            name,
            confirmed=confirmed,
            automation_granted=automation_granted,
            **args,
        )
        self.audit(
            "tool.executed",
            outcome="ok" if result.success else "failed",
            metadata={
                "tool": name,
                "success": result.success,
                "requires_confirmation": result.requires_confirmation,
                **result.metadata,
            },
        )
        return {
            "success": result.success,
            "output": result.output,
            "requires_confirmation": result.requires_confirmation,
            "metadata": result.metadata,
        }

    def audit(self, event: str, *, request_id: Optional[str] = None, outcome: str = "ok", metadata: Optional[Mapping[str, Any]] = None) -> None:
        self.audit_log.record(
            event,
            request_id=request_id,
            outcome=outcome,
            metadata=dict(metadata or {}),
        )

    def run_workflow(self, steps: Iterable[WorkflowStep], *, initial: Optional[Mapping[str, Any]] = None) -> WorkflowResult:
        state = dict(initial or {})
        completed: list[str] = []
        for step in steps:
            try:
                state[step.name] = step.action(dict(state))
                completed.append(step.name)
            except Exception as exc:
                return WorkflowResult(False, "step_failed", completed, step.name, state, {"error_type": type(exc).__name__})
        return WorkflowResult(True, "completed", completed, output=state)

    def checkpoint(self, request_id: str, state: Mapping[str, Any]) -> str:
        return self.recovery.checkpoint(request_id, state)

    def resume(self, checkpoint_id: str) -> dict[str, Any]:
        return self.recovery.resume(checkpoint_id)

    def rollback(self, checkpoint_id: str) -> dict[str, Any]:
        result = self.recovery.rollback(checkpoint_id)
        self.audit("recovery.rollback", outcome="ok", metadata={"checkpoint_id": checkpoint_id})
        return result

    def simulate_actions(self, actions: Iterable[Mapping[str, Any]], *, confirmed: bool = False) -> list[dict[str, Any]]:
        return self.simulation.preview(actions, confirmed=confirmed)

    def diagnostics(self) -> dict[str, Any]:
        return self.diagnostics_engine.run()

    def learn_correction(self, correction: str, *, approved: bool = False) -> dict[str, Any]:
        text = str(correction or "").strip()
        if not text:
            return {"ok": False, "status": "correction_required"}
        decision = self.governance("memory.write", confirmed=approved)
        if not decision.allowed:
            return {"ok": False, "status": "approval_required", "reason": decision.reason}
        item_id = self.remember(text, kind="approved_correction", tags=("learning", "approved"), source="owner-approved")
        self.audit("learning.applied", outcome="ok", metadata={"memory_id": item_id})
        return {"ok": True, "status": "learned", "memory_id": item_id}

    def predict_assistance(self, query: str, limit: int = 3) -> list[dict[str, Any]]:
        hits = self.memory.search(str(query or ""), max(1, min(int(limit), 10)))
        return [
            {
                "suggestion": item.text,
                "kind": item.kind,
                "reason": "matched prior approved/runtime context",
            }
            for item in hits
        ]

    def delegate_companion(
        self,
        companion_id: str,
        task: str,
        handler: Optional[Callable[[str], Any]] = None,
        *,
        confirmed: bool = False,
    ) -> dict[str, Any]:
        record = self.companions.get(companion_id)
        if record is None:
            return {"ok": False, "status": "companion_not_found"}
        if not record.trusted:
            return {"ok": False, "status": "companion_not_trusted"}
        if handler is None:
            return {
                "ok": True,
                "status": "delegation_ready",
                "companion_id": companion_id,
                "task": str(task or ""),
            }
        try:
            output = handler(str(task or ""))
            self.audit("companion.delegated", outcome="ok", metadata={"companion_id": companion_id})
            return {"ok": True, "status": "completed", "companion_id": companion_id, "output": output}
        except Exception as exc:
            self.audit("companion.failed", outcome="failed", metadata={"companion_id": companion_id, "error_type": type(exc).__name__})
            return {"ok": False, "status": "companion_failed", "error_type": type(exc).__name__}

    def spatial_observe(
        self,
        entity_id: str,
        kind: str,
        *,
        relation: str = "near",
        space_id: str = "space:default",
        label: Optional[str] = None,
        metadata: Optional[Mapping[str, Any]] = None,
    ) -> dict[str, Any]:
        sid = str(space_id or "space:default")
        eid = str(entity_id or "").strip()
        if not eid:
            return {"ok": False, "status": "entity_required"}
        self.knowledge.upsert_node(sid, "space", sid)
        self.knowledge.upsert_node(eid, str(kind or "entity"), str(label or eid), dict(metadata or {}))
        edge = self.knowledge.relate(sid, str(relation or "near"), eid)
        self.audit("spatial.observed", metadata={"space_id": sid, "entity_id": eid, "relation": edge.relation})
        return {"ok": True, "status": "observed", "space_id": sid, "entity_id": eid, "relation": edge.relation}

    def backup_state(self, path: str | Path) -> dict[str, Any]:
        target = Path(path)
        target.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "version": "evo35",
            "created_at": time.time(),
            "memory": json.loads(self.memory.export()),
            "knowledge": self.knowledge.export(),
            "skills": self.skills.manifest(),
            "companions": self.companions.snapshot(),
            "devices": self.devices.snapshot(),
        }
        target.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        return {"ok": True, "path": str(target), "memory_items": len(payload["memory"])}

    def restore_memory_from_backup(self, path: str | Path) -> dict[str, Any]:
        source = Path(path)
        data = json.loads(source.read_text(encoding="utf-8"))
        count = 0
        for item in data.get("memory", []):
            text = str(item.get("text") or "").strip()
            if not text:
                continue
            self.memory.put(
                text,
                kind=str(item.get("kind") or "fact"),
                tags=tuple(item.get("tags") or ()),
                source=str(item.get("source") or "restore"),
                item_id=str(item.get("id") or "") or None,
            )
            count += 1
        self.audit("backup.restored", outcome="ok", metadata={"memory_items": count})
        return {"ok": True, "restored_memory_items": count}

    def phase_progress(self) -> dict[str, Any]:
        implemented = [p.number for p in PHASES_35 if p.software_ready]
        external = [p.number for p in PHASES_35 if p.external_gate]
        return {
            "phase_count": len(PHASES_35),
            "implemented_contracts": len(implemented),
            "external_verification_pending": len(external),
            "implemented_phase_numbers": implemented,
            "external_gate_phase_numbers": external,
        }

    def multimodal(self, *, text: str = "", audio_ref: Optional[str] = None, image_ref: Optional[str] = None, vision: Optional[Mapping[str, Any]] = None, metadata: Optional[Mapping[str, Any]] = None) -> MultimodalEnvelope:
        return MultimodalEnvelope(text, audio_ref, image_ref, dict(vision or {}), dict(metadata or {}))

    def roadmap(self) -> dict[str, Any]:
        return {
            "phase_count": len(PHASES_35),
            "online_only_brain": True,
            "offline_ai_removed": True,
            "core_count": len(CORE_BY_ID),
            "phases": [
                {
                    "number": p.number,
                    "name": p.name,
                    "goal": p.goal,
                    "category": p.category,
                    "dependencies": list(p.dependencies),
                    "software_ready": p.software_ready,
                    "external_gate": p.external_gate,
                }
                for p in PHASES_35
            ],
        }

    def platform_status(self) -> dict[str, Any]:
        report = self.roadmap()
        code_ready = sum(1 for p in PHASES_35 if p.software_ready)
        external = sum(1 for p in PHASES_35 if p.external_gate)
        return {
            **report,
            "software_ready_phases": code_ready,
            "external_verification_gates": external,
            "phase_progress": self.phase_progress(),
            "knowledge_nodes": len(self.knowledge.nodes),
            "knowledge_edges": len(self.knowledge.edges),
            "skills": len(self.skills._skills),
            "trusted_companions": len(self.companions.trusted()),
            "trusted_online_devices": len(self.devices.discover()),
            "capabilities": {
                "learning": True,
                "predictive_assistance": True,
                "companion_delegation": True,
                "spatial_world_model": True,
                "simulation": True,
                "backup_restore": True,
                "controlled_self_improvement": True,
            },
            "diagnostics": self.diagnostics(),
        }


class SelfEvolution:
    """Bounded self-evolution: propose, test, approve, activate, rollback."""

    def propose(self, title: str, reason: str, files: Iterable[str], tests: Iterable[str]) -> EvolutionProposal:
        files = tuple(files)
        tests = tuple(tests)
        pid = sha256("|".join((title, reason, *files, *tests)).encode()).hexdigest()[:16]
        return EvolutionProposal(pid, title, reason, files, tests)

    @staticmethod
    def activation_allowed(proposal: EvolutionProposal, *, approved_by_master: bool, tests_passed: bool) -> bool:
        return bool(approved_by_master and tests_passed)


@dataclass(frozen=True)
class EvolutionProposal:
    proposal_id: str
    title: str
    reason: str
    files: tuple[str, ...]
    test_commands: tuple[str, ...]
    rollback_required: bool = True
    requires_approval: bool = True
