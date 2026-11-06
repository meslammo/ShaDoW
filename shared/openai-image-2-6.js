const API_BASE = "https://api.openai.com/v1";
const MODELS = Object.freeze({
  flare: "gpt-image-2.6-flare",
  sunburst: "gpt-image-2.6-sunburst",
});
const QUALITIES = new Set(["low", "medium", "high", "xhigh", "max", "auto"]);
const COMPLEXITIES = new Set(["low", "medium", "high"]);
const FORMATS = new Set(["png", "jpeg", "webp", "svg", "gif"]);
const BACKGROUNDS = new Set(["auto", "opaque", "transparent"]);

function fail(code, status = 400) {
  const error = new Error(code);
  error.code = code;
  error.status = status;
  throw error;
}

function normalizeModel(value = "flare") {
  const model = String(value || "flare").trim().toLowerCase();
  if (model === "flare" || model === MODELS.flare) return MODELS.flare;
  if (model === "sunburst" || model === MODELS.sunburst) return MODELS.sunburst;
  fail("unsupported_image_model");
}

function normalizeSize(value = "1024x1024") {
  const size = String(value || "1024x1024").trim().toLowerCase();
  if (size === "auto") return size;
  const match = size.match(/^([0-9]{2,4})x([0-9]{2,4})$/);
  if (!match) fail("invalid_image_size");
  const width = Number(match[1]);
  const height = Number(match[2]);
  const pixels = width * height;
  if (width % 16 || height % 16 || width > 3840 || height > 3840 ||
      Math.max(width, height) / Math.min(width, height) > 3 ||
      pixels < 655360 || pixels > 8294400) fail("image_size_out_of_range");
  return `${width}x${height}`;
}

function normalizeOptions(options = {}, { editing = false } = {}) {
  const model = normalizeModel(options.model || "flare");
  const quality = String(options.quality || "auto").toLowerCase();
  const complexity = String(options.complexity || "high").toLowerCase();
  const outputFormat = String(options.outputFormat || options.output_format || "png").toLowerCase();
  const background = String(options.background || "auto").toLowerCase();
  if (!QUALITIES.has(quality)) fail("invalid_image_quality");
  if (!COMPLEXITIES.has(complexity)) fail("invalid_image_complexity");
  if (!FORMATS.has(outputFormat)) fail("invalid_image_output_format");
  if (!BACKGROUNDS.has(background)) fail("invalid_image_background");
  if (editing && outputFormat === "svg") fail("svg_edit_not_supported");
  if (outputFormat === "svg" && Number(options.n || 1) !== 1) fail("svg_requires_single_output");
  if (background === "transparent" && !["png", "webp"].includes(outputFormat)) {
    fail("transparent_background_requires_png_or_webp");
  }
  const size = normalizeSize(options.size || "1024x1024");
  const n = Number(options.n ?? 1);
  if (!Number.isInteger(n) || n < 1 || n > 4) fail("invalid_image_count");
  const frameCount = Number(options.frameCount ?? options.frame_count ?? 9);
  const frameDurationMs = Number(options.frameDurationMs ?? options.frame_duration_ms ?? 200);
  if (outputFormat === "gif") {
    if (!Number.isInteger(frameCount) || frameCount < 2 || frameCount > 16) fail("invalid_gif_frame_count");
    if (!Number.isInteger(frameDurationMs) || frameDurationMs < 20 || frameDurationMs > 2000 || frameDurationMs % 10) {
      fail("invalid_gif_frame_duration");
    }
  }
  const outputCompression = options.outputCompression ?? options.output_compression;
  if (outputCompression !== undefined &&
      (!Number.isInteger(Number(outputCompression)) || Number(outputCompression) < 0 || Number(outputCompression) > 100)) {
    fail("invalid_output_compression");
  }
  return {
    model, quality, complexity, outputFormat, background, size, n,
    frameCount, frameDurationMs,
    ...(outputCompression === undefined ? {} : { outputCompression: Number(outputCompression) }),
  };
}

function decodeBase64(value, field) {
  const raw = String(value || "").trim().replace(/^data:[^,]+,/i, "");
  if (!raw || !/^[A-Za-z0-9+/]+={0,2}$/.test(raw)) fail(`invalid_${field}`);
  const bytes = Buffer.from(raw, "base64");
  if (!bytes.length || bytes.length > 50 * 1024 * 1024) fail(`${field}_size_invalid`);
  return bytes;
}

function normalizeMime(value, fallback = "image/png") {
  const mime = String(value || fallback).toLowerCase().split(";")[0].trim();
  if (!["image/png", "image/jpeg", "image/webp", "image/gif"].includes(mime)) fail("unsupported_image_mime_type");
  return mime;
}

function outputMime(format) {
  return format === "svg" ? "image/svg+xml" : format === "gif" ? "image/gif" :
    format === "jpeg" ? "image/jpeg" : format === "webp" ? "image/webp" : "image/png";
}

