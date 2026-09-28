import { createHash } from 'node:crypto';

const OPENAI_URL = 'https://api.openai.com/v1/responses';

const cfg = {
  openaiKey: String(process.env.OPENAI_API_KEY || '').trim(),
  openaiModel: String(process.env.SHADOW_VISION_MODEL || process.env.OPENAI_MODEL || 'gpt-5.6').trim(),
  geminiKey: String(process.env.GEMINI_API_KEY || '').trim(),
  geminiModel: String(process.env.SHADOW_GEMINI_VISION_MODEL || process.env.GEMINI_MODEL || 'gemini-3.8-flash').trim(),
  maxBytes: Number(process.env.SHADOW_MAX_VISION_BYTES || 12 * 1024 * 1024),
};

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function cleanMime(value) {
  const mime = String(value || 'image/jpeg').toLowerCase().split(';')[0].trim();
  return /^image\/(jpeg|jpg|png|webp|gif)$/.test(mime) ? (mime === 'image/jpg' ? 'image/jpeg' : mime) : 'image/jpeg';
}

function decodeImage(data, mimeType) {
  if (typeof data !== 'string' || !data.trim()) throw new Error('image_required');
  const normalized = data.includes(',') && data.startsWith('data:') ? data.slice(data.indexOf(',') + 1) : data;
  const bytes = Buffer.from(normalized, 'base64');
  if (!bytes.length) throw new Error('invalid_image_base64');
  if (bytes.length > cfg.maxBytes) throw new Error('image_too_large');
  return { data: normalized, bytes, mimeType: cleanMime(mimeType) };
}

