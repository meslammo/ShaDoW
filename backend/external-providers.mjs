/**
 * SHADOW external AI provider adapters.
 *
 * These adapters use the providers' public APIs when credentials are supplied.
 * They do not bypass authentication, billing, rate limits, or provider policy.
 */

async function postJson(url, headers, body, timeout = 65000) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeout),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = payload?.error?.message || payload?.error?.code || `upstream_${response.status}`;
    const error = new Error(message);
    error.http = response.status;
    throw error;
  }
  return payload;
}

export const externalProviderConfig = {
  mistral: {
    keyEnv: 'MISTRAL_API_KEY',
    modelEnv: 'MISTRAL_MODEL',
    defaultModel: 'mistral-large-latest',
    capabilities: ['chat', 'reasoning', 'vision', 'tools'],
  },
  anthropic: {
    keyEnv: 'ANTHROPIC_API_KEY',
    modelEnv: 'ANTHROPIC_MODEL',
    defaultModel: 'claude-sonnet-4-5',
    capabilities: ['chat', 'reasoning', 'vision', 'tools'],
  },
  gemini: {
    keyEnv: 'GEMINI_API_KEY',
    modelEnv: 'GEMINI_MODEL',
    defaultModel: 'gemini-3.8-flash',
    capabilities: ['chat', 'reasoning', 'vision', 'audio', 'video', 'pdf', 'image-generation', 'video-generation', 'tools'],
  },
};



export function localProviderStatus(env = process.env) {
  const baseUrl = String(env.SHADOW_LOCAL_AI_BASE_URL || '').trim();
  return {
    configured: Boolean(baseUrl),
    base_url: baseUrl || null,
    model: String(env.SHADOW_LOCAL_AI_MODEL || 'local-model').trim(),
    capabilities: ['chat', 'reasoning', 'tools', 'vision-when-model-supports-it'],
  };
}

export async function localOpenAICompatAgent({ message, systemPrompt, toolDefinitions, runTool, model, baseUrl }) {
  if (!baseUrl) throw new Error('local_provider_not_configured');
  const url = baseUrl.replace(/\/+$/, '') + '/chat/completions';
  const messages = [
    { role: 'system', content: systemPrompt },
    { role: 'user', content: message },
  ];
  const tools = toolDefinitions.map(x => ({
    type: 'function',
    function: { name: x.name, description: x.description, parameters: x.parameters },
  }));

  for (let round = 0; round < 8; round += 1) {
    const out = await postJson(url, {}, {
      model,
      messages,
      tools,
      tool_choice: 'auto',
      temperature: 0.2,
    });
    const assistant = out?.choices?.[0]?.message;
    if (!assistant) throw new Error('local_provider_empty_response');
    const calls = Array.isArray(assistant.tool_calls) ? assistant.tool_calls : [];
    if (!calls.length) {
      return {
        provider: 'local',
        model,
        answer: String(assistant.content || '').trim(),
        responseId: out.id || null,
        usedWeb: false,
        pendingAction: null,
      };
    }
    messages.push(assistant);
    for (const call of calls) {
      let args = {};
      try { args = JSON.parse(call?.function?.arguments || '{}'); }
      catch { throw new Error('local_provider_invalid_tool_arguments'); }
      const result = await runTool(call.function?.name, args);
      if (result.kind === 'client_action') {
        return {
          provider: 'local',
          model,
          answer: 'هحتاج أنفذ الإجراء ده على الجهاز.',
          responseId: out.id || null,
          usedWeb: false,
          pendingAction: { ...result, toolCallId: call.id || '' },
        };
      }
      messages.push({ role: 'tool', tool_call_id: call.id, content: JSON.stringify(result.value) });
    }
  }
  throw new Error('local_provider_agent_loop_limit');
}

export function externalProviderStatus(env = process.env) {
  return Object.fromEntries(Object.entries(externalProviderConfig).map(([name, cfg]) => [
    name,
    {
      configured: Boolean(String(env[cfg.keyEnv] || '').trim()),
      model: String(env[cfg.modelEnv] || cfg.defaultModel).trim(),
      capabilities: cfg.capabilities,
    },
  ]));
}

