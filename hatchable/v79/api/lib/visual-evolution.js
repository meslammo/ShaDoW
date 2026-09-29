import { config, ai } from "hatchable";
// Hatchable-compatible deterministic fingerprinting; no Node crypto dependency.
import { getVisualPreset } from "./capability-evolution.js";

const MAX_PROMPT=8000;
const DEFAULT_SIZE="1024x1024";

function clean(v,max=8000){ return String(v ?? "").trim().slice(0,max); }
function sha256Buffer(bytes){ return fingerprint(Buffer.from(bytes)); }
function fingerprint(value){
  const text=String(value??"");
  let h1=2166136261,h2=16777619;
  for(let i=0;i<text.length;i++){
    const c=text.charCodeAt(i);
    h1^=c; h1=Math.imul(h1,16777619);
    h2^=c; h2=Math.imul(h2,1099511627);
  }
  return (h1>>>0).toString(16).padStart(8,"0")+(h2>>>0).toString(16).padStart(8,"0");
}
function ratio(w,h){
  if(!w || !h) return "1:1";
  const r=w/h;
  if(r>1.6) return "16:9";
  if(r>1.25) return "4:3";
  if(r<0.625) return "9:16";
  if(r<0.8) return "3:4";
  return "1:1";
}
function expectedDimensions(size){
  const m=String(size||DEFAULT_SIZE).match(/^(\\d+)x(\\d+)$/);
  return m?{width:Number(m[1]),height:Number(m[2])}:null;
}
function fourcc(b,start){
  if(!b || start+4>b.length) return "";
  return String.fromCharCode(b[start],b[start+1],b[start+2],b[start+3]);
}
function imageInfo(bytes){
  const b=Buffer.from(bytes);
  const png=[137,80,78,71,13,10,26,10];
  const isPng=b.length>=24 && png.every((v,i)=>b[i]===v);
  if(isPng) return {format:"png",mime_type:"image/png",width:b.readUInt32BE(16),height:b.readUInt32BE(20)};
  if(b.length>=12 && fourcc(b,0)==="RIFF" && fourcc(b,8)==="WEBP"){
    let width=null,height=null;
    if(fourcc(b,12)==="VP8X" && b.length>=30){
      width=1+b[24]+(b[25]<<8)+(b[26]<<16);
      height=1+b[27]+(b[28]<<8)+(b[29]<<16);
    }
    return {format:"webp",mime_type:"image/webp",width,height};
  }
  if(b.length>=3 && b[0]===255 && b[1]===216){
    let i=2;
    while(i+9<b.length){
      if(b[i]!==255){i++;continue;}
      const marker=b[i+1]; i+=2;
      if(marker===217 || marker===218) break;
      if(i+2>b.length) break;
      const len=b.readUInt16BE(i); if(len<2 || i+len>b.length) break;
      const sof=[192,193,194,195,197,198,199,201,202,203,205,206,207];
      if(sof.includes(marker)) return {format:"jpeg",mime_type:"image/jpeg",height:b.readUInt16BE(i+3),width:b.readUInt16BE(i+5)};
      i+=len;
    }
    return {format:"jpeg",mime_type:"image/jpeg",width:null,height:null};
  }
  return {format:"unknown",mime_type:"application/octet-stream",width:null,height:null};
}
function removeCommandPrefix(s){
  return clean(s.replace(/^(يا\\s+)?(اعمل|اعملي|ارسم|ارسملي|صمم|صمّم|ولّد|ولد|أنشئ|انشئ|generate|create|draw|design|make|render)\\b[:：]?\\s*/iu,""),MAX_PROMPT);
}
function detect(patterns,text,fallback){ const hit=patterns.find(x=>x.re.test(text)); return hit?hit.value:fallback; }
function detectAspect(text,size){
  const dims=expectedDimensions(size); if(dims) return ratio(dims.width,dims.height);
  return detect([
    {re:/(16:9|wide|landscape|سينمائي|عرضي)/i,value:"16:9"},
    {re:/(9:16|vertical|portrait|طولي|عمودي)/i,value:"9:16"},
    {re:/(4:3)/i,value:"4:3"},
    {re:/(3:4)/i,value:"3:4"}
  ],text,"1:1");
}
const STYLE_PATTERNS=[
  {re:/(photoreal|photo-real|واقعي|واقعية|حقيقي)/i,value:"photorealistic realism"},
  {re:/(cinematic|سينمائي)/i,value:"cinematic realism"},
  {re:/(anime|أنمي)/i,value:"anime-inspired illustration"},
  {re:/(3d|ثلاثي الأبعاد)/i,value:"high-end 3D render"},
  {re:/(vector|متجه|شعار|logo|emblem|أيقونة)/i,value:"clean vector-inspired graphic"},
  {re:/(oil paint|زيت|لوحة زيتية)/i,value:"oil-painting aesthetic"},
  {re:/(watercolor|مائي|ألوان مائية)/i,value:"watercolor illustration"}
];
const CAMERA_PATTERNS=[
  {re:/(85mm|portrait lens|بورتريه)/i,value:"85mm portrait lens, shallow depth of field"},
  {re:/(50mm|standard lens)/i,value:"50mm natural perspective"},
  {re:/(35mm|wide-angle|زاوية واسعة)/i,value:"35mm environmental perspective"},
  {re:/(close[- ]up|لقطة قريبة)/i,value:"close-up framing"},
  {re:/(aerial|drone|جوية|درون)/i,value:"aerial camera viewpoint"},
  {re:/(front[- ]facing|أمامي)/i,value:"front-facing composition"}
];
const LIGHTING_PATTERNS=[
  {re:/(neon|نيون)/i,value:"controlled neon accent lighting"},
  {re:/(sunset|غروب)/i,value:"warm sunset key light"},
  {re:/(night|ليلي|ليل)/i,value:"night lighting with controlled highlights"},
  {re:/(studio|استوديو)/i,value:"soft studio key with subtle rim light"},
  {re:/(storm|عاصفة|برق)/i,value:"dramatic storm lighting with directional flashes"},
  {re:/(soft light|إضاءة ناعمة)/i,value:"soft diffused lighting"}
];
const COMPOSITION_PATTERNS=[
  {re:/(centered|center|وسط)/i,value:"centered hero composition with clean negative space"},
  {re:/(rule of thirds|ثلث)/i,value:"rule-of-thirds composition"},
  {re:/(dynamic|ديناميكي|action|حركة)/i,value:"dynamic diagonal composition with clear subject separation"},
  {re:/(minimal|بسيط|بسيطة)/i,value:"minimal composition with strong silhouette"}
];
const MOOD_PATTERNS=[
  {re:/(dark|داكن|مظلم)/i,value:"dark controlled mood"},
  {re:/(luxury|فاخر|فخامة)/i,value:"premium luxury mood"},
  {re:/(dramatic|درامي|درامية)/i,value:"dramatic high-contrast mood"},
  {re:/(calm|هادئ|هادئة)/i,value:"calm polished mood"},
  {re:/(futuristic|مستقبلي|سايبربانك|cyberpunk)/i,value:"futuristic technological mood"}
];

