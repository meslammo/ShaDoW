import { db, config } from "hatchable";
import { referenceReceipt } from "./visual-evolution.js";
import { analyzeSemanticVision } from "./semantic-vision-v1.js";

export const COMPLETE_RUNTIME_VERSION="shadow-runtime-v53.0";
const PROVIDER_LIMIT=2;
const MAX_RETRIES=2;
const slots=new Map();
const health=new Map();

const VISUAL_CONTROLS=["pose","depth","edge","segmentation","mask","layout","perspective"];
const RISKY=/(delete|remove|wipe|format|purchase|buy|pay|transfer|call|message|sms|حذف|امسح|فورمات|شراء|ادفع|حوّل|اتصل|رسالة|مكالمة)/i;

function clean(v,max=12000){return String(v??"").trim().slice(0,max);}
function keyHash(v){
  let h=2166136261>>>0;
  for(const ch of String(v??"")){h^=ch.charCodeAt(0);h=Math.imul(h,16777619);}
  return ("00000000"+(h>>>0).toString(16)).slice(-8);
}
function vectorize(text,size=48){
  const vec=Array(size).fill(0);
  const words=clean(text,4000).toLowerCase().split(/[^\p{L}\p{N}_]+/u).filter(Boolean);
  for(const word of words){
    let h=parseInt(keyHash(word),16)||0;
    const i=h%size;
    vec[i]+=1;
    vec[(i*7+3)%size]+=0.5;
  }
  const n=Math.sqrt(vec.reduce((a,b)=>a+b*b,0))||1;
  return vec.map(x=>Number((x/n).toFixed(6)));
}
function cosine(a,b){
  if(!Array.isArray(a)||!Array.isArray(b)||!a.length||a.length!==b.length) return 0;
  let dot=0,na=0,nb=0;
  for(let i=0;i<a.length;i++){dot+=a[i]*b[i];na+=a[i]*a[i];nb+=b[i]*b[i];}
  return na&&nb?dot/(Math.sqrt(na)*Math.sqrt(nb)):0;
}
async function secret(name){try{return String(await config.get(name)||"").trim();}catch{return "";}}
function now(){return new Date().toISOString();}

export async function analyzeVisionInput({imageBase64="",mimeType="image/jpeg",prompt=""}={}){
  const image=clean(imageBase64,20_000_000);
  if(!image) return {configured:false,executed:false,semantic_verified:false,status:"image_missing"};
  const receipt=await referenceReceipt({base64:image,mimeType});
  if(!receipt?.available) return {configured:false,executed:false,semantic_verified:false,status:"invalid_image",receipt};
  const endpoint=await secret("SHADOW_LOCAL_VISION_URL");
  if(!endpoint){
    return {
      configured:false,
      executed:true,
      semantic_verified:false,
      status:"metadata_only",
      provider:"shadow-native",
      model:"metadata-analyzer",
      receipt,
      analysis:{
        format:receipt.detected_format,
        width:receipt.width,
        height:receipt.height,
        aspect_ratio:receipt.aspect_ratio,
        bytes:receipt.bytes,
        sha256:receipt.sha256
      }
    };
  }
  const key=await secret("SHADOW_LOCAL_VISION_API_KEY");
  const headers={"Content-Type":"application/json","User-Agent":"SHADOW-Vision/3.0"};
  if(key) headers.Authorization="Bearer "+key;
  try{
    const r=await fetch(endpoint,{
      method:"POST",headers,
      body:JSON.stringify({
        mode:"analyze",
        prompt:clean(prompt,6000)||"Analyze the image accurately.",
        image_base64:image.replace(/^data:[^,]+,/i,""),
        mime_type,
        response_format:"json"
      }),
      signal:AbortSignal.timeout(120000)
    });
    const raw=await r.text(); let data={}; try{data=JSON.parse(raw);}catch{}
    if(!r.ok) return {configured:true,executed:false,semantic_verified:false,status:"error",http_status:r.status,receipt,error:String(data?.error||raw||"vision_error").slice(0,500)};
    return {
      configured:true,executed:true,
      semantic_verified:Boolean(data?.semantic_verified ?? data?.verified ?? true),
      status:"completed",provider:"self-hosted",model:data?.model||"self-hosted-vision",
      score:Number.isFinite(Number(data?.score))?Number(data.score):null,
      receipt,analysis:data?.analysis||data?.result||data
    };
  }catch(e){
    return {configured:true,executed:false,semantic_verified:false,status:"unavailable",receipt,error:String(e?.message||e).slice(0,500)};
  }
}
function reqId(prefix="rt"){return prefix+"-"+Date.now().toString(36)+"-"+Math.random().toString(36).slice(2,10);}

