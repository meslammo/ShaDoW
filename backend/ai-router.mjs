import fs from 'node:fs/promises';
import path from 'node:path';
import pg from 'pg';

const { Pool } = pg;
const OPENAI_URL='https://api.openai.com/v1/responses';
const XAI_URL='https://api.x.ai/v1/responses';
const DEEPSEEK_URL='https://api.deepseek.com/chat/completions';
const cfg={
  openaiKey:(process.env.OPENAI_API_KEY||'').trim(),openaiModel:(process.env.OPENAI_MODEL||'gpt-5.6-luna').trim(),
  xaiKey:(process.env.XAI_API_KEY||'').trim(),xaiModel:(process.env.XAI_MODEL||'grok-4.6').trim(),
  deepseekKey:(process.env.DEEPSEEK_API_KEY||'').trim(),deepseekModel:(process.env.DEEPSEEK_MODEL||'deepseek-chat').trim(),
  memoryDir:(process.env.SHADOW_MEMORY_DIR||'/data/shadow-memory').trim(),
  databaseUrl:(process.env.DATABASE_URL||'').trim()
};
const pool=cfg.databaseUrl?new Pool({connectionString:cfg.databaseUrl,ssl:cfg.databaseUrl.includes('railway')?{rejectUnauthorized:false}:undefined,max:5,idleTimeoutMillis:10000,connectionTimeoutMillis:5000}):null;
let dbReady=false;
const systemPrompt=String(process.env.SHADOW_SYSTEM_PROMPT||[
'You are SHADOW, a personal unified AI assistant for one owner.',
'Use natural Egyptian Arabic when the user uses Arabic unless formal Arabic is requested.',
'Operate as an agent: understand -> plan -> choose tools -> execute -> observe -> verify -> continue.',
'Never claim an action happened unless a tool or device result confirms it.',
'For current information, news, prices, websites, images, videos, and fresh facts, use web search.',
'Use GitHub tools for public repository information and memory tools for useful non-secret durable facts.',
'Never store or reveal passwords, API keys, access tokens, private keys, or authentication secrets.',
'For risky or irreversible device actions, request explicit confirmation before execution.'
].join(' '));

async function ensureDb(){
  if(!pool)return false;
  if(dbReady)return true;
  try{
    await pool.query(`CREATE TABLE IF NOT EXISTS shadow_memory (
      id BIGSERIAL PRIMARY KEY,
      fact TEXT NOT NULL,
      reason TEXT NOT NULL DEFAULT '',
      saved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      owner TEXT NOT NULL DEFAULT 'master'
    )`);
    await pool.query('CREATE INDEX IF NOT EXISTS shadow_memory_saved_at_idx ON shadow_memory(saved_at DESC)');
    dbReady=true;return true;
  }catch{return false;}
}
async function ensureMemory(){await fs.mkdir(cfg.memoryDir,{recursive:true});const f=path.join(cfg.memoryDir,'memory.json');try{await fs.access(f);}catch{await fs.writeFile(f,'[]','utf8');}return f;}
async function loadFileMemory(){const f=await ensureMemory();try{const d=JSON.parse(await fs.readFile(f,'utf8'));return Array.isArray(d)?d:[];}catch{return[];}}
async function saveFileMemory(items){const f=await ensureMemory();await fs.writeFile(f,JSON.stringify(items.slice(-500),null,2),'utf8');}
async function loadMemory(){
  if(await ensureDb()){
    try{const r=await pool.query('SELECT fact, reason, saved_at FROM shadow_memory ORDER BY saved_at DESC LIMIT 500');return r.rows.map(x=>({fact:x.fact,reason:x.reason,saved_at:x.saved_at}));}catch{dbReady=false;}
  }
  return loadFileMemory();
}
async function saveMemory(fact,reason){
  if(await ensureDb()){
    try{await pool.query('INSERT INTO shadow_memory(fact,reason) VALUES($1,$2)',[fact,reason]);return 'postgres';}catch{dbReady=false;}
  }
  const all=await loadFileMemory();all.push({fact,reason,saved_at:new Date().toISOString()});await saveFileMemory(all);return 'file-fallback';
}