export async function understandVisualPrompt(message,size=DEFAULT_SIZE){
  const source=clean(message,MAX_PROMPT);
  if(!source) throw new Error("visual_prompt_required");
  const subject=removeCommandPrefix(source);
  return {
    source,subject,aspect_ratio:detectAspect(source,size),
    style:detect(STYLE_PATTERNS,source,"high-detail visual realism"),
    camera:detect(CAMERA_PATTERNS,source,"natural perspective camera"),
    lighting:detect(LIGHTING_PATTERNS,source,"balanced cinematic lighting"),
    composition:detect(COMPOSITION_PATTERNS,source,"clear subject-first composition"),
    mood:detect(MOOD_PATTERNS,source,"polished neutral mood"),
    environment:"environment faithful to the user's requested scene",
    materials:"physically plausible materials and surfaces",
    constraints:[
      "preserve the user's requested subject and key visual attributes",
      "avoid unintended text, watermark, duplicate subjects and malformed details",
      "maintain coherent perspective, lighting and proportions"
    ]
  };
}

export async function buildVisualPlan({message,size=DEFAULT_SIZE,presetId=""}={}){
  const brief=await understandVisualPrompt(message,size);
  const preset=await getVisualPreset(presetId);
  const p=preset?.spec && typeof preset.spec==="object"?preset.spec:{};
  const merged={
    ...brief,
    composition:p.composition||brief.composition,
    camera:p.camera||brief.camera,
    lighting:p.lighting||brief.lighting,
    style:p.style||brief.style,
    constraints:Array.from(new Set([...(brief.constraints||[]),...(p.constraints?[p.constraints]:[])]))
  };
  const negative_prompt="watermark, unintended text, deformed anatomy, duplicate subjects, broken perspective, low resolution, blur, compression artifacts";
  const render_prompt=[
    "Subject: "+merged.subject,
    "Composition: "+merged.composition,
    "Camera: "+merged.camera,
    "Lighting: "+merged.lighting,
    "Environment: "+merged.environment,
    "Materials: "+merged.materials,
    "Style: "+merged.style,
    "Mood: "+merged.mood,
    "Aspect ratio: "+merged.aspect_ratio,
    "Quality constraints: "+merged.constraints.join("; ")
  ].join(". ");
  return {
    ok:true,title:preset?.name?"SHADOW "+preset.name:"SHADOW Visual Plan",
    version:"visual-plan-v2.0",designer:"shadow-native-visual-director",
    preset:preset?{id:preset.id,name:preset.name,version:preset.version}:null,
    brief:merged,negative_prompt,aspect_ratio:merged.aspect_ratio,
    render_prompt:clean(render_prompt,12000)
  };
}

