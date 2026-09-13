"""MOD-35.2: explicit skill registry.
Skills are metadata only until a trusted executor is attached."""
from __future__ import annotations
from dataclasses import dataclass

@dataclass(frozen=True)
class Skill:
    name: str
    description: str
    requires_approval: bool = False
    online: bool = False

class SkillRegistry:
    def __init__(self):
        self._skills: dict[str, Skill] = {}

    def register(self, skill: Skill) -> None:
        if not skill.name.strip():
            raise ValueError("skill name is required")
        self._skills[skill.name] = skill

    def get(self, name: str) -> Skill | None:
        return self._skills.get(name)

    def list(self) -> tuple[Skill, ...]:
        return tuple(self._skills.values())

    def defaults(self) -> None:
        for skill in (
            Skill("phone_use", "Accessibility-based phone interaction", True),
            Skill("web_search", "Current web discovery", False, True),
            Skill("site_building_design", "Land/building concept, area and CAD geometry", False),
            Skill("development_agent", "Inspect, plan, test and version project changes", True),
        ):
            self.register(skill)
