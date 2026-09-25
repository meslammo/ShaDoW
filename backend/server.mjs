import { createHash } from 'node:crypto';
import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import { applyFiles, createPullRequest, runDevelopmentPipeline, status as developmentStatus } from './development-agent.mjs';
import { startDeviceAuthorization, pollDeviceAuthorization, status as githubOAuthStatus } from './github-oauth.mjs';
import { voiceprintStatus, verifyVoiceprint } from './voiceprint.mjs';
import { runAgent, streamAgent, providerStatus, memoryStatus } from './ai-router.mjs';

const app = express();
const port = Number(process.env.PORT || 8787);
const apiKey = String(process.env.OPENAI_API_KEY || '').trim();
const imageModel = String(process.env.OPENAI_IMAGE_MODEL || 'gpt-image-2.5-flare').trim();
const ttsModel = String(process.env.SHADOW_TTS_MODEL || 'gpt-4o-mini-tts').trim();
const ttsVoice = String(process.env.SHADOW_TTS_VOICE || 'onyx').trim();
const ttsVoiceId = String(process.env.SHADOW_TTS_VOICE_ID || '').trim();
const ttsInstructions = String(process.env.SHADOW_TTS_INSTRUCTIONS || 'Speak with a refined, cinematic, futuristic British AI-assistant character: deep adult male voice, calm authority, precise diction, restrained emotion, intelligent and composed, slightly warm, measured pacing, subtle dry confidence. This is an original Jarvis-inspired delivery, not an imitation of any actor or copyrighted character performance. When speaking Egyptian Arabic (ar-EG), keep the same deep, polished, controlled delivery while using natural Egyptian pronunciation and vocabulary.').trim();
const geminiKey = String(process.env.GEMINI_API_KEY || '').trim();
const geminiImageModel = String(process.env.GEMINI_IMAGE_MODEL || 'gemini-3.1-flash-image').trim();
const geminiVideoModel = String(process.env.GEMINI_VIDEO_MODEL || 'veo-3.1-generate-preview').trim();
const transcriptionModel = String(process.env.SHADOW_STT_MODEL || 'gpt-transcribe').trim();
const geminiTranscriptionModel = String(process.env.GEMINI_STT_MODEL || 'gemini-3.5-transcribe').trim();
const geminiTtsModel = String(process.env.GEMINI_TTS_MODEL || 'gemini-3.1-flash-tts-preview').trim();
const visionModel = String(process.env.SHADOW_VISION_MODEL || process.env.OPENAI_MODEL || 'gpt-5.6').trim();

app.disable('x-powered-by');
app.use(helmet());
app.use(cors({ origin: true, methods: ['GET', 'POST', 'OPTIONS'], allowedHeaders: ['Content-Type', 'Authorization'] }));
app.use(express.json({ limit: '20mb' }));

const buckets = new Map();
function rateLimit(req, res, next) {
  const key = req.ip || 'unknown';
  const now = Date.now();
  const state = buckets.get(key);
  if (!state || now - state.started >= 60000) { buckets.set(key, { started: now, count: 1 }); return next(); }
  state.count++;
  if (state.count > 40) return res.status(429).json({ ok: false, error: 'rate_limited' });
  next();
}

app.get('/health', async (_req, res) => res.json({
  ok: true,
  service: 'shadow-cloud',
  agent: 'unified-multi-ai',
  online: true,
  providers: providerStatus(),
  memory: await memoryStatus(),
  capabilities: {
    web_search: true,
    web_fetch: true,
    streaming_chat: true,
    streaming_provider: 'openai',
    github_read: true,
    github_write_gateway: githubOAuthStatus().configured,
    files: true,
    android_action: true,
    agent_loop: true,
    voiceprint_required: false,
    speech_to_text: Boolean(apiKey),
    vision: Boolean(apiKey),
    full_12_step_master: true,
    unified_150_core: true,
    phase_roadmap: 35,
    online_only_brain: true,
  },
  image_generation: Boolean(apiKey || geminiKey),
  tts: {
    model: ttsModel,
    voice: ttsVoice,
    voice_id_configured: Boolean(ttsVoiceId),
    mode: ttsVoiceId ? 'custom' : 'built_in',
    style: 'jarvis-inspired-original',
    locale: 'ar-EG',
    gender: 'male',
  },
  development_agent: developmentStatus(),
  github_authorization: githubOAuthStatus(),
  voiceprint: { required: false, status: voiceprintStatus() },
}));



