"""MOD-35.3: explicit GitHub authorization state, never a credential store."""
from __future__ import annotations
from dataclasses import dataclass

@dataclass(frozen=True)
class GitHubAuthorization:
    authorized: bool = False
    permissions: tuple[str, ...] = ()
    account: str | None = None

    def allows(self, permission: str) -> bool:
        return self.authorized and permission in self.permissions

    def summary(self) -> dict[str, object]:
        return {"authorized": self.authorized, "permissions": list(self.permissions), "account": self.account, "credential_stored": False}

    @classmethod
    def pending(cls, permissions: tuple[str, ...] = ("repository-write", "workflow-control")) -> "GitHubAuthorization":
        return cls(False, permissions, None)
