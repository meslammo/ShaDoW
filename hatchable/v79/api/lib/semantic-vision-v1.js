import { config, ai } from "hatchable";
import { referenceReceipt } from "./visual-evolution.js";

const MAX_BYTES=12000000;
const PLATFORM_VISION_MODELS=["gemini-flash","gpt","sonnet"];

async function secret(name){try{return String(await config.get(name)||"").trim()}catch{return ""}}
function clean(v,n=8000){return String(v??"").trim().slice(0,n)}

function decodeData(v){
  const raw=clean(v,24000000).replace(/^data:[^,]+,/i,"").trim();
  if(!raw) throw new Error("image_required");
  if(!/^[A-Za-z0-9+/=]+$/.test(raw)) throw new Error("invalid_image_base64");
  const bytes=Math.floor(raw.length*3/4)-(raw.endsWith("==")?2:raw.endsWith("=")?1:0);
  if(bytes<=0) throw new Error("invalid_image");
  if(bytes>MAX_BYTES) throw new Error("image_too_large");
  return raw;
}

function extract(text){
  const s=String(text||"").trim();
  try{return JSON.parse(s)}catch{}
  const fenced=s.match(/\`\`\`(?:json)?\s*([\s\S]*?)\`\`\`/i);
  if(fenced)try{return JSON.parse(fenced[1])}catch{}
  const a=s.indexOf("{"),b=s.lastIndexOf("}");
  if(a>=0&&b>a)try{return JSON.parse(s.slice(a,b+1))}catch{}
  return {scene:s,objects:[],people:[],text:[],regions:[],relations:[],attributes:[],observations:[s]};
}

function normalize(x,meta){
  const arr=v=>Array.isArray(v)?v.slice(0,100):[];
  return {
    schema:"shadow.semantic-vision.v1",
    provider:meta.provider,
    model:meta.model,
    scene:clean(x?.scene,4000),
    objects:arr(x?.objects),
    people:arr(x?.people),
    text:arr(x?.text),
    regions:arr(x?.regions),
    relations:arr(x?.relations),
    attributes:arr(x?.attributes),
    observations:arr(x?.observations),
    uncertainty:arr(x?.uncertainty),
    confidence:Number.isFinite(Number(x?.confidence))?Math.max(0,Math.min(1,Number(x.confidence))):null,
    prompt_compliance:x?.prompt_compliance||{satisfied:false,notes:["provider did not return compliance"]},
    comparison:x?.comparison||null
  };
}

const schema="Return ONLY JSON with scene, objects, people, text, regions, relations, attributes, observations, prompt_compliance, confidence, uncertainty, comparison. Coordinates are normalized 0..1. Use visible evidence only. Do not guess.";

function setupError(provider,model,error){
  const message=clean(error,500);
  return {
    provider,
    model,
    setup_required:true,
    semantic_verified:false,
    status:"provider_setup_required",
    error:message
  };
}

async function platformVision(images,prompt){
  const attempts=[];
  const imageParts=images.slice(0,4).map(im=>({
    type:"image_url",
    image_url:{url:"data:"+im.mime+";base64,"+im.data,detail:"high"}
  }));
  const content=[{type:"text",text:schema+" User task: "+clean(prompt)}].concat(imageParts);

  for(const model of PLATFORM_VISION_MODELS){
    try{
      const r=await ai.generateText({
        model,
        purpose:"semantic-vision",
        messages:[{role:"user",content}],
        maxTokens:2200
      });
      if(typeof r?.text==="string"&&r.text.trim()){
        return {provider:"hatchable-ai",model:r.model||model,data:extract(r.text),attempts};
      }
      attempts.push({provider:"hatchable-ai",model,error:"empty_response"});
    }catch(e){
      const msg=String(e?.message||e).slice(0,500);
      attempts.push({provider:"hatchable-ai",model,error:msg});
      if(/setup|required|API key|no .* key|412/i.test(msg) && !attempts.some(a=>a.setup_required)){
        attempts[attempts.length-1].setup_required=true;
      }
    }
  }

  return {fallbackNeeded:true,attempts};
}

async function llm7Vision(images,prompt){
  const url="https://api.llm7.io/v1/chat/completions";
  const key=await secret("LLM7_API_KEY");
  if(!key) return {fallbackNeeded:true,attempts:[{provider:"llm7",error:"not_configured"}]};

  const attempts=[];
  for(const model of ["glm-5.3-flash","gpt-5.5","claude-sonnet-4-6"]){
    const content=[{type:"text",text:schema+" User task: "+clean(prompt)}];
    for(const im of images){
      content.push({type:"image_url",image_url:{url:"data:"+im.mime+";base64,"+im.data,detail:"high"}});
    }
    try{
      const r=await fetch(url,{
        method:"POST",
        headers:{"content-type":"application/json","authorization":"Bearer "+key},
        body:JSON.stringify({model,messages:[{role:"user",content}],max_tokens:2200}),
        signal:AbortSignal.timeout(90000)
      });
      const raw=await r.text();let d={};try{d=JSON.parse(raw)}catch{}
      if(!r.ok){
        attempts.push({provider:"llm7",model,status:r.status,error:String(d?.error?.message||raw).slice(0,400)});
        continue;
      }
      const t=d?.choices?.[0]?.message?.content;
      if(typeof t==="string"&&t.trim()) return {provider:"llm7",model,data:extract(t),attempts};
      attempts.push({provider:"llm7",model,status:200,error:"empty_response"});
    }catch(e){
      attempts.push({provider:"llm7",model,error:String(e?.message||e).slice(0,400)});
    }
  }
  return {fallbackNeeded:true,attempts};
}

async function configuredVision(images,prompt){
  const url=await secret("SHADOW_LOCAL_VISION_URL");
  if(!url) return {fallbackNeeded:true,attempts:[{provider:"configured-vision-adapter",error:"not_configured"}]};
  const key=await secret("SHADOW_LOCAL_VISION_API_KEY");
  const headers={"content-type":"application/json","user-agent":"SHADOW-Semantic-Vision/2.0"};
  if(key)headers.authorization="Bearer "+key;
  try{
    const r=await fetch(url,{
      method:"POST",headers,
      body:JSON.stringify({
        mode:images.length>1?"compare":"analyze",
        prompt,
        image_base64:images[0].data,
        mime_type:images[0].mime,
        response_format:"json",
        images
      }),
      signal:AbortSignal.timeout(120000)
    });
    const raw=await r.text();let d={};try{d=JSON.parse(raw)}catch{}
    if(!r.ok) return {fallbackNeeded:true,attempts:[{provider:"configured-vision-adapter",status:r.status,error:String(d?.error||raw).slice(0,400)}]};
    return {provider:"configured-vision-adapter",model:d?.model||"configured-vision",data:d?.analysis||d?.result||d,attempts:[]};
  }catch(e){
    return {fallbackNeeded:true,attempts:[{provider:"configured-vision-adapter",error:String(e?.message||e).slice(0,400)}]};
  }
}

export async function analyzeSemanticVision({message="",imageBase64="",imageMimeType="image/jpeg",images=[]}={}){
  const source=Array.isArray(images)&&images.length?images:[{data:imageBase64,mime:imageMimeType}];
  const normalized=source.slice(0,4).map(x=>({
    data:decodeData(x?.data||x?.image_base64),
    mime:clean(x?.mime||x?.mime_type||x?.content_type||"image/jpeg",80)
  }));
  const receipts=await Promise.all(normalized.map(x=>referenceReceipt({base64:x.data,mimeType:x.mime})));
  if(receipts.some(x=>!x?.available))throw new Error("invalid_image");

  const prompt=clean(message||"حلل الصورة بدقة وأجب عن السؤال اعتمادًا على البكسلات المرئية فقط.");
  const providerAttempts=[];

  let out=await platformVision(normalized,prompt);
  providerAttempts.push(...(out.attempts||[]));
  if(out?.fallbackNeeded){
    out=await llm7Vision(normalized,prompt);
    providerAttempts.push(...(out.attempts||[]));
  }
  if(out?.fallbackNeeded){
    out=await configuredVision(normalized,prompt);
    providerAttempts.push(...(out.attempts||[]));
  }

  if(out?.fallbackNeeded){
    const setupFromAttempts=providerAttempts.find(a=>a?.setup_required)||providerAttempts.find(a=>/setup|required|API key|no .* key|412/i.test(String(a?.error||"")));
    if(setupFromAttempts){
      return {
        active:false,
        semantic_verified:false,
        provider:null,
        model:null,
        setup_required:true,
        status:"provider_setup_required",
        attempts:providerAttempts,
        receipts,
        result:null,
        error:setupError("vision",null,setupFromAttempts.error)
      };
    }
    return {
      active:false,
      semantic_verified:false,
      provider:null,
      model:null,
      status:"provider_unavailable",
      attempts:providerAttempts,
      receipts,
      result:null
    };
  }

  return {
    active:true,
    semantic_verified:true,
    provider:out.provider,
    model:out.model,
    attempts:providerAttempts,
    receipts,
    result:normalize(out.data,{provider:out.provider,model:out.model})
  };
}