function safeBase64Meta(value,limitBytes){
  const raw=clean(value,limitBytes>0?Math.floor(limitBytes*1.4):4_000_000).replace(/^data:[^,]+,/i,"").trim();
  if(!raw) return {present:false,valid:false,bytes:0};
  if(!/^[A-Za-z0-9+/=]+$/.test(raw)) return {present:true,valid:false,bytes:0};
  const padding=raw.endsWith("==")?2:raw.endsWith("=")?1:0;
  const bytes=Math.max(0,Math.floor(raw.length*3/4)-padding);
  return {present:true,valid:bytes>0,bytes,within_limit:bytes<=limitBytes};
}
export function normalizeMultimodalInput(input={}){
  const message=clean(input.message,12000);
  const imageBase64=String(input.imageBase64||input.image_base64||input.referenceBase64||input.reference_base64||"").trim();
  const audioBase64=String(input.audioBase64||input.audio_base64||"").trim();
  const imageMeta=safeBase64Meta(imageBase64,8_000_000);
  const audioMeta=safeBase64Meta(audioBase64,12_000_000);
  const rawFiles=Array.isArray(input.files)?input.files.slice(0,12):[];
  const files=rawFiles.map((f,i)=>{
    const name=clean(f?.name||f?.filename||("file-"+i),240);
    const mime=clean(f?.mime_type||f?.content_type||"",120);
    const data=String(f?.data_base64||f?.base64||f?.content_base64||"").trim();
    const meta=safeBase64Meta(data,8_000_000);
    return {
      index:i,name,mime_type:mime,
      extension:(name.match(/\\.([A-Za-z0-9]+)$/)||[])[1]?.toLowerCase()||"",
      content_present:Boolean(data),
      content_valid:meta.valid,
      bytes:meta.bytes,
      within_limit:meta.within_limit
    };
  });
  const messages=Array.isArray(input.messages)
    ? input.messages.slice(-12).map(m=>({role:clean(m?.role,40),content:clean(m?.content,6000)})).filter(m=>m.content)
    : [];
  const normalized={
    message,
    modalities:{
      text:Boolean(message),
      image:imageMeta.present,
      audio:audioMeta.present,
      files:files.length,
      structured:messages.length
    },
    media:{
      image:{present:imageMeta.present,valid:imageMeta.valid,bytes:imageMeta.bytes,within_limit:imageMeta.within_limit,mime_type:clean(input.imageMimeType||input.image_mime_type||input.referenceMimeType||input.reference_mime_type||"image/jpeg",80)||"image/jpeg"},
      audio:{present:audioMeta.present,valid:audioMeta.valid,bytes:audioMeta.bytes,within_limit:audioMeta.within_limit,mime_type:clean(input.audioMimeType||input.audio_mime_type,"audio/wav",80)||"audio/wav"}
    },
    files,
    messages,
    valid:(!imageMeta.present||imageMeta.valid&&imageMeta.within_limit)&&(!audioMeta.present||audioMeta.valid&&audioMeta.within_limit)&&files.every(f=>!f.content_present||f.content_valid&&f.within_limit),
    normalized_at:now()
  };
  return {normalized,imageBase64,audioBase64,rawFiles};
}
export function detectModalities(input={}){
  const normalized=normalizeMultimodalInput(input).normalized;
  return normalized.modalities;
}
export function classifyIntent(message="",modalities={}){
  const x=clean(message,6000).toLowerCase();
  const out=[];
  const generationRequest=/(ارسم|ارسملي|صمم|صمّم|اعمل\s+صورة|أنشئ\s+صورة|انشئ\s+صورة|ولد\s+صورة|ولّد\s+صورة|generate\s+(an\s+)?image|create\s+(an\s+)?image|draw\s+(an\s+)?image|render\s+(an\s+)?image|wallpaper|بوستر)/i.test(x);
  const visionRequest=/(حلل\s+الصورة|حلّل\s+الصورة|صف\s+الصورة|وصف\s+الصورة|بص\s+للصورة|بص\s+الصورة|analyze\s+(the\s+)?image|describe\s+(the\s+)?image|vision)/i.test(x);
  if(modalities.image||visionRequest) out.push("vision");
  if(generationRequest) out.push("image");
  if(/(كود|code|github|git|repo|apk|build|debug|compile|برمج|تطوير)/i.test(x)) out.push("code");
  if(/(ابحث|بحث|research|latest|latest|today|current|news|مصادر|دور)/i.test(x)) out.push("research");
  if(/(صوت|voice|speech|اسمعني|اتكلم|mic|audio)/i.test(x)||modalities.audio) out.push("speech");
  if(modalities.files) out.push("files");
  if(/(ذاكرة|memory|افتكر|اتذكر)/i.test(x)) out.push("memory");
  if(!out.length) out.push("conversation");
  return [...new Set(out)];
}
export function taskRoute(intents,localModel){
  if(intents.includes("image")) return localModel?"local-image+brain":"image-provider+brain";
  if(intents.includes("vision")) return localModel?"local-vision+brain":"vision-provider+brain";
  if(intents.includes("code")) return localModel?"local-reasoning+tools":"code-provider+tools";
  if(intents.includes("research")) return "research+web";
  if(intents.includes("speech")) return "speech+brain";
  return localModel?"local-chat":"fast-conversation";
}
function controlsFrom(input={}){
  const c=input.controlLayers||input.controls||{};
  const out={};
  for(const key of VISUAL_CONTROLS) if(c[key]!=null) out[key]=clean(typeof c[key]==="string"?c[key]:JSON.stringify(c[key]),1200);
  return out;
}
export async function resolveVisualIdentity({identityId="",kind="object",label="",attributes={}}={}){
  const id=clean(identityId,180);
  if(id){
    try{
      const r=await db.query("SELECT * FROM shadow_visual_identities WHERE id=$1 LIMIT 1",[id]);
      if(r.rows[0]) return r.rows[0];
    }catch(_){}
  }
  if(!label) return null;
  const fingerprint=keyHash(JSON.stringify({kind,label,attributes}));
  const identityId2=id||"vid-"+fingerprint;
  try{
    await db.query(
      "INSERT INTO shadow_visual_identities(id,kind,label,fingerprint,embedding,attributes) VALUES($1,$2,$3,$4,$5::jsonb,$6::jsonb) ON CONFLICT(id) DO UPDATE SET attributes=EXCLUDED.attributes,updated_at=NOW()",
      [identityId2,clean(kind,80),clean(label,300),fingerprint,JSON.stringify(vectorize(label)),JSON.stringify(attributes||{})]
    );
  }catch(_){}
  return {id:identityId2,kind,label,fingerprint,embedding:vectorize(label),attributes:attributes||{}};
}
export function buildImageControlContract({controls={},identity=null,maskBase64=""}={}){
  return {
    enabled:true,
    controls,
    identity:identity?{id:identity.id,kind:identity.kind,label:identity.label,attributes:identity.attributes}:null,
    mask_present:Boolean(maskBase64),
    accepted_layers:VISUAL_CONTROLS,
    prompt_directives:Object.entries(controls).map(([k,v])=>k.toUpperCase()+": "+v),
    policy:["Preserve identity when requested.","Apply edits only to requested regions.","Do not introduce unrelated subjects."]
  };
}
export async function compareVisuals({requestId="",prompt="",referenceBase64="",referenceMimeType="image/jpeg",generatedImage=null}={}){
  const generatedBase64=String(generatedImage?.image_base64||"").trim();
  const ref=referenceBase64?await referenceReceipt({base64:referenceBase64,mimeType:referenceMimeType}):null;
  const gen=generatedBase64?await referenceReceipt({base64:generatedBase64,mimeType:generatedImage?.mime_type||"image/png"}):null;
  if(!gen?.available) return {verified:false,method:"invalid-generated-artifact",score:0,matches:[],mismatches:["generated_artifact_invalid"]};
  if(ref?.available && ref.sha256 && gen.sha256 && ref.sha256===gen.sha256){
    return await persistComparison({
      requestId,prompt,ref,gen,score:1,
      matches:["exact_artifact_sha_match","dimensions_match","aspect_ratio_match"],
      mismatches:[],
      method:"artifact-exact-match",
      verified:true
    });
  }
  let score=ref?.available?0.5:0.7;
  const matches=[];
  const mismatches=[];
  if(ref?.available){
    if(ref.width===gen.width&&ref.height===gen.height){score+=0.15;matches.push("dimensions_match");}
    else mismatches.push("dimensions_differ");
    if(ref.aspect_ratio===gen.aspect_ratio){score+=0.1;matches.push("aspect_ratio_match");}
    else mismatches.push("aspect_ratio_differ");
  }
  const vision=await secret("SHADOW_LOCAL_VISION_URL");
  let method="artifact-compare";
  if(ref?.available){
    try{
      const semantic=await analyzeSemanticVision({
        message:"Compare Image 1 (reference) and Image 2 (generated) against the user's request. Return evidence-grounded matches, mismatches, comparison score, prompt compliance and confidence. User request: "+clean(prompt,5000),
        images:[
          {data:String(referenceBase64||"").replace(/^data:[^,]+,/i,"").trim(),mime:referenceMimeType||"image/jpeg"},
          {data:generatedBase64.replace(/^data:[^,]+,/i,"").trim(),mime:generatedImage?.mime_type||"image/png"}
        ]
      });
      const result=semantic?.result||null;
      const comparison=Number(result?.comparison?.score);
      const confidence=Number(result?.confidence);
      if(Number.isFinite(comparison)) score=Math.max(0,Math.min(1,comparison));
      else if(Number.isFinite(confidence)) score=Math.max(0,Math.min(1,confidence));
      if(semantic?.active){
        method="platform-semantic-compare";
        const compliance=result?.prompt_compliance?.satisfied;
        const verified=Boolean(
          semantic.semantic_verified &&
          compliance !== false &&
          (Number.isFinite(comparison) ? comparison>=0.8 : score>=0.8)
        );
        return await persistComparison({
          requestId,prompt,ref,gen,score,
          matches:Array.isArray(result?.comparison?.matches)?result.comparison.matches:(
            Array.isArray(result?.observations)?result.observations:matches
          ),
          mismatches:Array.isArray(result?.comparison?.mismatches)?result.comparison.mismatches:mismatches,
          method,verified
        });
      }
    }catch(_){}
  }
  if(vision){
    try{
      const key=await secret("SHADOW_LOCAL_VISION_API_KEY");
      const headers={"Content-Type":"application/json","User-Agent":"SHADOW-Visual-Compare/1.0"};
      if(key) headers.Authorization="Bearer "+key;
      const rr=await fetch(vision,{method:"POST",headers,body:JSON.stringify({
        mode:"compare",prompt:clean(prompt,5000),
        reference_base64:String(referenceBase64||"").replace(/^data:[^,]+,/i,""),
        generated_base64:generatedBase64.replace(/^data:[^,]+,/i,""),
        reference_mime_type:referenceMimeType,generated_mime_type:generatedImage?.mime_type||"image/png",
        response_format:"json"
      }),signal:AbortSignal.timeout(120000)});
      const raw=await rr.text(); let data={}; try{data=JSON.parse(raw);}catch{}
      if(rr.ok){
        method="self-hosted-semantic-compare";
        score=Number.isFinite(Number(data?.score))?Math.max(0,Math.min(1,Number(data.score))):score;
        return await persistComparison({requestId,prompt,ref,gen,score,matches:data?.matches||matches,mismatches:data?.mismatches||mismatches,method,verified:Boolean(data?.semantic_verified??data?.verified??(score>=0.8))});
      }
    }catch(_){}
  }
  return await persistComparison({requestId,prompt,ref,gen,score:Math.max(0,Math.min(1,score)),matches,mismatches,method,verified:score>=0.8});
}
async function persistComparison({requestId,prompt,ref,gen,score,matches,mismatches,method,verified}){
  const id=reqId("cmp");
  try{
    await db.query("INSERT INTO shadow_visual_comparisons(id,request_id,reference_sha,generated_sha,prompt,score,matches,mismatches,method) VALUES($1,$2,$3,$4,$5,$6,$7::jsonb,$8::jsonb,$9)",
      [id,requestId,ref?.sha256||null,gen?.sha256||null,clean(prompt,12000),score,JSON.stringify(matches||[]),JSON.stringify(mismatches||[]),method]);
  }catch(_){}
  return {id,verified,method,score,matches:machesOr(matches),mismatches:machesOr(mismatches)};
}
function machesOr(v){return Array.isArray(v)?v.slice(0,20):[];}