export async function mistralAgent({ message, systemPrompt, toolDefinitions, runTool, model, apiKey }) {
  if (!apiKey) throw new Error('mistral_not_configured');
  const messages = [
    { role: 'system', content: systemPrompt },
    { role: 'user', content: message },
  ];
  const tools = toolDefinitions.map(x => ({
    type: 'function',
    function: { name: x.name, description: x.description, parameters: x.parameters },
  }));

  for (let round = 0; round < 8; round += 1) {
    const body = {
      model,
      messages,
      tools,
      tool_choice: 'auto',
      temperature: 0.2,
    };
    const out = await postJson('https://api.mistral.ai/v1/chat/completions', {
      Authorization: `Bearer ${apiKey}`,
    }, body);
    const assistant = out?.choices?.[0]?.message;
    if (!assistant) throw new Error('mistral_empty_response');
    const calls = Array.isArray(assistant.tool_calls) ? assistant.tool_calls : [];
    if (!calls.length) {
      return {
        provider: 'mistral',
        model,
        answer: String(assistant.content || '').trim(),
        responseId: out.id || null,
        usedWeb: false,
        pendingAction: null,
      };
    }
    messages.push(assistant);
    for (const call of calls) {
      let args = {};
      try { args = JSON.parse(call?.function?.arguments || '{}'); }
      catch { throw new Error('mistral_invalid_tool_arguments'); }
      const result = await runTool(call.function?.name, args);
      if (result.kind === 'client_action') {
        return {
          provider: 'mistral',
          model,
          answer: 'هحتاج أنفذ الإجراء ده على الجهاز.',
          responseId: out.id || null,
          usedWeb: false,
          pendingAction: { ...result, toolCallId: call.id || '' },
        };
      }
      messages.push({
        role: 'tool',
        tool_call_id: call.id,
        content: JSON.stringify(result.value),
      });
    }
  }
  throw new Error('mistral_agent_loop_limit');
}

export async function anthropicAgent({ message, systemPrompt, toolDefinitions, runTool, model, apiKey }) {
  if (!apiKey) throw new Error('anthropic_not_configured');
  let messages = [{ role: 'user', content: message }];
  const tools = toolDefinitions.map(x => ({
    name: x.name,
    description: x.description,
    input_schema: x.parameters,
  }));

  for (let round = 0; round < 8; round += 1) {
    const out = await postJson('https://api.anthropic.com/v1/messages', {
      'x-api-key': apiKey,
      'anthropic-version': '2023-06-01',
    }, {
      model,
      max_tokens: 8192,
      system: systemPrompt,
      messages,
      tools,
      tool_choice: { type: 'auto' },
    });

    const blocks = Array.isArray(out?.content) ? out.content : [];
    const answer = blocks.filter(x => x?.type === 'text').map(x => x.text).join('\n').trim();
    const calls = blocks.filter(x => x?.type === 'tool_use');
    if (!calls.length) {
      return {
        provider: 'anthropic',
        model,
        answer,
        responseId: out.id || null,
        usedWeb: false,
        pendingAction: null,
      };
    }

    messages.push({ role: 'assistant', content: blocks });
    const toolResults = [];
    for (const call of calls) {
      const result = await runTool(call.name, call.input || {});
      if (result.kind === 'client_action') {
        return {
          provider: 'anthropic',
          model,
          answer: answer || 'هحتاج أنفذ الإجراء ده على الجهاز.',
          responseId: out.id || null,
          usedWeb: false,
          pendingAction: { ...result, toolCallId: call.id || '' },
        };
      }
      toolResults.push({
        type: 'tool_result',
        tool_use_id: call.id,
        content: JSON.stringify(result.value),
      });
    }
    messages.push({ role: 'user', content: toolResults });
  }
  throw new Error('anthropic_agent_loop_limit');
}

export async function geminiAgent({ message, systemPrompt, toolDefinitions, runTool, model, apiKey }) {
  if (!apiKey) throw new Error('gemini_not_configured');
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
  let contents = [{ role: 'user', parts: [{ text: message }] }];
  const tools = [{
    functionDeclarations: toolDefinitions.map(x => ({
      name: x.name,
      description: x.description,
      parameters: x.parameters,
    })),
  }];

  for (let round = 0; round < 8; round += 1) {
    const out = await postJson(url, {
      'x-goog-api-key': apiKey,
    }, {
      systemInstruction: { parts: [{ text: systemPrompt }] },
      contents,
      tools,
    });

    const parts = out?.candidates?.[0]?.content?.parts || [];
    const answer = parts.filter(x => typeof x?.text === 'string').map(x => x.text).join('\n').trim();
    const calls = parts.filter(x => x?.functionCall);
    if (!calls.length) {
      return {
        provider: 'gemini',
        model,
        answer,
        responseId: out?.responseId || null,
        usedWeb: false,
        pendingAction: null,
      };
    }

    contents.push({ role: 'model', parts });
    const responses = [];
    for (const part of calls) {
      const call = part.functionCall || {};
      const result = await runTool(call.name, call.args || {});
      if (result.kind === 'client_action') {
        return {
          provider: 'gemini',
          model,
          answer: answer || 'هحتاج أنفذ الإجراء ده على الجهاز.',
          responseId: out?.responseId || null,
          usedWeb: false,
          pendingAction: { ...result, toolCallId: call.name || '' },
        };
      }
      responses.push({
        functionResponse: {
          name: call.name,
          response: result.value,
        },
      });
    }
    contents.push({ role: 'user', parts: responses });
  }
  throw new Error('gemini_agent_loop_limit');
}
