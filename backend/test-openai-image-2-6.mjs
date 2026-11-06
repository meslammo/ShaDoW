import test from "node:test";
import assert from "node:assert/strict";
import { generateOpenAIImage, editOpenAIImage } from "../shared/openai-image-2-6.js";

function response(payload, status=200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "x-request-id": "req-shadow-test" }),
    async json() { return payload; },
  };
}

test("Images 2.6 Flare generation sends complexity quality and returns a PNG artifact", async () => {
  let captured;
  const result = await generateOpenAIImage({
    apiKey: "test-secret",
    prompt: "A clean product photo of a ceramic coffee cup",
    options: { model: "flare", size: "1024x1024", quality: "high", complexity: "medium" },
    fetchImpl: async (url, init) => {
      captured = { url, init, body: JSON.parse(init.body) };
      return response({ model: "gpt-image-2.6-flare", data: [{ b64_json: "aGVsbG8=" }] });
    },
  });
  assert.equal(captured.url, "https://api.openai.com/v1/images/generations");
  assert.equal(captured.init.headers.Authorization, "Bearer test-secret");
  assert.equal(captured.body.model, "gpt-image-2.6-flare");
  assert.equal(captured.body.quality, "high");
  assert.equal(captured.body.complexity, "medium");
  assert.equal(result.provider, "openai");
  assert.equal(result.mime_type, "image/png");
  assert.equal(result.image_base64, "aGVsbG8=");
  assert.equal(result.request_id, "req-shadow-test");
});

test("Images 2.6 GIF generation validates and sends animation controls", async () => {
  let body;
  await generateOpenAIImage({
    apiKey: "test-secret",
    prompt: "A seamless looping animation of a lantern glowing",
    options: { model: "sunburst", outputFormat: "gif", frameCount: 12, frameDurationMs: 300 },
    fetchImpl: async (_url, init) => {
      body = JSON.parse(init.body);
      return response({ model: "gpt-image-2.6-sunburst", data: [{ b64_json: "R0lGODlh" }] });
    },
  });
  assert.equal(body.output_format, "gif");
  assert.equal(body.frame_count, 12);
  assert.equal(body.frame_duration_ms, 300);
});

test("Images 2.6 SVG generation is restricted to one output", async () => {
  let body;
  await generateOpenAIImage({
    apiKey: "test-secret",
    prompt: "A minimal vector leaf icon",
    options: { outputFormat: "svg", n: 1 },
    fetchImpl: async (_url, init) => {
      body = JSON.parse(init.body);
      return response({ model: "gpt-image-2.6-flare", data: [{ b64_json: "PHN2Zy8+" }] });
    },
  });
  assert.equal(body.output_format, "svg");
  await assert.rejects(
    generateOpenAIImage({
      apiKey: "test-secret", prompt: "vector", options: { outputFormat: "svg", n: 2 },
      fetchImpl: async () => response({ data: [{ b64_json: "PHN2Zy8+" }] }),
    }),
    /svg_requires_single_output/,
  );
});

test("GIF parameters and image dimensions are validated before network access", async () => {
  let called = false;
  await assert.rejects(
    generateOpenAIImage({
      apiKey: "test-secret", prompt: "animate",
      options: { outputFormat: "gif", frameCount: 1 },
      fetchImpl: async () => { called = true; return response({}); },
    }),
    /invalid_gif_frame_count/,
  );
  assert.equal(called, false);
  await assert.rejects(
    generateOpenAIImage({
      apiKey: "test-secret", prompt: "invalid dimensions",
      options: { size: "1000x1000" },
      fetchImpl: async () => { called = true; return response({}); },
    }),
    /image_size_out_of_range/,
  );
  assert.equal(called, false);
});

test("Image edits send reference, mask, and markup as multipart files", async () => {
  let captured;
  const result = await editOpenAIImage({
    apiKey: "test-secret",
    prompt: "Recolor only the marked object and preserve everything else",
    imageBase64: Buffer.from("reference-image").toString("base64"),
    imageMimeType: "image/png",
    maskBase64: Buffer.from("mask").toString("base64"),
    markupBase64: Buffer.from("markup").toString("base64"),
    options: { model: "sunburst", quality: "high", complexity: "low" },
    fetchImpl: async (url, init) => {
      captured = { url, init, form: init.body };
      return response({ model: "gpt-image-2.6-sunburst", data: [{ b64_json: "aGVsbG8=" }] });
    },
  });
  assert.equal(captured.url, "https://api.openai.com/v1/images/edits");
  assert.equal(captured.init.headers.Authorization, "Bearer test-secret");
  assert.equal(captured.form.get("model"), "gpt-image-2.6-sunburst");
  assert.equal(captured.form.get("complexity"), "low");
  assert.equal(captured.form.get("image[]") instanceof Blob, true);
  assert.equal(captured.form.get("mask") instanceof Blob, true);
  assert.equal(captured.form.get("markup") instanceof Blob, true);
  assert.equal(result.provider, "openai");
});

test("API failures expose a safe code and request id without exposing credentials", async () => {
  await assert.rejects(
    generateOpenAIImage({
      apiKey: "never-print-this",
      prompt: "test",
      fetchImpl: async () => response({ error: { code: "rate_limit_exceeded", message: "slow down" } }, 429),
    }),
    error => {
      assert.equal(error.status, 429);
      assert.equal(error.code, "rate_limit_exceeded");
      assert.equal(error.requestId, "req-shadow-test");
      assert.equal(error.message.includes("never-print-this"), false);
      return true;
    },
  );
});