export async function upsertMemoryEmbedding({scope="general",content="",importance=0.5,metadata={}}={}){
  const text=clean(content,8000); if(!text) return null;
  const contentHash=keyHash(text);
  let vector=vectorize(text);
  const endpoint=await secret("SHADOW_LOCAL_EMBEDDING_URL");
  if(endpoint){
    try{
      const key=await secret("SHADOW_LOCAL_EMBEDDING_API_KEY");
      const headers={"Content-Type":"application/json","User-Agent":"SHADOW-Embeddings/1.0"};
      if(key) headers.Authorization="Bearer "+key;
      const r=await fetch(endpoint,{method:"POST",headers,body:JSON.stringify({input:text}),signal:AbortSignal.timeout(60000)});
      const d=await r.json().catch(()=>({}));
      if(r.ok&&Array.isArray(d?.embedding)) vector=d.embedding.map(Number).slice(0,1536);
    }catch(_){}
  }
  try{
    await db.query("INSERT INTO shadow_memory_embeddings(scope,content_hash,content,vector,metadata,importance) VALUES($1,$2,$3,$4::jsonb,$5::jsonb,$6) ON CONFLICT(scope,content_hash) DO UPDATE SET vector=EXCLUDED.vector,metadata=EXCLUDED.metadata,importance=EXCLUDED.importance",
      [clean(scope,120),contentHash,text,JSON.stringify(vector),JSON.stringify(metadata||{}),Math.max(0,Math.min(1,Number(importance)||0.5))]);
  }catch(_){}
  return {scope,content_hash:contentHash,dimensions:vector.length,importance:Number(importance)||0.5};
}
export async function deepMemoryRecall({query="",scope=""}={}){
  const q=clean(query,4000);
  const qv=vectorize(q);
  const out=[];
  try{
    const r=await db.query("SELECT scope,content,vector,metadata,importance,created_at FROM shadow_memory_embeddings WHERE ($1='' OR scope=$1) ORDER BY created_at DESC LIMIT 80",[clean(scope,120)]);
    for(const row of r.rows){
      let vec=[]; try{vec=Array.isArray(row.vector)?row.vector:JSON.parse(row.vector||"[]");}catch{}
      const semantic=cosine(qv,vec);
      const lex=String(row.content||"").toLowerCase().includes(q.toLowerCase())?0.35:0;
      out.push({...row,semantic_score:Number((semantic+lex).toFixed(4))});
    }
  }catch(_){}
  out.sort((a,b)=>b.semantic_score-a.semantic_score);
  return out.slice(0,12);
}
export async function recordConversationTurn({sessionId="default",role="user",content="",modalities={},metadata={}}={}){
  const text=clean(content,12000); if(!text) return null;
  const sid=clean(sessionId,160)||"default";
  try{
    await db.query("INSERT INTO shadow_conversation_turns(session_id,role,content,modalities,metadata) VALUES($1,$2,$3,$4::jsonb,$5::jsonb)",
      [sid,clean(role,30),text,JSON.stringify(modalities||{}),JSON.stringify(metadata||{})]);
  }catch(_){}
  await upsertMemoryEmbedding({scope:"conversation:"+sid,content:text,importance:0.35,metadata:{role}});
  return {session_id:sid,stored:true};
}
export async function conversationContext({sessionId="default",limit=12}={}){
  try{
    const r=await db.query("SELECT role,content,modalities,metadata,created_at FROM shadow_conversation_turns WHERE session_id=$1 ORDER BY created_at DESC LIMIT $2",[clean(sessionId,160),Math.max(1,Math.min(30,Number(limit)||12))]);
    return r.rows.reverse();
  }catch(_){return [];}
}
export function decomposeTask({message="",intents=[],modalities={},controlLayers={}}={}){
  const steps=[{id:"understand",type:"understand",status:"planned"},{id:"route",type:"model-route",status:"planned"},{id:"memory",type:"memory-fusion",status:"planned"}];
  if(intents.includes("vision")) steps.push({id:"perceive",type:"vision",status:"planned"});
  if(intents.includes("research")) steps.push({id:"research",type:"web-research",status:"planned"});
  if(intents.includes("code")) steps.push({id:"tool-plan",type:"tool-planner",status:"planned"});
  if(intents.includes("image")) steps.push(
    {id:"render",type:"image-render",status:"planned",controls:Object.keys(controlLayers)},
    {id:"inspect",type:"vision-qa",status:"planned"},
    {id:"compare",type:"visual-compare",status:"planned"},
    {id:"repair",type:"image-repair",status:"planned"}
  );
  if(intents.includes("files")||modalities.files) steps.push({id:"file-intelligence",type:"file-understanding",status:"planned"});
  if(intents.includes("speech")||modalities.audio) steps.push({id:"speech",type:"speech-pipeline",status:"planned"});
  steps.push({id:"verify",type:"independent-verifier",status:"planned"},{id:"deliver",type:"response",status:"planned"});
  return steps;
}
export function governanceDecision(message="",confirmed=false){
  const risky=RISKY.test(clean(message,5000));
  return {risk:risky?"high":"normal",requires_confirmation:risky&&!confirmed,principal:"mohamed",source_mutation_requires_human_approval:true};
}
export async function registerSandboxRun({taskId,input={},diff={},testReport={},status="planned"}={}){
  const id=reqId("sbx");
  try{
    await db.query("INSERT INTO shadow_sandbox_runs(id,task_id,status,input,diff,test_report) VALUES($1,$2,$3,$4::jsonb,$5::jsonb,$6::jsonb)",
      [id,clean(taskId,160),clean(status,40),JSON.stringify(input||{}),JSON.stringify(diff||{}),JSON.stringify(testReport||{})]);
  }catch(_){}
  return {id,task_id:taskId,status,production_mutation:false};
}

