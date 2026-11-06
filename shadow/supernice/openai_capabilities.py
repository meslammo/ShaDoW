"""Versioned OpenAI/ChatGPT capability catalog for SHADOW.

This catalog records public release information and the integration boundary.
A listed API capability is not proof that a deployment has credentials, billing,
regional eligibility, or trusted-access approval.
"""
from __future__ import annotations

from copy import deepcopy
from typing import Any

UPDATE_WINDOW = {"from": "2026-09-01", "through": "2026-11-06"}

OPENAI_MODELS: dict[str, dict[str, Any]] = {
    "gpt-6-astra": {
        "family": "GPT-6", "kind": "reasoning", "released": "2026-09-03",
        "capabilities": ["reasoning", "coding", "research", "computer_use", "long_running_tools"],
        "access": "api_model",
    },
    "gpt-6-sol": {
        "family": "GPT-6", "kind": "reasoning", "released": "2026-09-22",
        "capabilities": ["reasoning", "text", "image_input"], "access": "api_model",
    },
    "gpt-6-luna": {
        "family": "GPT-6", "kind": "efficient_reasoning", "released": "2026-09-22",
        "capabilities": ["reasoning", "text", "image_input", "decisions"], "access": "api_model",
    },
    "gpt-6.1-sol": {
        "family": "GPT-6.1", "kind": "professional_coding", "released": "2026-09-29",
        "capabilities": ["reasoning", "coding", "computer_use", "multi_agent", "ultrafast"],
        "access": "api_model",
    },
    "gpt-6.1-terra": {
        "family": "GPT-6.1", "kind": "visual_computer_use", "released": "2026-10-26",
        "capabilities": ["visual_reasoning", "computer_use", "writing"],
        "access": "api_model",
    },
    "gpt-6-rosalind": {
        "family": "GPT-6", "kind": "life_sciences_research", "released": "2026-10-30",
        "capabilities": ["scientific_reasoning", "life_sciences_tools"],
        "access": "trusted_access_only",
    },
    "gpt-6-cyber": {
        "family": "GPT-6", "kind": "cybersecurity", "released": "2026-11-05",
        "capabilities": ["authorized_vulnerability_research", "safe_reproduction", "patch_validation"],
        "access": "approved_defenders_only",
    },
    "gpt-image-2.6-flare": {
        "family": "GPT Image 2.6", "kind": "fast_image_generation", "released": "2026-11-04",
        "capabilities": ["image_generation", "image_editing", "gif", "svg", "markup_edit", "complexity_control"],
        "access": "api_model",
    },
    "gpt-image-2.6-sunburst": {
        "family": "GPT Image 2.6", "kind": "precision_image_generation", "released": "2026-11-04",
        "capabilities": ["image_generation", "image_editing", "gif", "svg", "markup_edit", "complexity_control", "edit_consistency"],
        "access": "api_model",
    },
    "gpt-image-2.5-flare": {
        "family": "GPT Image 2.5", "kind": "fast_image_generation", "released": "2026-09-08",
        "capabilities": ["image_generation", "image_editing"], "access": "api_model_legacy",
    },
    "gpt-image-2.5-sunburst": {
        "family": "GPT Image 2.5", "kind": "precision_image_generation", "released": "2026-09-08",
        "capabilities": ["image_generation", "image_editing"], "access": "api_model_legacy",
    },
    "gpt-rosalind-research": {
        "family": "GPT Rosalind", "kind": "life_sciences_research", "released": "2026-09-08",
        "capabilities": ["scientific_reasoning", "life_sciences_research"], "access": "trusted_access_only",
    },
    "gpt-live-1": {
        "family": "GPT-Live", "kind": "full_duplex_audio", "released": "2026-09-10",
        "capabilities": ["realtime_audio", "speech", "tool_delegation", "sip"],
        "access": "api_model",
    },
}

