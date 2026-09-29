import { Buffer } from 'node:buffer';

const providerUrl = String(process.env.SHADOW_VOICEPRINT_PROVIDER_URL || '').trim();
const providerToken = String(process.env.SHADOW_VOICEPRINT_PROVIDER_TOKEN || '').trim();

export function voiceprintStatus() {
  return {
    configured: Boolean(providerUrl),
    provider: providerUrl ? 'raw-audio-http' : 'none',
    reason: providerUrl
      ? 'raw-audio provider configured; enrollment/verification are delegated to provider'
      : 'raw-audio speaker embedding provider is not configured',
  };
}

export async function verifyVoiceprint(audioBase64, contentType = 'audio/wav') {
  if (!providerUrl) throw new Error('voiceprint_provider_not_configured');
  if (typeof audioBase64 !== 'string' || !audioBase64.trim()) throw new Error('audio_required');
  if (audioBase64.length > 15_000_000) throw new Error('audio_too_large');
  const audio = Buffer.from(audioBase64, 'base64');
  if (!audio.length) throw new Error('audio_invalid');

  const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
  if (providerToken) headers.Authorization = `Bearer ${providerToken}`;
  const upstream = await fetch(providerUrl, {
    method: 'POST',
    headers,
    body: JSON.stringify({ audio_base64: audio.toString('base64'), content_type: String(contentType || 'audio/wav') }),
    signal: AbortSignal.timeout(15_000),
  });
  const body = await upstream.json().catch(() => ({}));
  if (!upstream.ok) throw new Error(`voiceprint_provider_http_${upstream.status}`);
  return {
    verified: body?.verified === true,
    enrolled: body?.enrolled === true,
    score: typeof body?.score === 'number' ? body.score : null,
    provider: 'raw-audio-http',
    reason: String(body?.reason || (body?.verified ? 'verified' : 'not_verified')).slice(0, 256),
  };
}