function extractJson(text) {
  const raw = String(text || '').trim();
  if (!raw) throw new Error('empty_vision_result');
  try { return JSON.parse(raw); } catch {}
  const fenced = raw.match(/\`\`\`(?:json)?\s*([\s\S]*?)\`\`\`/i);
  if (fenced) {
    try { return JSON.parse(fenced[1]); } catch {}
  }
  const start = raw.indexOf('{');
  const end = raw.lastIndexOf('}');
  if (start >= 0 && end > start) {
    try { return JSON.parse(raw.slice(start, end + 1)); } catch {}
  }
  throw new Error('vision_result_not_json');
}

function normalize(result, meta = {}) {
  const safeArray = (x) => Array.isArray(x) ? x.slice(0, 100) : [];
  return {
    schema: 'shadow.semantic-vision.v1',
    provider: meta.provider || null,
    model: meta.model || null,
    image_hash: meta.imageHash || null,
    scene: typeof result?.scene === 'string' ? result.scene.slice(0, 4000) : '',
    objects: safeArray(result?.objects),
    people: safeArray(result?.people),
    text: safeArray(result?.text),
    regions: safeArray(result?.regions),
    relations: safeArray(result?.relations),
    attributes: safeArray(result?.attributes),
    observations: safeArray(result?.observations),
    comparison: result?.comparison && typeof result.comparison === 'object' ? result.comparison : null,
    prompt_compliance: result?.prompt_compliance && typeof result.prompt_compliance === 'object'
      ? result.prompt_compliance : { satisfied: false, notes: ['provider did not return compliance details'] },
    confidence: typeof result?.confidence === 'number' ? Math.max(0, Math.min(1, result.confidence)) : null,
    uncertainty: safeArray(result?.uncertainty),
  };
}

const VISION_INSTRUCTIONS = [
  'You are SHADOW Semantic Vision.',
  'Understand the actual pixels. Do not answer from filename, MIME type, metadata, or user assumptions.',
  'Return ONLY valid JSON matching the requested schema.',
  'Use visible evidence only. Do not identify real people by name or infer sensitive traits.',
  'Represent spatial relations explicitly and use regions/bounding boxes when visually supportable.',
  'OCR text must be tied to a region when possible.',
  'If something cannot be determined from the image, say so in uncertainty and lower confidence.',
].join(' ');

function schemaPrompt(userPrompt, comparison = false) {
  return `User task: ${String(userPrompt || 'Describe and analyze the image.').slice(0, 8000)}

Return JSON with this exact top-level shape:
{
  "scene": "short scene description",
  "objects": [{"label":"","attributes":[],"region":{"x":0,"y":0,"width":0,"height":0},"confidence":0}],
  "people": [{"description":"","region":{"x":0,"y":0,"width":0,"height":0},"confidence":0}],
  "text": [{"text":"","region":{"x":0,"y":0,"width":0,"height":0},"confidence":0}],
  "regions": [{"label":"","region":{"x":0,"y":0,"width":0,"height":0}}],
  "relations": [{"subject":"","relation":"","object":"","confidence":0}],
  "attributes": [{"target":"","attribute":"","value":"","confidence":0}],
  "observations": ["visible evidence"],
  "comparison": ${comparison ? '{"same_subject":null,"changed":[],"unchanged":[],"notes":[]}' : 'null'},
  "prompt_compliance": {"satisfied":true,"notes":[]},
  "confidence": 0,
  "uncertainty": []
}
Coordinates are normalized 0..1. Do not invent coordinates when unsupported.`;
}

async function openaiVision(images, prompt) {
  if (!cfg.openaiKey) throw new Error('openai_vision_not_configured');
  const content = [{ type: 'input_text', text: schemaPrompt(prompt, images.length > 1) }];
  for (const image of images) {
    content.push({ type: 'input_image', image_url: 'data:' + image.mimeType + ';base64,' + image.data, detail: 'high' });
  }
  const response = await fetch(OPENAI_URL, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + cfg.openaiKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: cfg.openaiModel,
      instructions: VISION_INSTRUCTIONS,
      input: [{ role: 'user', content }],
    }),
    signal: AbortSignal.timeout(90000),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw Object.assign(new Error(body?.error?.code || 'openai_vision_upstream_error'), { http: response.status });
  const text = typeof body?.output_text === 'string'
    ? body.output_text
    : (Array.isArray(body?.output) ? body.output.flatMap(x => Array.isArray(x.content) ? x.content : []).filter(x => x?.type === 'output_text').map(x => String(x.text || '')).join('') : '');
  return { result: extractJson(text), provider: 'openai', model: cfg.openaiModel };
}

async function geminiVision(images, prompt) {
  if (!cfg.geminiKey) throw new Error('gemini_vision_not_configured');
  const parts = [{ text: VISION_INSTRUCTIONS + '\n' + schemaPrompt(prompt, images.length > 1) }];
  for (const image of images) parts.push({ inline_data: { mime_type: image.mimeType, data: image.data } });
  const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(cfg.geminiModel)}:generateContent`, {
    method: 'POST',
    headers: { 'x-goog-api-key': cfg.geminiKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      contents: [{ role: 'user', parts }],
      generationConfig: { responseMimeType: 'application/json' },
    }),
    signal: AbortSignal.timeout(90000),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw Object.assign(new Error(body?.error?.message || 'gemini_vision_upstream_error'), { http: response.status });
  const text = Array.isArray(body?.candidates?.[0]?.content?.parts)
    ? body.candidates[0].content.parts.filter(x => typeof x?.text === 'string').map(x => x.text).join('')
    : '';
  return { result: extractJson(text), provider: 'gemini', model: cfg.geminiModel };
}

export function normalizeMultimodalInput({ message = '', imageBase64 = '', imageMimeType = 'image/jpeg', images = [] } = {}) {
  const source = Array.isArray(images) && images.length
    ? images
    : imageBase64 ? [{ data: imageBase64, mimeType: imageMimeType }] : [];
  const normalizedImages = source.slice(0, 4).map(item => decodeImage(item?.data || item?.image_base64, item?.mimeType || item?.content_type));
  return {
    message: String(message || '').trim().slice(0, 12000),
    images: normalizedImages,
    hasImages: normalizedImages.length > 0,
    imageCount: normalizedImages.length,
    imageHashes: normalizedImages.map(x => sha256(x.bytes)),
  };
}

export async function analyzeSemanticVision(input) {
  const normalized = normalizeMultimodalInput(input);
  if (!normalized.hasImages) return { active: false, normalized, result: null };
  const prompt = normalized.message || 'حلل الصورة بدقة واذكر ما يمكن التحقق منه فقط.';
  const attempts = [];
  const providers = [
    ['openai', () => openaiVision(normalized.images, prompt)],
    ['gemini', () => geminiVision(normalized.images, prompt)],
  ];
  for (const [provider, fn] of providers) {
    try {
      const out = await fn();
      return {
        active: true,
        normalized: { ...normalized, images: undefined },
        provider: out.provider,
        model: out.model,
        attempts,
        result: normalize(out.result, { provider: out.provider, model: out.model, imageHash: normalized.imageHashes[0] }),
      };
    } catch (error) {
      attempts.push({ provider, reason: String(error?.message || error), http: error?.http || null });
    }
  }
  const error = new Error('no_semantic_vision_provider_available');
  error.attempts = attempts;
  throw error;
}

export async function compareSemanticVision({ message = '', images = [] } = {}) {
  const normalized = normalizeMultimodalInput({ message, images });
  if (normalized.images.length !== 2) throw new Error('exactly_two_images_required');
  return analyzeSemanticVision({ message: normalized.message || 'قارن الصورتين بدقة وحدد ما هو متشابه وما هو مختلف.', images });
}

export function semanticVisionStatus() {
  return {
    configured: Boolean(cfg.openaiKey || cfg.geminiKey),
    providers: {
      openai: { configured: Boolean(cfg.openaiKey), model: cfg.openaiModel },
      gemini: { configured: Boolean(cfg.geminiKey), model: cfg.geminiModel },
    },
    max_bytes: cfg.maxBytes,
    schema: 'shadow.semantic-vision.v1',
  };
}