async function readResponse(response) {
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(String(payload?.error?.message || payload?.error?.code || `openai_image_http_${response.status}`).slice(0, 400));
    error.status = response.status;
    error.code = payload?.error?.code || "openai_image_request_failed";
    error.requestId = response.headers?.get?.("x-request-id") || null;
    throw error;
  }
  const item = Array.isArray(payload?.data) ? payload.data[0] : null;
  if (!item || (!item.b64_json && !item.url)) fail("openai_image_empty_response", 502);
  return {
    ok: true,
    provider: "openai",
    model: payload.model || null,
    image_base64: item.b64_json || null,
    image_url: item.url || null,
    mime_type: item.mime_type || null,
    output_format: item.mime_type === "image/svg+xml" ? "svg" : null,
    revised_prompt: item.revised_prompt || null,
    request_id: response.headers?.get?.("x-request-id") || payload.id || null,
    usage: payload.usage || null,
  };
}

function generationBody(prompt, options) {
  const normalized = normalizeOptions(options);
  const body = {
    model: normalized.model,
    prompt: String(prompt || "").trim().slice(0, 12000),
    size: normalized.size,
    quality: normalized.quality,
    complexity: normalized.complexity,
    output_format: normalized.outputFormat,
    background: normalized.background,
    n: normalized.n,
  };
  if (normalized.outputCompression !== undefined && ["jpeg", "webp"].includes(normalized.outputFormat)) {
    body.output_compression = normalized.outputCompression;
  }
  if (normalized.outputFormat === "gif") {
    body.frame_count = normalized.frameCount;
    body.frame_duration_ms = normalized.frameDurationMs;
  }
  return body;
}

export async function generateOpenAIImage({
  apiKey, prompt, options = {}, fetchImpl = fetch, timeoutMs = 180000,
} = {}) {
  if (!String(apiKey || "").trim()) fail("openai_image_not_configured", 503);
  if (!String(prompt || "").trim()) fail("prompt_required");
  const body = generationBody(prompt, options);
  const response = await fetchImpl(`${API_BASE}/images/generations`, {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });
  const result = await readResponse(response);
  return {
    ...result,
    mime_type: result.mime_type || outputMime(body.output_format),
    output_format: body.output_format,
    complexity: body.complexity,
    quality: body.quality,
  };
}

export async function editOpenAIImage({
  apiKey, prompt, imageBase64, imageMimeType = "image/png",
  maskBase64 = "", maskMimeType = "image/png",
  markupBase64 = "", markupMimeType = "image/png",
  options = {}, fetchImpl = fetch, timeoutMs = 180000,
} = {}) {
  if (!String(apiKey || "").trim()) fail("openai_image_not_configured", 503);
  if (!String(prompt || "").trim()) fail("prompt_required");
  if (!String(imageBase64 || "").trim()) fail("reference_image_required");
  const normalized = normalizeOptions(options, { editing: true });
  const form = new FormData();
  form.append("model", normalized.model);
  form.append("prompt", String(prompt).trim().slice(0, 12000));
  form.append("size", normalized.size);
  form.append("quality", normalized.quality);
  form.append("complexity", normalized.complexity);
  form.append("output_format", normalized.outputFormat);
  form.append("background", normalized.background);
  form.append("n", String(normalized.n));
  if (normalized.outputCompression !== undefined && ["jpeg", "webp"].includes(normalized.outputFormat)) {
    form.append("output_compression", String(normalized.outputCompression));
  }
  if (normalized.outputFormat === "gif") {
    form.append("frame_count", String(normalized.frameCount));
    form.append("frame_duration_ms", String(normalized.frameDurationMs));
  }
  const imageBytes = decodeBase64(imageBase64, "reference_image");
  const imageMime = normalizeMime(imageMimeType);
  form.append("image[]", new Blob([imageBytes], { type: imageMime }), `reference.${imageMime.split("/")[1]}`);
  if (maskBase64) {
    const bytes = decodeBase64(maskBase64, "mask");
    form.append("mask", new Blob([bytes], { type: normalizeMime(maskMimeType) }), "mask.png");
  }
  if (markupBase64) {
    const bytes = decodeBase64(markupBase64, "markup");
    form.append("markup", new Blob([bytes], { type: normalizeMime(markupMimeType) }), "markup.png");
  }
  const response = await fetchImpl(`${API_BASE}/images/edits`, {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}` },
    body: form,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const result = await readResponse(response);
  return {
    ...result,
    mime_type: result.mime_type || outputMime(normalized.outputFormat),
    output_format: normalized.outputFormat,
    complexity: normalized.complexity,
    quality: normalized.quality,
  };
}

export const openAIImageModels = MODELS;
export { normalizeOptions as validateOpenAIImageOptions };