const fnTools=[
 {type:'function',name:'calculator',description:'Calculate a basic arithmetic expression.',parameters:{type:'object',properties:{expression:{type:'string'}},required:['expression'],additionalProperties:false}},
 {type:'function',name:'github_read',description:'Read public GitHub repository metadata or a file. Use owner/name and optional path.',parameters:{type:'object',properties:{repo:{type:'string'},path:{type:'string'}},required:['repo'],additionalProperties:false}},
 {type:'function',name:'memory_search',description:'Search SHADOW long-term memory for relevant non-secret facts.',parameters:{type:'object',properties:{query:{type:'string'}},required:['query'],additionalProperties:false}},
 {type:'function',name:'memory_save',description:'Save one useful non-secret durable fact or preference. Never save credentials or authentication secrets.',parameters:{type:'object',properties:{fact:{type:'string'},reason:{type:'string'}},required:['fact'],additionalProperties:false}},
 {type:'function',name:'android_action',description:'Request a deterministic action that the Android client can execute and verify.',parameters:{type:'object',properties:{action:{type:'string'},argument:{type:'string'},reason:{type:'string'},requires_confirmation:{type:'boolean'}},required:['action'],additionalProperties:false}}
];
function calc(expr){const x=String(expr||'').trim();if(!/^[0-9+\-*/().%\s]+$/.test(x)||x.length>200)throw new Error('invalid_expression');const v=Function('"use strict";return('+x+')')();if(typeof v!=='number'||!Number.isFinite(v))throw new Error('invalid_result');return String(v);}
async function toolExec(name,args){
 if(name==='calculator')return {kind:'result',value:calc(args.expression)};
 if(name==='memory_search'){const q=String(args.query||'').toLowerCase(),all=await loadMemory();return {kind:'result',value:all.filter(x=>String(x.fact||'').toLowerCase().includes(q)||String(x.reason||'').toLowerCase().includes(q)).slice(-20)};}
 if(name==='memory_save'){const fact=String(args.fact||'').trim();if(!fact||/(password|passphrase|api[_ -]?key|access[_ -]?token|secret|private key|كلمة السر|باسورد|توكن|مفتاح)/i.test(fact))return {kind:'result',value:{saved:false,reason:'secret_or_credential_blocked'}};const source=await saveMemory(fact,String(args.reason||''));return {kind:'result',value:{saved:true,storage:source}};}
 if(name==='github_read'){const repo=String(args.repo||'').trim();if(!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repo))throw new Error('invalid_repo');const p=String(args.path||'').replace(/^\/+/,''),url='https://api.github.com/repos/'+repo+(p?'/contents/'+p:'');const r=await fetch(url,{headers:{Accept:'application/vnd.github+json','User-Agent':'SHADOW-AI/1.0'},signal:AbortSignal.timeout(15000)});const body=await r.json().catch(()=>({}));if(!r.ok)throw new Error('github_'+r.status);if(Array.isArray(body))return {kind:'result',value:body.map(x=>({name:x.name,path:x.path,type:x.type,size:x.size,url:x.html_url}))};let out=body;if(body&&body.encoding==='base64'&&typeof body.content==='string')out={name:body.name,path:body.path,size:body.size,html_url:body.html_url,content:Buffer.from(body.content.replace(/\s/g,''),'base64').toString('utf8').slice(0,60000)};return {kind:'result',value:out};}
 if(name==='android_action'){const action=String(args.action||'').trim();if(!action)throw new Error('action_required');return {kind:'client_action',action,argument:String(args.argument||''),reason:String(args.reason||''),requires_confirmation:Boolean(args.requires_confirmation)};}
 throw new Error('unknown_tool');
}
function textOf(body){if(typeof body?.output_text==='string')return body.output_text.trim();const out=Array.isArray(body?.output)?body.output:[];return out.flatMap(x=>Array.isArray(x.content)?x.content:[]).filter(x=>x?.type==='output_text'&&typeof x.text==='string').map(x=>x.text).join('\n').trim();}
function callsOf(body){return(Array.isArray(body?.output)?body.output:[]).filter(x=>x?.type==='function_call'&&typeof x.name==='string');}
function webUsed(body){return(Array.isArray(body?.output)?body.output:[]).some(x=>x?.type==='web_search_call');}
async function callResponses(provider,payload){const x=provider==='xai',key=x?cfg.xaiKey:cfg.openaiKey,url=x?XAI_URL:OPENAI_URL,r=await fetch(url,{method:'POST',headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json'},body:JSON.stringify(payload),signal:AbortSignal.timeout(65000)}),b=await r.json().catch(()=>({}));if(!r.ok){const e=new Error(b?.error?.code||b?.error?.message||`upstream_${r.status}`);e.http=r.status;throw e;}return b;}
async function responsesAgent(provider,message,previousResponseId,device){let prev=previousResponseId||undefined,input=device?`${message}\n\n[DEVICE_PROFILE]\n${device}`:message,usedWeb=false;const model=provider==='xai'?cfg.xaiModel:cfg.openaiModel;for(let i=0;i<8;i++){const built=provider==='xai'?[{type:'web_search'},{type:'x_search'}]:[{type:'web_search_preview'}];const payload={model,instructions:systemPrompt,input,tools:[...built,...fnTools],store:true};if(prev)payload.previous_response_id=prev;const b=await callResponses(provider,payload);usedWeb ||= webUsed(b);const calls=callsOf(b);if(!calls.length)return{provider,model,answer:textOf(b),responseId:b.id||prev||null,usedWeb,pendingAction:null};const outputs=[];for(const c of calls){const args=typeof c.arguments==='string'?JSON.parse(c.arguments||'{}'):(c.arguments||{}),r=await toolExec(c.name,args);if(r.kind==='client_action')return{provider,model,answer:textOf(b)||'هحتاج تنفيذ الإجراء على الموبايل.',responseId:b.id||null,usedWeb,pendingAction:{...r,toolCallId:c.call_id||c.id||''}};outputs.push({type:'function_call_output',call_id:c.call_id||c.id,output:JSON.stringify(r.value)});}prev=b.id;input=outputs;}throw new Error('agent_loop_limit');}
async function deepseekAgent(message,device){const messages=[{role:'system',content:systemPrompt},{role:'user',content:device?`${message}\n\n[DEVICE_PROFILE]\n${device}`:message}],tools=fnTools.map(x=>({type:'function',function:{name:x.name,description:x.description,parameters:x.parameters}}));for(let i=0;i<8;i++){const r=await fetch(DEEPSEEK_URL,{method:'POST',headers:{Authorization:`Bearer ${cfg.deepseekKey}`,'Content-Type':'application/json'},body:JSON.stringify({model:cfg.deepseekModel,messages,tools,tool_choice:'auto',temperature:0.2}),signal:AbortSignal.timeout(65000)}),b=await r.json().catch(()=>({}));if(!r.ok){const e=new Error(b?.error?.message||`upstream_${r.status}`);e.http=r.status;throw e;}const m=b?.choices?.[0]?.message;if(!m)throw new Error('empty_response');if(!Array.isArray(m.tool_calls)||!m.tool_calls.length)return{provider:'deepseek',model:cfg.deepseekModel,answer:String(m.content||'').trim(),responseId:null,usedWeb:false,pendingAction:null};messages.push(m);for(const tc of m.tool_calls){const a=JSON.parse(tc.function?.arguments||'{}'),rr=await toolExec(tc.function?.name,a);if(rr.kind==='client_action')return{provider:'deepseek',model:cfg.deepseekModel,answer:'هحتاج أنفذ الإجراء ده على الجهاز.',responseId:null,usedWeb:false,pendingAction:{...rr,toolCallId:tc.id}};messages.push({role:'tool',tool_call_id:tc.id,content:JSON.stringify(rr.value)});}}throw new Error('agent_loop_limit');}
export async function runAgent({message,previousResponseId='',device='',preferredProvider='auto'}){const order=preferredProvider==='openai'?['openai']:preferredProvider==='xai'?['xai']:preferredProvider==='deepseek'?['deepseek']:['openai','xai','deepseek'],attempts=[];for(const p of order){const key=p==='openai'?cfg.openaiKey:p==='xai'?cfg.xaiKey:cfg.deepseekKey;if(!key){attempts.push({provider:p,reason:'not_configured'});continue;}try{const out=p==='deepseek'?await deepseekAgent(message,device):await responsesAgent(p,message,previousResponseId,device);if(!out.answer)throw new Error('empty_ai_response');return{...out,attempts};}catch(e){attempts.push({provider:p,reason:String(e?.message||e),http:e?.http||null});}}const e=new Error('no_ai_provider_available');e.attempts=attempts;throw e;}
export function providerStatus(){return{openai:{configured:Boolean(cfg.openaiKey),model:cfg.openaiModel},xai:{configured:Boolean(cfg.xaiKey),model:cfg.xaiModel},deepseek:{configured:Boolean(cfg.deepseekKey),model:cfg.deepseekModel},routing:'openai -> xAI/Grok -> DeepSeek',memory:{database_configured:Boolean(cfg.databaseUrl),database_ready:dbReady,fallback_file:cfg.memoryDir}}}
