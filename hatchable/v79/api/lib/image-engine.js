import { config, ai } from "hatchable";
import { generateOpenAIImage } from "../../../../shared/openai-image-2-6.js";

async function secret(name) {
  try { return String(await config.get(name) || "").trim(); } catch { return ""; }
}

export function sizeForAspectRatio(value) {
  const ratio = String(value || "1:1");
  const sizes = {
    "1:1": "1024x1024",
    "16:9": "1536x864",
    "9:16": "864x1536",
    "4:3": "1360x1024",
    "3:4": "1024x1360",
  };
  return sizes[ratio] || sizes["1:1"];
}

export async function generateImage(prompt, options = {}) {
  const provider = String(options.provider || "auto").toLowerCase();
  const apiKey = await secret("OPENAI_API_KEY");
  const configuredModel = await secret("OPENAI_IMAGE_MODEL") || await secret("SHADOW_OPENAI_IMAGE_MODEL" );
  if (provider === "openai" && !apiKey) throw new Error("openai_image_not_configured");
  if (apiKey && ["auto", "openai"].includes(provider)) {
    const result = await generateOpenAIImage({
      apiKey,
      prompt,
      options: {
        model: options.model || configuredModel || "flare",
        size: options.size || "1024x1024",
        quality: options.quality || "auto",
        complexity: options.complexity || "high",
        outputFormat: options.outputFormat || "png",
        background: options.background || "auto",
        frameCount: options.frameCount,
        frameDurationMs: options.frameDurationMs,
        outputCompression: options.outputCompression,
        n: options.n || 1,
      },
    });
    return { ...result, renderer_role: "primary_renderer" };
  }
  if (!["auto", "google", "gemini"].includes(provider)) {
    throw new Error("requested_image_provider_not_configured");
  }
  const result = await ai.fetch({
    provider: "google",
    path: "/v1beta/models/gemini-3.1-flash-image:generateContent",
    body: {
      contents: [{ role: "user", parts: [{ text: String(prompt).slice(0, 12000) }] }],
      generationConfig: { responseModalities: ["IMAGE"] },
    },
    purpose: "image-generation",
    timeoutMs: 170000,
  });
  if (!result.ok) throw new Error("platform_image_generation_" + result.status);
  const payload = await result.json();
  const parts = payload?.candidates?.[0]?.content?.parts || [];
  const inline = parts.find(part => part?.inlineData?.data || part?.inline_data?.data);
  const data = inline?.inlineData?.data || inline?.inline_data?.data || "";
  const mime = inline?.inlineData?.mimeType || inline?.inline_data?.mime_type || "image/png";
  if (!data) throw new Error("platform_image_generation_empty_image");
  return {
    ok: true,
    image_base64: String(data).replace(/^data:[^,]+,/i, ""),
    image_url: null,
    mime_type: mime,
    provider: "google",
    model: "gemini-3.1-flash-image",
    renderer_role: "primary_renderer",
  };
}
