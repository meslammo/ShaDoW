"""
SHADOW v0.39: Typed ToolCall Model

Defines the structured representation of tool calls from the model,
ensuring type safety and schema validation before execution.
"""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
from enum import Enum


class ToolCallStatus(Enum):
    """Status of a tool call through the execution pipeline."""
    PENDING = "pending"
    VALIDATED = "validated"
    SECURITY_CHECK = "security_check"
    AWAITING_APPROVAL = "awaiting_approval"
    APPROVED = "approved"
    REJECTED = "rejected"
    EXECUTING = "executing"
    COMPLETED = "completed"
    FAILED = "failed"
    TIMEOUT = "timeout"


class ToolCallError(Exception):
    """Base exception for tool call errors."""
    pass


class UnknownToolError(ToolCallError):
    """Tool requested by model is not registered."""
    pass


class InvalidArgumentError(ToolCallError):
    """Tool arguments do not match the tool's schema."""
    pass


class SchemaValidationError(ToolCallError):
    """Schema validation failed."""
    pass


class SecurityDenialError(ToolCallError):
    """Security policy denied tool execution."""
    pass


class ApprovalRequiredError(ToolCallError):
    """Tool requires approval which was not provided."""
    pass


class ExecutionError(ToolCallError):
    """Tool execution failed."""
    pass


@dataclass
class ToolCallArgument:
    """Represents a single argument to a tool."""
    name: str
    value: Any
    expected_type: Optional[str] = None
    required: bool = True

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary."""
        return {
            "name": self.name,
            "value": self.value,
            "expected_type": self.expected_type,
            "required": self.required
        }


@dataclass
class ToolCall:
    """
    Typed representation of a tool call from the model.
    
    Flows through validation → security check → approval → execution.
    """
    id: str  # Unique identifier for this tool call
    tool_name: str  # Name of the tool to invoke
    arguments: Dict[str, Any]  # Arguments to pass to the tool
    status: ToolCallStatus = field(default=ToolCallStatus.PENDING)
    
    # Validation results
    validation_errors: List[str] = field(default_factory=list)
    is_valid: bool = field(default=False)
    
    # Security assessment
    is_risky: bool = field(default=False)
    security_notes: List[str] = field(default_factory=list)
    
    # Approval state
    requires_approval: bool = field(default=False)
    approval_requested: bool = field(default=False)
    approval_given: bool = field(default=False)
    approval_reason: Optional[str] = field(default=None)
    
    # Execution results
    result: Optional[Any] = None
    error: Optional[str] = None
    execution_time_ms: float = 0.0
    
    # Metadata
    model_call_id: Optional[str] = None  # Reference to originating model call
    retry_count: int = 0
    max_retries: int = 1

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for serialization."""
        return {
            "id": self.id,
            "tool_name": self.tool_name,
            "arguments": self.arguments,
            "status": self.status.value,
            "validation_errors": self.validation_errors,
            "is_valid": self.is_valid,
            "is_risky": self.is_risky,
            "security_notes": self.security_notes,
            "requires_approval": self.requires_approval,
            "approval_given": self.approval_given,
            "approval_reason": self.approval_reason,
            "result": self.result,
            "error": self.error,
            "execution_time_ms": self.execution_time_ms,
            "model_call_id": self.model_call_id,
            "retry_count": self.retry_count
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ToolCall":
        """Create ToolCall from dictionary."""
        status_str = data.get("status", "pending")
        try:
            status = ToolCallStatus(status_str)
        except ValueError:
            status = ToolCallStatus.PENDING

        return cls(
            id=data.get("id", ""),
            tool_name=data.get("tool_name", ""),
            arguments=data.get("arguments", {}),
            status=status,
            validation_errors=data.get("validation_errors", []),
            is_valid=data.get("is_valid", False),
            is_risky=data.get("is_risky", False),
            security_notes=data.get("security_notes", []),
            requires_approval=data.get("requires_approval", False),
            approval_requested=data.get("approval_requested", False),
            approval_given=data.get("approval_given", False),
            approval_reason=data.get("approval_reason"),
            result=data.get("result"),
            error=data.get("error"),
            execution_time_ms=data.get("execution_time_ms", 0.0),
            model_call_id=data.get("model_call_id"),
            retry_count=data.get("retry_count", 0)
        )
