from shadow.supernice.openai_capabilities import (
    OPENAI_CAPABILITIES,
    OPENAI_MODELS,
    UPDATE_WINDOW,
    get_openai_capability_catalog,
    get_openai_model,
    select_image_model,
)
from shadow.supernice.orchestrator import Unified150Orchestrator


def test_openai_update_window_and_latest_image_models_are_catalogued():
    assert UPDATE_WINDOW == {"from": "2026-09-01", "through": "2026-11-06"}
    assert "gpt-image-2.6-flare" in OPENAI_MODELS
    assert "gpt-image-2.6-sunburst" in OPENAI_MODELS
    assert get_openai_model("gpt-6.1-terra")["kind"] == "visual_computer_use"


def test_restricted_models_are_not_misrepresented_as_public_access():
    assert get_openai_model("gpt-6-cyber")["access"] == "approved_defenders_only"
    assert get_openai_model("gpt-6-rosalind")["access"] == "trusted_access_only"
    catalog = get_openai_capability_catalog()
    assert "does not grant account" in catalog["access_boundary"]


def test_images_2_6_catalog_is_integrated_and_has_expected_surfaces():
    image = next(x for x in OPENAI_CAPABILITIES if x["id"] == "images_2_6")
    assert image["status"] == "integrated"
    assert "gif" in image["features"]
    assert "svg" in image["features"]
    assert "/v1/images/edits" in image["shadow_surface"]


def test_image_model_selection_uses_flare_for_fast_and_sunburst_for_precision():
    assert select_image_model(task="generate") == "gpt-image-2.6-flare"
    assert select_image_model(task="edit") == "gpt-image-2.6-sunburst"
    assert select_image_model(precision=True) == "gpt-image-2.6-sunburst"


def test_unified_orchestrator_routes_image_creation_and_editing_separately():
    assert Unified150Orchestrator.select_core("اعمل صورة جديدة لشعار SHADOW") == "CORE-100"
    assert Unified150Orchestrator.select_core("عدّل الصورة وغيّر الخلفية") == "CORE-101"
    assert Unified150Orchestrator.select_core("حلل الصورة دي") == "CORE-099"