async function providerHealthOk(provider){
  const name=clean(provider,80)||"brain";
  const state=health.get(name)||{inflight:0,consecutive:0,openUntil:0};
  return Date.now()>=state.openUntil&&state.inflight<PROVIDER_LIMIT;
}
export async function withProviderResilience(fn,{provider="brain"}={}){
  const name=clean(provider,80)||"brain";
  const start=Date.now();
  for(let attempt=0;attempt<=MAX_RETRIES;attempt++){
    let state=health.get(name)||{inflight:0,consecutive:0,openUntil:0};
    if(!await providerHealthOk(name)){
      await new Promise(r=>setTimeout(r,Math.min(4000,250*Math.pow(2,attempt))));
      continue;
    }
    state.inflight++; health.set(name,state);
    try{
      const value=await fn(attempt);
      state.inflight=Math.max(0,state.inflight-1); state.consecutive=0;
      const latency=Date.now()-start;
      try{await db.query("INSERT INTO shadow_provider_health(provider,success_count,consecutive_failures,last_latency_ms) VALUES($1,1,0,$2) ON CONFLICT(provider) DO UPDATE SET success_count=shadow_provider_health.success_count+1,consecutive_failures=0,last_latency_ms=EXCLUDED.last_latency_ms,updated_at=NOW()",[name,latency]);}catch(_){}
      return value;
    }catch(error){
      state.inflight=Math.max(0,state.inflight-1); state.consecutive++;
      state.openUntil=state.consecutive>=3?Date.now()+5000:0; health.set(name,state);
      try{await db.query("INSERT INTO shadow_provider_health(provider,failure_count,consecutive_failures,open_until,last_latency_ms) VALUES($1,1,$2,$3,$4) ON CONFLICT(provider) DO UPDATE SET failure_count=shadow_provider_health.failure_count+1,consecutive_failures=EXCLUDED.consecutive_failures,open_until=EXCLUDED.open_until,last_latency_ms=EXCLUDED.last_latency_ms,updated_at=NOW()",[name,state.consecutive,state.openUntil?new Date(state.openUntil):null,Date.now()-start]);}catch(_){}
      if(attempt===MAX_RETRIES) throw error;
      await new Promise(r=>setTimeout(r,400*Math.pow(2,attempt)));
    }
  }
  throw new Error("provider_router_exhausted");
}