export async function referenceReceipt({base64="",mimeType="image/jpeg",name=""}={}){
  const raw=String(base64||"").trim().replace(/^data:[^,]+,/i,"");
  if(!raw) return {available:false,reason:"reference_base64_required"};
  let bytes;
  try{bytes=Buffer.from(raw,"base64");}catch{return {available:false,reason:"reference_base64_invalid"};}
  if(!bytes.length) return {available:false,reason:"reference_empty"};
  const info=imageInfo(bytes);
  return {
    available:info.format!=="unknown",
    filename:clean(name,180)||null,sha256:sha256Buffer(bytes),bytes:bytes.length,
    mime_type:clean(mimeType,80)||info.mime_type,detected_format:info.format,
    width:info.width,height:info.height,aspect_ratio:ratio(info.width,info.height),
    semantic_status:"metadata_only_no_external_vision_dependency"
  };
}

export async function qaGeneratedImage({image,prompt,renderPrompt,requestedSize=DEFAULT_SIZE}={}){
  const raw=String(image?.image_base64||"").trim();
  if(!raw) return {verified:false,artifact_verified:false,semantic_verified:false,errors:["empty_image_payload"]};
  let bytes;
  try{bytes=Buffer.from(raw,"base64");}catch{return {verified:false,artifact_verified:false,semantic_verified:false,errors:["invalid_base64"]};}
  const info=imageInfo(bytes);
  const errors=[];
  if(bytes.length<1000) errors.push("image_too_small");
  if(info.format==="unknown") errors.push("unknown_image_format");
  const expected=expectedDimensions(requestedSize);
  const dimension_match=Boolean(!expected||!info.width||!info.height||(expected.width===info.width&&expected.height===info.height));
  if(!dimension_match) errors.push("dimension_mismatch");
  const sourceTokens=removeCommandPrefix(prompt).toLowerCase().split(/[^\\p{L}\\p{N}]+/u).filter(x=>x.length>=4).slice(0,8);
  const rp=String(renderPrompt||"").toLowerCase();
  const contract_match=sourceTokens.length===0||sourceTokens.some(t=>rp.includes(t));
  if(!contract_match) errors.push("prompt_render_contract_mismatch");
  const artifact_verified=errors.every(x=>x==="prompt_render_contract_mismatch");
  return {
    verified:artifact_verified&&contract_match,artifact_verified,semantic_verified:false,
    semantic_status:"not_executed_without_self_hosted_vision",
    format:info.format,mime_type:info.mime_type,bytes:bytes.length,
    width:info.width,height:info.height,aspect_ratio:ratio(info.width,info.height),
    dimension_match,contract_match,sha256:sha256Buffer(bytes),errors
  };
}

