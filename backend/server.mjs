import { createHash } from 'node:crypto';
import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import { applyFiles, createPullRequest, runDevelopmentPipeline, status as developmentStatus } from './development-agent.mjs';
import { startDeviceAuthorization, pollDeviceAuthorization, status as githubOAuthStatus } from './github-oauth.mjs';
import { voiceprintStatus, verifyVoiceprint } from './voiceprint.mjs';
import { providerStatus, memoryStatus } from './ai-router.mjs';
import { runUnifiedPipeline, streamUnifiedPipeline, platformContract } from './shadow-pipeline.mjs';

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
  providers: Object.fromEntries(Object.entries(providerStatus()).filter(([name]) => name !== 'local')),
  memory: await memoryStatus(),
  capabilities: {
    web_search: true,
    web_fetch: true,
    streaming_chat: true,
    streaming_provider: 'openai',
    github_read: true,
    github_write_gateway: githubOAuthStatus().configured,
    files: true,
    device_control: false,
    agent_loop: true,
    voiceprint_required: false,
    speech_to_text: Boolean(apiKey),
    vision: Boolean(apiKey),
    full_12_step_master: true,
    unified_150_core: true,
    phase_roadmap: 35,
    online_only_brain: true,
    unified_cloud_pipeline: true,
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
      external_device_control: false,
      offline_ai_removed: true,