export async function fileIntelligence(files=[]){
  const items=Array.isArray(files)?files.slice(0,20):[];
  return items.map((f,i)=>{
    const name=clean(f?.name||f?.filename||("file-"+i),240);
    const type=clean(f?.mime_type||f?.content_type||"",120);
    const ext=(name.match(/\.([A-Za-z0-9]+)$/)||[])[1]?.toLowerCase()||"";
    let kind="binary";
    if(type.includes("pdf")||ext==="pdf") kind="pdf";
    else if(type.includes("word")||/docx?$/i.test(ext)) kind="docx";
    else if(type.includes("sheet")||/xlsx?|csv$/i.test(ext)) kind="spreadsheet";
    else if(type.startsWith("image/")||/png|jpe?g|webp|heic|gif$/i.test(ext)) kind="image";
    else if(type.startsWith("audio/")||/mp3|wav|m4a|ogg|flac$/i.test(ext)) kind="audio";
    else if(/py|js|ts|java|kt|go|rs|cpp|h|json|yaml|yml|xml|gradle|dart$/i.test(ext)) kind="code";
    else if(type.startsWith("text/")||/txt|md$/i.test(ext)) kind="text";
    return {name,type,extension:ext,kind,route:kind==="image"?"vision":kind==="audio"?"speech":kind==="code"?"code":kind==="pdf"||kind==="docx"||kind==="spreadsheet"?"document":"text"};
  });
}

