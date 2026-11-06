import json
from urllib.request import Request

from shadow.supernice.contracts import CoreRequest
from shadow.supernice.runtime import CoreRuntime


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload
    def __enter__(self):
        return self
    def __exit__(self, *_):
        return False
    def read(self, _limit):
        return json.dumps(self.payload).encode("utf-8")


def test_image_core_fails_honestly_when_api_endpoint_is_not_configured(tmp_path, monkeypatch):
    monkeypatch.delenv("SHADOW_IMAGE_API_BASE_URL", raising=False)
    result = CoreRuntime(workspace=str(tmp_path)).execute(
        CoreRequest("CORE-100", "create a SHADOW logo", context={"prompt": "SHADOW logo"})
    )
    assert result.ok is True
    assert result.status == "adapter_unavailable"
    assert result.result["available"] is False


def test_core_100_posts_image_request_to_configured_shadow_api(tmp_path, monkeypatch):
    monkeypatch.setenv("SHADOW_IMAGE_API_BASE_URL", "https://shadow.example")
    monkeypatch.setenv("SHADOW_IMAGE_API_TOKEN", "integration-token")
    captured = {}

    def fake_urlopen(request, timeout):
        captured["url"] = request.full_url
        captured["method"] = request.get_method()
        captured["headers"] = dict(request.header_items())
        captured["timeout"] = timeout
        captured["body"] = json.loads(request.data.decode("utf-8"))
        return FakeResponse({
            "ok": True,
            "provider": "openai",
            "model": "gpt-image-2.6-flare",
            "image_base64": "aGVsbG8=",
        })

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    result = CoreRuntime(workspace=str(tmp_path)).execute(
        CoreRequest(
            "CORE-100",
            "create image",
            context={"prompt": "A clean SHADOW logo", "output_format": "png"},
        )
    )
    assert result.ok is True
    assert result.status == "image_generated"
    assert captured["url"] == "https://shadow.example/v1/images"
    assert captured["method"] == "POST"
    assert captured["body"]["model"] == "gpt-image-2.6-flare"
    assert captured["body"]["prompt"] == "A clean SHADOW logo"
    assert result.evidence[0]["external_io"] is True


def test_core_101_requires_reference_and_uses_markup_edit_route(tmp_path, monkeypatch):
    monkeypatch.setenv("SHADOW_IMAGE_API_BASE_URL", "https://shadow.example")
    captured = {}

    def fake_urlopen(request, timeout):
        captured["url"] = request.full_url
        captured["body"] = json.loads(request.data.decode("utf-8"))
        return FakeResponse({
            "ok": True,
            "provider": "openai",
            "model": "gpt-image-2.6-sunburst",
            "image_base64": "aGVsbG8=",
        })

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    runtime = CoreRuntime(workspace=str(tmp_path))
    missing = runtime.execute(CoreRequest("CORE-101", "edit", context={"prompt": "recolor"}))
    assert missing.ok is False
    assert missing.status == "reference_image_required"

    result = runtime.execute(
        CoreRequest(
            "CORE-101",
            "edit image",
            context={
                "prompt": "Recolor the marked object",
                "reference_base64": "cmVmZXJlbmNl",
                "markup_base64": "bWFya3Vw",
                "output_format": "png",
            },
        )
    )
    assert result.ok is True
    assert captured["url"] == "https://shadow.example/v1/images/edits"
    assert captured["body"]["markup_base64"] == "bWFya3Vw"
    assert result.result["model"] == "gpt-image-2.6-sunburst"


def test_image_api_endpoint_must_use_https_except_local_development(tmp_path, monkeypatch):
    monkeypatch.setenv("SHADOW_IMAGE_API_BASE_URL", "http://remote.example")
    result = CoreRuntime(workspace=str(tmp_path)).execute(
        CoreRequest("CORE-100", "create", context={"prompt": "test"})
    )
    assert result.ok is False
    assert result.status == "image_api_url_must_use_https"