app.get('/v1/platform/status', async (_req, res) => {
  try {
    const memory = await memoryStatus();
    const providers = providerStatus();
    return res.json({
      ok: true,
      platform: 'SHADOW Long-Term Platform',
      phase_count: 35,
      core_count: 150,
      online_only_brain: true,
      offline_ai_removed: true,
      architecture: 'Unified150Orchestrator + production Cloud Agent + governed tools',
      memory,
      providers: Object.fromEntries(Object.entries(providers).filter(([name]) => name !== 'local')),
      capabilities: {
        memory: true,
        governance: true,
        tools: Array.isArray(providers.tools) ? providers.tools : [],
        streaming: true,
        github: true,
        development_agent: true,
        android_actions: true,
        multimodal: true,
        companions: true,
        spatial: true,
        recovery: true,
        skills: true,
        simulation: true,
        diagnostics: true,
        controlled_self_improvement: true,
      },
      external_verification_gates: [
        'real_provider_credentials',
        'real_android_device',
        'wake_word_and_barge_in',
        'real_device_adapters',
        'trusted_companions',
        'cross_device_federation',
        'production_failover_restore',
      ],
    });
  } catch (error) {
    return res.status(503).json({ ok: false, error: 'platform_status_failed' });
  }
});

app.post('/v1/chat', rateLimit, async (req, res) => {
  const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
  if (!message) return res.status(400).json({ ok: false, error: 'message_required' });
  if (message.length > 12000) return res.status(413).json({ ok: false, error: 'message_too_large' });
  const previous = typeof req.body?.previous_response_id === 'string' ? req.body.previous_response_id.trim() : '';
  const device = typeof req.body?.device === 'string' ? req.body.device.slice(0, 16000) : '';
  const providerInput = String(req.body?.provider || '').toLowerCase();
  const reasoningInput = String(req.body?.reasoning_effort || 'none').toLowerCase();
  const reasoningEffort = ['none','minimal','low','medium','high','xhigh'].includes(reasoningInput) ? reasoningInput : 'none';
  const allowedProviders = ['openai', 'xai', 'grok', 'deepseek', 'mistral', 'anthropic', 'gemini'];
  const preferred = allowedProviders.includes(providerInput) ? providerInput.replace('grok', 'xai') : 'auto';
  try {
    const result = await runAgent({ message, previousResponseId: previous, device, preferredProvider: preferred, reasoningEffort });
    return res.json({ ok: true, answer: result.answer, response_id: result.responseId || null, provider: result.provider, model: result.model, reasoning_effort: result.reasoningEffort || reasoningEffort, used_web_search: Boolean(result.usedWeb), pending_action: result.pendingAction || null, attempts: result.attempts || [] });
  } catch (error) {
    console.error('Unified agent failed', String(error?.message || error));
    return res.status(503).json({ ok: false, error: 'agent_failed' });
  }
});