async function secret(name){ try{return String(await config.get(name)||"").trim();}catch{return "";} }

export async function editImage({prompt,referenceBase64,referenceMimeType="image/jpeg",size=DEFAULT_SIZE}={}){
  const cleanImage=String(referenceBase64||"").replace(/^data:[^,]+,/i,"").trim();
  if(!cleanImage) throw new Error("reference_image_required");

  const endpoint=await secret("SHADOW_LOCAL_IMAGE_EDIT_URL");
  if(endpoint){
    const apiKey=await secret("SHADOW_LOCAL_IMAGE_EDIT_API_KEY");
    const headers={"Content-Type":"application/json","User-Agent":"SHADOW-Image-Edit/2.1"};
    if(apiKey) headers.Authorization="Bearer "+apiKey;
    const localResponse=await fetch(endpoint,{method:"POST",headers,body:JSON.stringify({
      operation:"edit",prompt:clean(prompt,MAX_PROMPT),
      image_base64:cleanImage,
      mime_type:clean(referenceMimeType,80),size:clean(size,30)
    }),signal:AbortSignal.timeout(120000)});
    const payload=await localResponse.json().catch(()=>({}));
    if(!localResponse.ok) throw new Error("local_image_edit_"+localResponse.status);
    const imageBase64=String(payload?.image_base64||"").trim();
    if(!imageBase64) throw new Error("local_image_edit_empty_image");
    return {ok:true,status:"completed",operation:"edit",image_base64:imageBase64,image_url:payload?.image_url||null,provider:"self-hosted",model:payload?.model||"local-image-edit",mime_type:payload?.mime_type||"image/png"};
  }

  // Platform-mediated online image editing; provider credentials remain outside the isolate.
  const result=await ai.fetch({
    provider:"google",
    path:"/v1beta/models/gemini-3.1-flash-image:generateContent",
    body:{
      contents:[{
        role:"user",
        parts:[
          {text:clean(prompt,MAX_PROMPT)},
          {inlineData:{mimeType:clean(referenceMimeType,80)||"image/jpeg",data:cleanImage}}
        ]
      }],
      generationConfig:{responseModalities:["IMAGE"]}
    },
    purpose:"image-edit",
    timeoutMs:170000
  });
  if(!result.ok){
    const detail=typeof result.error==="string"?result.error:JSON.stringify(result.error||{});
    const err=new Error("platform_image_edit_"+result.status+":"+detail.slice(0,500));
    err.status=result.status;
    throw err;
  }
  const payload=await result.json();
  const parts=payload?.candidates?.[0]?.content?.parts||[];
  const inline=parts.find(part=>part?.inlineData?.data||part?.inline_data?.data);
  const data=inline?.inlineData?.data||inline?.inline_data?.data||"";
  const mime=inline?.inlineData?.mimeType||inline?.inline_data?.mime_type||"image/png";
  if(!data) throw new Error("platform_image_edit_empty_image");
  return {
    ok:true,status:"completed",operation:"edit",
    image_base64:String(data).replace(/^data:[^,]+,/i,""),
    image_url:null,provider:"google",model:"gemini-3.1-flash-image",mime_type:mime
  };
}

export async function imageEvolutionInfo(){
  const editEndpoint=await secret("SHADOW_LOCAL_IMAGE_EDIT_URL");
  return {
    version:"visual-evolution-v2.0",prompt_understanding:"shadow-native",visual_planning:"shadow-native",
    reference_understanding:"metadata-native",artifact_qa:"deterministic",semantic_qa:"platform-ai-or-self-hosted",
    editing:{
      contract_ready:true,
      self_hosted_renderer_configured:Boolean(editEndpoint),
      online_platform_ai_path:true,
      status:editEndpoint?"self_hosted_active":"online_provider_pending_credentials",
      external_named_model_required:false
    },
    visual_skills:"versioned-registry",visual_presets:"versioned-registry"
  };
}