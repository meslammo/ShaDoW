"""MOD-97: semantic spatial intelligence without raw coordinate retention."""
from __future__ import annotations
from dataclasses import asdict, dataclass, field
from time import time
from typing import Any

@dataclass
class SpatialEntity:
    entity_id: str
    kind: str
    relation: str = "near"
    direction: str | None = None
    distance_bucket: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)
    observed_at: float = field(default_factory=time)

class SpatialIntelligence:
    def __init__(self):
        self._entities: dict[str, SpatialEntity] = {}

    def observe(self, entity_id: str, kind: str, *, relation: str = "near", direction: str | None = None,
                distance_bucket: str | None = None, **metadata: Any) -> SpatialEntity:
        entity = SpatialEntity(str(entity_id), str(kind), str(relation), direction, distance_bucket, dict(metadata))
        self._entities[entity.entity_id] = entity
        return entity

    def forget(self, entity_id: str) -> bool:
        return self._entities.pop(str(entity_id), None) is not None

    def snapshot(self) -> dict[str, Any]:
        return {"entities": [asdict(x) for x in self._entities.values()], "raw_coordinates_retained": False}