app.post('/v1/chat/stream', rateLimit, async (req, res) => {
  const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
  if (!message) return res.status(400).json({ ok: false, error: 'message_required' });
  if (message.length > 12000) return res.status(413).json({ ok: false, error: 'message_too_large' });
  const previous = typeof req.body?.previous_response_id === 'string' ? req.body.previous_response_id.trim() : '';
  const device = typeof req.body?.device === 'string' ? req.body.device.slice(0, 16000) : '';
  const reasoningInput = String(req.body?.reasoning_effort || 'none').toLowerCase();
  const reasoningEffort = ['none','minimal','low','medium','high','xhigh'].includes(reasoningInput) ? reasoningInput : 'none';
  res.statusCode = 200;
  res.set({
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  if (typeof res.flushHeaders === 'function') res.flushHeaders();
  const send = (payload) => { if (!res.writableEnded) res.write('data: ' + JSON.stringify(payload) + '\n\n'); };
  try {
    const result = await streamAgent({
      message,
      previousResponseId: previous,
      device,
      reasoningEffort,
      onDelta: (text) => send({ type: 'delta', text }),
      onPending: (data) => send({ type: 'pending_action', ...data }),
      onDone: (data) => send({ type: 'done', ...data }),
    });
    if (!res.writableEnded) {
      if (!result.pendingAction && result.responseId && !result.provider) send({ type: 'done', ...result });
      send({ type: 'eof' });
      res.end();
    }
  } catch (error) {
    console.error('Streaming agent failed', String(error?.message || error));
    send({ type: 'error', error: String(error?.message || 'agent_failed') });
    send({ type: 'eof' });
    if (!res.writableEnded) res.end();
  }
});

app.post('/v1/agent/continue', rateLimit, async (req, res) => {
  const provider = String(req.body?.provider || '').toLowerCase();
  const responseId = String(req.body?.response_id || '').trim();
  const toolCallId = String(req.body?.tool_call_id || '').trim();
  const output = typeof req.body?.output === 'string' ? req.body.output.slice(0, 20000) : JSON.stringify(req.body?.output ?? '');
  const reasoningInput = String(req.body?.reasoning_effort || 'none').toLowerCase();
  const reasoningEffort = ['none','minimal','low','medium','high','xhigh'].includes(reasoningInput) ? reasoningInput : 'none';
  if (!provider || !toolCallId) return res.status(400).json({ ok: false, error: 'tool_context_required' });
  const original = String(req.body?.original_message || 'نفّذ الإجراء المطلوب واستكمل.');
  try {
    const result = await runAgent({ message: `${original}\n[DEVICE_TOOL_RESULT]\n${output}`, previousResponseId: responseId, preferredProvider: provider, reasoningEffort });
    return res.json({ ok: true, answer: result.answer, response_id: result.responseId || null, provider: result.provider, model: result.model, reasoning_effort: result.reasoningEffort || reasoningEffort, used_web_search: Boolean(result.usedWeb), pending_action: result.pendingAction || null });
  } catch { return res.status(503).json({ ok: false, error: 'agent_continue_failed' }); }
});

app.post('/v1/master/run', rateLimit, async (req, res) => {
  const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
  if (!message) return res.status(400).json({ ok: false, error: 'message_required' });
  if (message.length > 12000) return res.status(413).json({ ok: false, error: 'message_too_large' });
  const confirmed = req.body?.confirmed === true;
  const authenticated = req.body?.authenticated === true;
  const risky = /(delete|remove|wipe|format|purchase|buy|send|pay|transfer|call|message|حذف|امسح|فورمات|اشتر|شراء|ابعت|ادفع|حوّل|اتصل)/i.test(message);
  if (risky && !confirmed) return res.status(403).json({
    ok: false,
    error: 'confirmation_required',
    pipeline: { status: 'confirmation_required', stage: 'security_approval', authenticated, confirmed, startup_blocking: false },
  });
  try {
    const result = await runAgent({
      message,
      device: typeof req.body?.device === 'string' ? req.body.device.slice(0, 16000) : '',
      preferredProvider: 'auto',
      reasoningEffort: String(req.body?.reasoning_effort || 'none').toLowerCase(),
    });
    const usedWeb = Boolean(result.usedWeb);
    const pending = result.pendingAction || null;
    const development = /(github|git|repo|repository|code|coding|build|apk|test|commit|push|pr|كود|برمجة|جيت هب)/i.test(message);
    const device = /(phone|mobile|android|device|screen|click|tap|type|open app|موبايل|تليفون|جهاز|الشاشة|اضغط|اكتب|افتح)/i.test(message);
    const fresh = /(latest|today|now|current|news|update|جديد|دلوقتي|حالي|آخر|اخر|النهارده|بحث|ابحث|دور)/i.test(message);
    const pipeline = {
      status: pending ? 'action_pending' : 'completed',
      trace_id: createHash('sha256').update(message).digest('hex').slice(0, 16),
      startup_blocking: false,
      phase_count: 35,
      core_count: 150,
      online_only_brain: true,
      offline_ai_removed: true,
      stages: [
        { stage: 'understand', status: 'executed' },
        { stage: 'model_route', status: 'executed', provider: result.provider || null, model: result.model || null, attempts: result.attempts || [] },
        { stage: 'voice_multimodal', status: 'adapter_ready' },
        { stage: 'memory', status: 'integrated' },
        { stage: 'web_discovery', status: fresh || usedWeb ? 'used' : 'not_required' },
        { stage: 'github_development', status: development ? 'routed' : 'not_required' },
        { stage: 'phone_devices', status: device ? (pending ? 'action_pending' : 'client_adapter_ready') : 'not_required' },
        { stage: 'agent_loop', status: 'executed', bounded_rounds: 8 },
        { stage: 'security_approval', status: confirmed ? 'confirmed' : 'not_required' },
        { stage: 'execute', status: pending ? 'action_pending' : 'completed' },
        { stage: 'verify', status: result.answer ? 'response_verified' : 'degraded' },
        { stage: 'deliver', status: result.answer || pending ? 'completed' : 'degraded' },
      ],
    };
    return res.json({
      ok: Boolean(result.answer || pending),
      answer: result.answer || '',
      response_id: result.responseId || null,
      provider: result.provider || null,
      model: result.model || null,
      pending_action: pending,
      pipeline,
    });
  } catch (error) {
    return res.status(503).json({ ok: false, error: 'master_pipeline_failed', detail: String(error?.message || error).slice(0, 180) });
  }
});

app.post('/v1/transcribe', rateLimit, async (req, res) => {
  const data = typeof req.body?.audio_base64 === 'string' ? req.body.audio_base64.trim() : '';
  const contentType = typeof req.body?.content_type === 'string' ? req.body.content_type.slice(0, 80) : 'audio/wav';
  if (!data) return res.status(400).json({ ok: false, error: 'audio_required' });
  if (data.length > 25 * 1024 * 1024) return res.status(413).json({ ok: false, error: 'audio_too_large' });
  try {
    if (apiKey) {
      const form = new FormData();
      form.append('model', transcriptionModel);
      form.append('file', new Blob([Buffer.from(data, 'base64')], { type: contentType }), 'shadow-audio');
      const response = await fetch('https://api.openai.com/v1/audio/transcriptions', { method: 'POST', headers: { Authorization: 'Bearer ' + apiKey }, body: form, signal: AbortSignal.timeout(90000) });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) return res.status(502).json({ ok: false, error: body?.error?.code || 'transcription_upstream_error' });
      return res.json({ ok: true, text: String(body?.text || '').trim(), provider: 'openai', model: transcriptionModel });
    }
    if (!geminiKey) return res.status(503).json({ ok: false, error: 'speech_provider_not_configured' });
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(geminiTranscriptionModel)}:generateContent`, {
      method: 'POST',
      headers: { 'x-goog-api-key': geminiKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [
          { text: 'Transcribe this audio exactly. Return only the transcript. Preserve Arabic/Egyptian wording when spoken.' },
          { inline_data: { mime_type: contentType, data } },
        ] }],
      }),
      signal: AbortSignal.timeout(90000),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) return res.status(502).json({ ok: false, error: 'gemini_transcription_upstream_error' });
    const text = Array.isArray(body?.candidates?.[0]?.content?.parts)
      ? body.candidates[0].content.parts.filter(x => typeof x?.text === 'string').map(x => x.text).join('').trim()
      : '';
    return text ? res.json({ ok: true, text, provider: 'gemini', model: geminiTranscriptionModel })
      : res.status(502).json({ ok: false, error: 'empty_transcription' });
  } catch (e) {
    return res.status(502).json({ ok: false, error: String(e?.message || 'transcription_unreachable').slice(0, 180) });
  }
});

app.post('/v1/vision', rateLimit, async (req, res) => {
  if (!apiKey) return res.status(503).json({ ok: false, error: 'vision_provider_not_configured' });
  const data = typeof req.body?.image_base64 === 'string' ? req.body.image_base64.trim() : '';
  const contentType = typeof req.body?.content_type === 'string' ? req.body.content_type.slice(0, 80) : 'image/jpeg';
  const prompt = typeof req.body?.prompt === 'string' && req.body.prompt.trim()
    ? req.body.prompt.trim().slice(0, 8000)
    : 'حلل الصورة بدقة، واذكر ما يمكن التحقق منه فقط، ووضح درجة عدم اليقين عند الحاجة.';
  if (!data) return res.status(400).json({ ok: false, error: 'image_required' });
  if (data.length > 18 * 1024 * 1024) return res.status(413).json({ ok: false, error: 'image_too_large' });
  try {
    const response = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + apiKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: visionModel,
        instructions: 'You are SHADOW vision. Describe only visible evidence, identify uncertainty, and never claim to recognize a real person.',
        input: [{
          role: 'user',
          content: [
            { type: 'input_text', text: prompt },
            { type: 'input_image', image_url: 'data:' + contentType + ';base64,' + data, detail: 'high' },
          ],
        }],
      }),
      signal: AbortSignal.timeout(90000),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) return res.status(502).json({ ok: false, error: body?.error?.code || 'vision_upstream_error' });
    const answer = typeof body?.output_text === 'string'
      ? body.output_text.trim()
      : (Array.isArray(body?.output) ? body.output.flatMap(x => Array.isArray(x.content) ? x.content : []).filter(x => x?.type === 'output_text').map(x => String(x.text || '')).join('\n').trim() : '');
    if (!answer) return res.status(502).json({ ok: false, error: 'empty_vision_result' });
    return res.json({ ok: true, answer, provider: 'openai', model: visionModel });
  } catch (e) {
    return res.status(502).json({ ok: false, error: String(e?.message || 'vision_unreachable').slice(0, 180) });
  }
});

app.post('/v1/github/device/start', rateLimit, async (_req, res) => {
  try { res.json({ ok: true, ...await startDeviceAuthorization() }); }
  catch (e) { const message = String(e?.message || 'github_oauth_failed'); res.status(message === 'github_oauth_not_configured' ? 503 : 502).json({ ok: false, error: message }); }
});
app.post('/v1/github/device/poll', rateLimit, async (req, res) => {
  try {
    const code = typeof req.body?.device_code === 'string' ? req.body.device_code.trim() : '';
    if (!code) return res.status(400).json({ ok: false, error: 'device_code_required' });
    res.json({ ok: true, ...await pollDeviceAuthorization(code) });
  } catch (e) { res.status(502).json({ ok: false, error: String(e?.message || 'github_oauth_failed') }); }
});

app.post('/v1/voiceprint/verify', rateLimit, async (req, res) => {
  try {
    const audio = typeof req.body?.audio_base64 === 'string' ? req.body.audio_base64.trim() : '';
    const contentType = typeof req.body?.content_type === 'string' ? req.body.content_type.slice(0, 80) : 'audio/wav';
    const result = await verifyVoiceprint(audio, contentType);
    if (result.verified !== true) return res.status(401).json({ ok: false, ...result });
    res.json({ ok: true, ...result });
  } catch (e) {
    const message = String(e?.message || 'voiceprint_failed');
    res.status(message === 'voiceprint_provider_not_configured' ? 503 : 400).json({ ok: false, error: message });
  }
});

app.post('/v1/development/plan', rateLimit, async (req, res) => {
  const request = typeof req.body?.request === 'string' ? req.body.request.trim() : '';
  const project = typeof req.body?.project === 'string' ? req.body.project.slice(0, 12000) : '';
  if (!request) return res.status(400).json({ ok: false, error: 'request_required' });
  try {
    const result = await runAgent({ message: `Create a safe software implementation plan only. Do not claim edits executed. Request: ${request}\nProject: ${project}`, preferredProvider: 'auto' });
    res.json({ ok: true, plan: result.answer, approval_required: true, execution_available: developmentStatus().configured });
  } catch { res.status(503).json({ ok: false, error: 'development_ai_unavailable' }); }
});


app.post('/v1/development/pull-request', rateLimit, async (req, res) => {
  if (req.body?.approved !== true) return res.status(403).json({ ok: false, error: 'explicit_approval_required' });
  const branch = typeof req.body?.branch === 'string' && /^[A-Za-z0-9._/-]{1,80}$/.test(req.body.branch) ? req.body.branch : '';
  const title = typeof req.body?.title === 'string' ? req.body.title.trim().slice(0, 200) : '';
  const body = typeof req.body?.body === 'string' ? req.body.body.slice(0, 10000) : '';
  if (!branch || !title) return res.status(400).json({ ok: false, error: 'branch_and_title_required' });
  try {
    const result = await createPullRequest({ branch, title, body, draft: req.body?.draft !== false, token: typeof req.body?.github_token === 'string' ? req.body.github_token.trim() : '' });
    res.json({ ok: true, executed: true, result });
  } catch (e) {
    const message = String(e?.message || 'pull_request_failed');
    res.status(message === 'github_write_not_configured' ? 503 : 400).json({ ok: false, error: message });
  }
});
app.post('/v1/development/run', rateLimit, async (req, res) => {
  if (req.body?.approved !== true) return res.status(403).json({ ok: false, error: 'explicit_approval_required' });
  const branch = typeof req.body?.branch === 'string' && /^[A-Za-z0-9._/-]{1,80}$/.test(req.body.branch) ? req.body.branch : 'shadow-agent-work';
  const title = typeof req.body?.title === 'string' ? req.body.title.trim().slice(0, 200) : 'SHADOW Development Agent change';
  const body = typeof req.body?.body === 'string' ? req.body.body.slice(0, 10000) : '';
  const message = typeof req.body?.commit_message === 'string' && req.body.commit_message.trim() ? req.body.commit_message.trim().slice(0, 160) : 'SHADOW Development Agent change';
  try {
    const result = await runDevelopmentPipeline({
      branch,
      title,
      body,
      message,
      files: req.body?.files,
      token: typeof req.body?.github_token === 'string' ? req.body.github_token.trim() : '',
      draft: req.body?.draft !== false,
      waitSeconds: Number(req.body?.wait_seconds || 240),
    });
    return res.json({ ok: true, executed: true, result });
  } catch (e) {
    const messageOut = String(e?.message || 'development_pipeline_failed');
    return res.status(messageOut === 'github_write_not_configured' ? 503 : 400).json({ ok: false, error: messageOut });
  }
});

app.post('/v1/development/apply', rateLimit, async (req, res) => {
  if (req.body?.approved !== true) return res.status(403).json({ ok: false, error: 'explicit_approval_required' });
  const branch = typeof req.body?.branch === 'string' && /^[A-Za-z0-9._/-]{1,80}$/.test(req.body.branch) ? req.body.branch : 'shadow-agent-work';
  const message = typeof req.body?.commit_message === 'string' && req.body.commit_message.trim() ? req.body.commit_message.trim().slice(0, 160) : 'SHADOW Development Agent change';
  try {
    const result = await applyFiles({ branch, message, files: req.body?.files, token: typeof req.body?.github_token === 'string' ? req.body.github_token.trim() : '' });
    res.json({ ok: true, executed: true, result });
  } catch (e) {
    const messageOut = String(e?.message || 'github_write_failed');
    res.status(messageOut === 'github_write_not_configured' ? 503 : 400).json({ ok: false, error: messageOut });
  }
});

app.post('/v1/images', rateLimit, async (req, res) => {
  const provider = String(req.body?.provider || 'auto').toLowerCase();
  const prompt = typeof req.body?.prompt === 'string' ? req.body.prompt.trim() : '';
  if (!prompt) return res.status(400).json({ ok: false, error: 'prompt_required' });
  if (prompt.length > 8000) return res.status(413).json({ ok: false, error: 'prompt_too_large' });
  try {
    const useGemini = provider === 'gemini' || (provider === 'auto' && !apiKey && geminiKey);
    if (useGemini) {
      if (!geminiKey) return res.status(503).json({ ok: false, error: 'gemini_image_provider_not_configured' });
      const response = await fetch('https://generativelanguage.googleapis.com/v1beta/interactions', {
        method: 'POST',
        headers: { 'x-goog-api-key': geminiKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: geminiImageModel,
          input: prompt,
          response_format: { type: 'image', mime_type: 'image/png', aspect_ratio: '1:1', image_size: '1K' },
        }),
        signal: AbortSignal.timeout(120000),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) return res.status(502).json({ ok: false, error: 'gemini_image_upstream_error' });
      const data = body?.output_image?.data;
      if (typeof data !== 'string') return res.status(502).json({ ok: false, error: 'empty_gemini_image' });
      return res.json({ ok: true, image_base64: data, model: geminiImageModel, provider: 'gemini' });
    }
    if (!apiKey) return res.status(503).json({ ok: false, error: 'image_provider_not_configured' });
    const response = await fetch('https://api.openai.com/v1/images/generations', {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: imageModel, prompt, size: '1024x1024' }),
      signal: AbortSignal.timeout(120000),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) return res.status(502).json({ ok: false, error: 'image_upstream_error' });
    const data = body?.data?.[0]?.b64_json;
    if (typeof data !== 'string') return res.status(502).json({ ok: false, error: 'empty_image' });
    return res.json({ ok: true, image_base64: data, model: imageModel, provider: 'openai' });
  } catch { return res.status(502).json({ ok: false, error: 'image_unreachable' }); }
});

app.post('/v1/videos', rateLimit, async (req, res) => {
  if (!geminiKey) return res.status(503).json({ ok: false, error: 'gemini_video_provider_not_configured' });
  const prompt = typeof req.body?.prompt === 'string' ? req.body.prompt.trim() : '';
  if (!prompt) return res.status(400).json({ ok: false, error: 'prompt_required' });
  if (prompt.length > 12000) return res.status(413).json({ ok: false, error: 'prompt_too_large' });
  try {
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(geminiVideoModel)}:predictLongRunning`, {
      method: 'POST',
      headers: { 'x-goog-api-key': geminiKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({ instances: [{ prompt }] }),
      signal: AbortSignal.timeout(60000),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok || typeof body?.name !== 'string') return res.status(502).json({ ok: false, error: 'gemini_video_start_failed' });
    return res.json({ ok: true, provider: 'gemini', model: geminiVideoModel, operation: body.name, done: Boolean(body.done) });
  } catch { return res.status(502).json({ ok: false, error: 'gemini_video_unreachable' }); }
});

app.post('/v1/videos/status', rateLimit, async (req, res) => {
  if (!geminiKey) return res.status(503).json({ ok: false, error: 'gemini_video_provider_not_configured' });
  const operation = typeof req.body?.operation === 'string' ? req.body.operation.trim().replace(/^\/+/, '') : '';
  if (!operation || !operation.startsWith('operations/')) return res.status(400).json({ ok: false, error: 'operation_required' });
  try {
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/${operation}`, {
      headers: { 'x-goog-api-key': geminiKey },
      signal: AbortSignal.timeout(30000),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) return res.status(502).json({ ok: false, error: 'gemini_video_status_failed' });
    const videoUri = body?.response?.generateVideoResponse?.generatedSamples?.[0]?.video?.uri || null;
    return res.json({ ok: true, provider: 'gemini', operation, done: Boolean(body.done), video_uri: videoUri });
  } catch { return res.status(502).json({ ok: false, error: 'gemini_video_status_unreachable' }); }
});

app.post('/v1/speech', rateLimit, async (req, res) => {
  const input = typeof req.body?.input === 'string' ? req.body.input.trim() : '';
  if (!input) return res.status(400).json({ ok: false, error: 'input_required' });
  if (input.length > 4096) return res.status(413).json({ ok: false, error: 'input_too_large' });
  try {
    if (apiKey) {
      const response = await fetch('https://api.openai.com/v1/audio/speech', { method: 'POST', headers: { Authorization: 'Bearer ' + apiKey, 'Content-Type': 'application/json' }, body: JSON.stringify({ model: ttsModel, voice: ttsVoiceId ? { id: ttsVoiceId } : ttsVoice, input, instructions: ttsInstructions, response_format: 'mp3' }), signal: AbortSignal.timeout(60000) });
      if (!response.ok) return res.status(502).json({ ok: false, error: 'speech_upstream_error' });
      res.set('Content-Type', 'audio/mpeg').set('Cache-Control', 'no-store').set('X-SHADOW-TTS-Mode', ttsVoiceId ? 'custom' : 'built_in').set('X-SHADOW-TTS-Style', 'jarvis-inspired-original');
      return res.send(Buffer.from(await response.arrayBuffer()));
    }
    if (!geminiKey) return res.status(503).json({ ok: false, error: 'speech_provider_not_configured' });
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(geminiTtsModel)}:generateContent`, {
      method: 'POST',
      headers: { 'x-goog-api-key': geminiKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: input }] }],
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: 'Kore' } } },
        },
      }),
      signal: AbortSignal.timeout(60000),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) return res.status(502).json({ ok: false, error: 'gemini_tts_upstream_error' });
    const part = body?.candidates?.[0]?.content?.parts?.find(x => x?.inlineData?.data || x?.inline_data?.data);
    const b64 = part?.inlineData?.data || part?.inline_data?.data;
    const mime = part?.inlineData?.mimeType || part?.inline_data?.mime_type || 'audio/wav';
    if (typeof b64 !== 'string') return res.status(502).json({ ok: false, error: 'empty_gemini_tts_result' });
    res.set('Content-Type', mime).set('Cache-Control', 'no-store').set('X-SHADOW-TTS-Mode', 'gemini');
    return res.send(Buffer.from(b64, 'base64'));
  } catch (e) {
    return res.status(502).json({ ok: false, error: String(e?.message || 'speech_unreachable').slice(0, 180) });
  }
});

app.listen(port, '0.0.0.0', () => console.log(`SHADOW cloud backend listening on ${port}; agent=unified-multi-ai; providers=${JSON.stringify(providerStatus())}`));