OPENAI_CAPABILITIES: tuple[dict[str, Any], ...] = (
    {"id": "images_2_6", "date": "2026-11-04", "area": "images", "status": "integrated",
     "features": ["faster_generation", "edit_consistency", "gif", "svg", "markup_guided_edit", "complexity_control"],
     "shadow_surface": ["/v1/images", "/v1/images/edits", "CORE-100", "CORE-101"]},
    {"id": "images_2_5", "date": "2026-09-08", "area": "images", "status": "legacy_model_catalog",
     "features": ["sunburst_precision", "flare_fast_generation", "xhigh_quality", "max_quality"]},
    {"id": "gpt_6_astra_long_running_work", "date": "2026-09-03", "area": "reasoning", "status": "catalogued",
     "features": ["long_running_work", "mid_turn_steering", "responses_api_tool_calls", "misalignment_monitoring"]},
    {"id": "agents_api_public_beta", "date": "2026-09-10", "area": "agents", "status": "catalogued",
     "features": ["managed_sessions", "context_compaction", "recovery", "hosted_and_self_hosted_sandboxes"]},
    {"id": "api_key_governance", "date": "2026-09-10", "area": "security", "status": "design_target",
     "features": ["key_expiration", "maximum_key_lifetime", "project_and_org_key_creation_policy"]},
    {"id": "gpt_6_family_and_visual_fix", "date": "2026-09-25", "area": "models_vision", "status": "catalogued",
     "features": ["gpt_6_sol", "gpt_6_luna", "image_encoding_correction", "visual_evaluation_recommended"]},
    {"id": "codex_cloud_and_security", "date": "2026-09-29", "area": "software", "status": "design_target",
     "features": ["isolated_cloud_workspaces", "code_review", "scheduled_repository_scans", "reviewable_fixes"]},
    {"id": "audio_uploads_and_summaries", "date": "2026-10-06", "area": "audio", "status": "catalogued",
     "features": ["audio_transcription", "meeting_summaries", "recording_questions"]},
    {"id": "gpt_live_sip", "date": "2026-11-02", "area": "voice", "status": "catalogued",
     "features": ["outbound_sip_calling", "telephony_provider_connection"]},
    {"id": "gpt_6_cyber", "date": "2026-11-05", "area": "cybersecurity", "status": "restricted",
     "features": ["authorized_vulnerability_investigation", "safe_reproduction", "fix_verification"],
     "shadow_surface": ["CORE-085", "CORE-086", "CORE-087", "CORE-138", "CORE-141"],
     "note": "Requires OpenAI approved-defender access and authorized test environments."},
    {"id": "gpt_6_1_terra", "date": "2026-10-26", "area": "visual_reasoning", "status": "catalogued",
     "features": ["visual_reasoning", "computer_use", "writing"]},
    {"id": "intelligent_ui", "date": "2026-10-07", "area": "conversation_ui", "status": "schema_implemented_client_render_pending",
     "features": ["adaptive_text_visual_interactive_responses", "progressive_response", "interactive_widgets"],
     "shadow_surface": ["Unified150Orchestrator", "Android conversation surface"]},
    {"id": "agents_computer_use", "date": "2026-09-29", "area": "agents", "status": "catalogued",
     "features": ["hosted_browser", "application_approved_access", "authenticated_sessions"]},
    {"id": "agents_multi_agent", "date": "2026-09-29", "area": "agents", "status": "catalogued",
     "features": ["parallel_subagents", "delegated_work"]},
    {"id": "agents_automations", "date": "2026-10-23", "area": "automation", "status": "catalogued",
     "features": ["one_time_schedules", "recurring_schedules", "session_followups"]},
    {"id": "agents_session_governance", "date": "2026-10-30", "area": "governance", "status": "catalogued",
     "features": ["live_tool_updates", "automatic_review", "background_context_compaction", "time_awareness_opt_in"]},
    {"id": "agents_sandbox_artifacts", "date": "2026-10-16", "area": "execution", "status": "catalogued",
     "features": ["hosted_sandbox", "artifact_sdk", "document_spreadsheet_presentation_skills", "sandbox_prewarming"]},
    {"id": "decisions_api", "date": "2026-10-22", "area": "structured_reasoning", "status": "catalogued",
     "features": ["typed_answers", "text_and_image_questions", "up_to_400_questions", "shared_input_cache"]},
    {"id": "gpt_live_api", "date": "2026-09-10", "area": "voice", "status": "catalogued",
     "features": ["full_duplex_audio", "custom_prompt_to_voice", "native_decisions_delegation", "outbound_sip"]},
    {"id": "api_performance", "date": "2026-10-08", "area": "performance", "status": "catalogued",
     "features": ["ultrafast_service_tier", "12_hour_prompt_cache", "async_tool_calls", "mid_turn_steering"]},
    {"id": "chatgpt_slides_pages", "date": "2026-10-16", "area": "productivity", "status": "design_target",
     "features": ["editable_slides", "team_collaboration", "present_and_export", "collaborative_pages"]},
    {"id": "dots", "date": "2026-09-29", "area": "agents", "status": "design_target",
     "features": ["persistent_goals", "proactive_progress", "connected_apps", "cloud_computer"]},
    {"id": "locked_chats_privacy", "date": "2026-10-20", "area": "privacy", "status": "design_target",
     "features": ["locked_conversations", "device_authentication", "separate_visibility"]},
    {"id": "memory_reference_images", "date": "2026-11-04", "area": "memory_images", "status": "design_target",
     "features": ["named_reference_images", "reusable_visual_context"]},
    {"id": "connected_apps_library", "date": "2026-09-10", "area": "connectors", "status": "design_target",
     "features": ["multi_account_connections", "box", "dropbox", "sharepoint", "source_citations"]},
    {"id": "voice_chat_plugins", "date": "2026-09-23", "area": "voice_agents", "status": "design_target",
     "features": ["voice_tool_use", "voice_work_handoff", "text_continuation"]},
    {"id": "audio_uploads", "date": "2026-10-06", "area": "audio", "status": "catalogued",
     "features": ["audio_transcription", "meeting_summaries", "recording_questions"]},
    {"id": "flashcards", "date": "2026-09-22", "area": "learning", "status": "design_target",
     "features": ["interactive_cards", "spaced_practice", "saved_decks"]},
    {"id": "security_history", "date": "2026-09-25", "area": "account_security", "status": "design_target",
     "features": ["sign_in_history", "mfa_changes", "passkey_changes"]},
    {"id": "codex_cloud_security", "date": "2026-09-29", "area": "software_security", "status": "design_target",
     "features": ["scheduled_repository_scans", "continuous_commit_checks", "reviewable_fixes"]},
)

def get_openai_capability_catalog() -> dict[str, Any]:
    return {
        "schema": "shadow.openai-capabilities.v1",
        "update_window": dict(UPDATE_WINDOW),
        "models": deepcopy(OPENAI_MODELS),
        "capabilities": deepcopy(list(OPENAI_CAPABILITIES)),
        "access_boundary": (
            "Public API documentation does not grant account, region, billing, or trusted-access eligibility. "
            "Restricted models require the provider's own approval."
        ),
    }

def get_openai_model(model_id: str) -> dict[str, Any] | None:
    value = OPENAI_MODELS.get(str(model_id or "").strip().lower())
    return deepcopy(value) if value else None

def select_image_model(*, task: str = "generate", precision: bool = False) -> str:
    if str(task).lower() in {"edit", "markup_edit", "recolor", "inpaint"} or precision:
        return "gpt-image-2.6-sunburst"
    return "gpt-image-2.6-flare"