export async function getCompleteRuntimeStatus(){
  const localModel=Boolean(await secret("SHADOW_LOCAL_MODEL_URL"));
  const localVision=Boolean(await secret("SHADOW_LOCAL_VISION_URL"));
  const localRenderer=Boolean(await secret("SHADOW_LOCAL_IMAGE_RENDER_URL"));
  const localEditor=Boolean(await secret("SHADOW_LOCAL_IMAGE_EDIT_URL"));
  const localEmbedding=Boolean(await secret("SHADOW_LOCAL_EMBEDDING_URL"));
  return {
    version:COMPLETE_RUNTIME_VERSION,
    online_only:true,
    offline_ai_removed:true,
    image_engine:{
      "1_real_self_hosted_vision":localVision?"active":"blocked_provider_setup",
      "2_real_self_hosted_renderer":localRenderer?"active":"optional_external_renderer_active",
      "3_real_image_editing":localEditor?"active":"online_platform_ai_pending_credentials",
      "4_control_layers":"active_contract_and_forwarding",
      "5_character_object_consistency":"active_identity_registry",
      "6_visual_comparison":localVision?"active_semantic_plus_artifact":"active_artifact_plus_optional_semantics",
      "7_generation_loop":"active_bounded_multi_pass"
    },
    brain_engine:{
      "8_model_router":"active",
      "9_self_hosted_brain_model":localModel?"active":"adapter_ready",
      "10_deep_memory":"active",
      "11_task_decomposition":"active",
      "12_tool_planner":"active_governed_agent",
      "13_independent_verification":localVision?"active_semantic_plus_structural":"active_structural",
      "14_self_repair":"active_bounded",
      "15_streaming":"active_sse_incremental",
      "16_multimodal_context":"active",
      "17_speech_pipeline":"active_adapters",
      "18_file_intelligence":"active_classifier_and_routing",
      "19_conversation_intelligence":"active_context_store",
      "20_user_context":"active_context_fusion",
      "21_provider_resilience":"active_retry_circuit_concurrency",
      "22_governance":"active_human_approval_boundary",
      "23_evolution_sandbox":"active_staging_manifest"
    },
    adapters:{
      local_model:localModel,local_vision:localVision,local_renderer:localRenderer,local_editor:localEditor,local_embedding:localEmbedding
    },
    limits:{provider_concurrency:PROVIDER_LIMIT,max_retries:MAX_RETRIES,max_image_repair_passes:2},
    governance:{principal:"mohamed",source_mutation_requires_human_approval:true,safe_registry_updates_auto_apply:true,offline_ai_removed:true},
    note:"Model-dependent capabilities become fully semantic when an authorized Hatchable AI provider credential or self-hosted adapter is configured; provider credentials remain outside the source isolate."
  };
}