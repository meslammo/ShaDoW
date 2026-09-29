import { db, config } from "hatchable";
import { generateImage, sizeForAspectRatio } from "./image-engine.js";
import { buildVisualPlan, referenceReceipt, qaGeneratedImage, editImage } from "./visual-evolution.js";
import { analyzeSemanticVision } from "./semantic-vision-v1.js";
import { continuousImprove } from "./capability-evolution.js";

const IMAGE_ENGINE_VERSION="shadow-image-engine-v3.0";
const MAX_PROMPT=12000;
const MAX_REPAIR=2;

function clean(v,max=MAX_PROMPT){ return String(v??"").trim().slice(0,max); }
function id(prefix){ return prefix+"-"+Date.now().toString(36)+"-"+Math.random().toString(36).slice(2,10); }
async function secret(name){ try{return String(await config.get(name)||"").trim();}catch{return "";} }

function requestedOperation(message,operation="generate"){
  const x=clean(message,2000).toLowerCase();
  if(operation && operation!=="auto") return operation;
  if(/(امسح|احذف|remove|erase|background replacement|استبدل الخلفية|inpaint|inpainting)/i.test(x)) return "edit";
  if(/(كبر|وسّع|وسع|outpaint|extend canvas)/i.test(x)) return "outpaint";
  if(/(غير الستايل|restyle|style transfer|حوّل الستايل)/i.test(x)) return "restyle";
  if(/(recolor|إعادة تلوين|اعادة تلوين|بدّل اللون|بدل اللون|غيّر اللون|غير اللون|غيّر لون|غير لون)/i.test(x)) return "recolor";
  return "generate";
}

function classifyVisualIntent(message){
  const x=clean(message,5000).toLowerCase();
  return {
    subject_preservation:/(نفس|same|preserve|حافظ|زي الصورة|the same)/i.test(x),
    character_consistency:/(شخصية|character|face|وجه|portrait)/i.test(x),
    object_consistency:/(سيارة|car|product|منتج|logo|شعار|object)/i.test(x),
    text_rendering:/(اكتب|نص|text|title|logo|شعار|كلمة)/i.test(x),
    photorealism:/(واقعي|واقعية|realistic|photoreal|photo)/i.test(x)
  };
}

function decomposeScene(message,plan,operation,reference){
  const pieces=String(plan?.brief?.subject||message).split(/[,،;؛]/).map(x=>x.trim()).filter(Boolean).slice(0,12);
  return {
    operation,
    subject:pieces[0]||clean(message,500),
    elements:pieces,
    preserve_reference:Boolean(reference?.available),
    preserve_constraints:reference?.available?[
      "preserve primary subject identity when requested",
      "preserve requested proportions and key markings",
      "do not invent unrelated subjects"
    ]:[],
    stages:[
      "understand",
      "plan",
      reference?.available?"reference-condition":"no-reference",
      "render",
      "vision-qa",
      "repair-if-needed",
      "release"
    ]
  };
}

async function callLocalVision({imageBase64,mimeType,prompt,mode="analyze"}){
  const endpoint=await secret("SHADOW_LOCAL_VISION_URL");
  if(!endpoint){
    return {configured:false,executed:false,semantic_verified:false,status:"not_configured",reason:"Self-hosted vision endpoint is not configured."};
  }
  const key=await secret("SHADOW_LOCAL_VISION_API_KEY");
  const headers={"Content-Type":"application/json","User-Agent":"SHADOW-Vision/3.0"};
  if(key) headers.Authorization="Bearer "+key;
  const r=await fetch(endpoint,{
    method:"POST",
    headers,
    body:JSON.stringify({
      mode,
      prompt:clean(prompt,5000),
      image_base64:String(imageBase64||"").replace(/^data:[^,]+,/i,""),
      mime_type:clean(mimeType,80)||"image/jpeg",
      response_format:"json"
    }),
    signal:AbortSignal.timeout(120000)
  });
  const raw=await r.text();
  let data={};
  try{data=JSON.parse(raw);}catch{}
  if(!r.ok) return {configured:true,executed:false,semantic_verified:false,status:"error",http_status:r.status,error:String(data?.error||raw||"vision_error").slice(0,500)};
  return {
    configured:true,
    executed:true,
    semantic_verified:Boolean(data?.semantic_verified ?? data?.verified ?? true),
    status:"completed",
    model:data?.model||"self-hosted-vision",
    provider:"self-hosted",
    analysis:data?.analysis||data?.result||data,
    score:Number.isFinite(Number(data?.score))?Number(data.score):null
  };
}

async function semanticVerify({image,prompt,referenceBase64,referenceMimeType}){
  const generated=String(image?.image_base64||"").replace(/^data:[^,]+,/i,"").trim();
  if(!generated) return {configured:false,executed:false,semantic_verified:false,status:"empty_generated_image"};
  const compare=Boolean(referenceBase64);
  const requestPrompt=compare
    ? "Image 1 is the newly generated/edited image. Image 2 is the user reference image. Compare them against the user's request. Preserve the requested identity, geometry and key attributes. Return only evidence-grounded semantic analysis and prompt compliance."
    : "Judge whether the generated image semantically satisfies the user's request. Use visible evidence only and return prompt compliance, observations, mismatches and confidence.";
  try{
    const vision=await analyzeSemanticVision({
      message:requestPrompt+" User request: "+clean(prompt,5000),
      imageBase64:generated,
      imageMimeType:image?.mime_type||"image/png",
      images:compare
        ? [
            {data:generated,mime:image?.mime_type||"image/png"},
            {data:String(referenceBase64).replace(/^data:[^,]+,/i,"").trim(),mime:referenceMimeType||"image/jpeg"}
          ]
        : [{data:generated,mime:image?.mime_type||"image/png"}]
    });
    const result=vision?.result||null;
    const confidence=Number(result?.confidence);
    const comparisonScore=Number(result?.comparison?.score);
    const score=Number.isFinite(comparisonScore)
      ? Math.max(0,Math.min(1,comparisonScore))
      : (Number.isFinite(confidence)?Math.max(0,Math.min(1,confidence)):null);
    const promptSatisfied=result?.prompt_compliance?.satisfied;
    return {
      configured:true,
      executed:Boolean(vision?.active),
      semantic_verified:Boolean(vision?.semantic_verified && promptSatisfied !== false),
      status:vision?.status||"completed",
      provider:vision?.provider||null,
      model:vision?.model||null,
      score,
      analysis:result,
      attempts:vision?.attempts||[],
      receipts:vision?.receipts||[]
    };
  }catch(error){
    return {
      configured:true,
      executed:false,
      semantic_verified:false,
      status:"semantic_vision_error",
      error:String(error?.message||error).slice(0,500)
    };
  }
}

async function localEdit({operation,prompt,referenceBase64,referenceMimeType,size,maskBase64="",controlContract=null,identity=null}){
  const endpoint=await secret("SHADOW_LOCAL_IMAGE_EDIT_URL");
  if(!endpoint){
    const online=await editImage({
      prompt,
      referenceBase64,
      referenceMimeType,
      size
    });
    return {...online,configured:true};
  }
  const key=await secret("SHADOW_LOCAL_IMAGE_EDIT_API_KEY");
  const headers={"Content-Type":"application/json","User-Agent":"SHADOW-Image-Engine/3.0"};
  if(key) headers.Authorization="Bearer "+key;
  const r=await fetch(endpoint,{
    method:"POST",
    headers,
    body:JSON.stringify({
      operation,
      prompt:clean(prompt,MAX_PROMPT),
      image_base64:String(referenceBase64||"").replace(/^data:[^,]+,/i,""),
      mask_base64:String(maskBase64||"").replace(/^data:[^,]+,/i,"")||null,
      mime_type:clean(referenceMimeType,80)||"image/jpeg",
      size:clean(size,30),
      controls:controlContract?.controls||{},
      identity:identity?{id:identity.id,kind:identity.kind,label:identity.label,attributes:identity.attributes}:null
    }),
    signal:AbortSignal.timeout(180000)
  });
  const raw=await r.text(); let data={}; try{data=JSON.parse(raw);}catch{}
  if(!r.ok) throw new Error("self_hosted_image_edit_"+r.status);
  if(!data?.image_base64) throw new Error("self_hosted_image_edit_empty");
  return {
    ok:true,
    image_base64:String(data.image_base64).replace(/^data:[^,]+,/i,""),
    image_url:data.image_url||null,
    mime_type:data.mime_type||"image/png",
    provider:"self-hosted",
    model:data.model||"local-image-edit",
    renderer_role:"edit_renderer"
  };
}

async function tryRender({renderPrompt,provider,size,controlContract=null,identity=null,maskBase64=""}){ 
  const localEndpoint=await secret("SHADOW_LOCAL_IMAGE_RENDER_URL");
  if(localEndpoint){
    const key=await secret("SHADOW_LOCAL_IMAGE_RENDER_API_KEY");
    const headers={"Content-Type":"application/json","User-Agent":"SHADOW-Image-Engine/3.0"};
    if(key) headers.Authorization="Bearer "+key;
    const r=await fetch(localEndpoint,{
      method:"POST",
      headers,
      body:JSON.stringify({
        prompt:renderPrompt,
        size,
        provider:"self-hosted",
        controls:controlContract?.controls||{},
        identity:identity?{id:identity.id,kind:identity.kind,label:identity.label,attributes:identity.attributes}:null,
        mask_base64:maskBase64||null
      }),
      signal:AbortSignal.timeout(180000)
    });
    const raw=await r.text(); let data={}; try{data=JSON.parse(raw);}catch{}
    if(!r.ok) throw new Error("self_hosted_renderer_"+r.status);
    if(!data?.image_base64) throw new Error("self_hosted_renderer_empty");
    return {
      ok:true,
      image_base64:String(data.image_base64).replace(/^data:[^,]+,/i,""),
      image_url:data.image_url||null,
      mime_type:data.mime_type||"image/png",
      provider:"self-hosted",
      model:data.model||"local-image-renderer",
      renderer_role:"primary_renderer"
    };
  }
  return await generateImage(renderPrompt,{provider,size});
}

async function saveVisualMemory({kind,title,source,imageSha,metadata,plan}){
  try{
    const row={id:id("vmem"),kind,title:clean(title,300),source:clean(source,100)||"runtime",image_sha256:clean(imageSha,100)||null,metadata:metadata||{},plan:plan||{}};
    await db.query(
      "INSERT INTO shadow_visual_memory(id,kind,title,source,image_sha256,metadata,plan) VALUES($1,$2,$3,$4,$5,$6::jsonb,$7::jsonb)",
      [row.id,row.kind,row.title,row.source,row.image_sha256,JSON.stringify(row.metadata),JSON.stringify(row.plan)]
    );
    return row.id;
  }catch(_){ return null; }
}

function memorySignal(qa,semantic){
  const scoreParts=[];
  if(qa) scoreParts.push(Boolean(qa.verified)?1:0);
  if(semantic?.executed) scoreParts.push(Number.isFinite(semantic.score)?semantic.score:(semantic.semantic_verified?1:0));
  return scoreParts.length?Number((scoreParts.reduce((a,b)=>a+b,0)/scoreParts.length).toFixed(3)):0;
}

export async function runAdvancedImageEngine({
  message,
  operation="auto",
  provider="auto",
  size="1024x1024",
  presetId="",
  referenceBase64="",
  referenceMimeType="image/jpeg",
  controlContract=null,
  identity=null,
  maskBase64=""
}={}){
  const prompt=clean(message);
  if(!prompt) throw new Error("prompt_required");
  const requestId=id("img");
  const started=Date.now();
  const op=requestedOperation(prompt,operation);
  const visualIntent=classifyVisualIntent(prompt);
  const plan=await buildVisualPlan({message:prompt,size,presetId});
  const reference=referenceBase64?await referenceReceipt({base64:referenceBase64,mimeType:referenceMimeType}):null;
  const scene=decomposeScene(prompt,plan,op,reference);

  const runPayload={request_id:requestId,version:IMAGE_ENGINE_VERSION,operation:op,status:"running",renderer:"",model:"",attempt:0,request:{prompt,provider,size,presetId,has_reference:Boolean(reference?.available)}};
  try{
    await db.query(
      "INSERT INTO shadow_image_runs(id,request_id,version,operation,status,request) VALUES($1,$2,$3,$4,'running',$5::jsonb)",
      [requestId,requestId,IMAGE_ENGINE_VERSION,op,JSON.stringify(runPayload.request)]
    );
  }catch(_){}

  let best=null;
  let repairHistory=[];
  let lastError=null;

  if(op!=="generate"){
    if(!reference?.available) throw new Error("reference_required_for_"+op);
    // localEdit() routes to the configured self-hosted editor first, then to
    // Hatchable's online provider gateway. No architecture-level self-hosted requirement.
  }

  for(let attempt=0;attempt<=MAX_REPAIR;attempt++){
    try{
      const renderSize=(size==="1024x1024"&&plan.aspect_ratio&&plan.aspect_ratio!=="1:1")
        ? sizeForAspectRatio(plan.aspect_ratio) : size;
      const repairHint=attempt
        ? "\nREPAIR PASS "+attempt+": Correct the prior visual defects. Preserve all requested identity/geometry constraints; remove mismatches."
        : "";
      const renderPrompt=plan.render_prompt+repairHint+"\n\nNEGATIVE PROMPT: "+plan.negative_prompt;
      const image=op==="generate"
        ? await tryRender({
            renderPrompt,
            provider,
            size:renderSize,
            controlContract,
            identity,
            maskBase64
          })
        : await localEdit({
            operation:op,
            prompt:renderPrompt,
            referenceBase64,
            referenceMimeType,
            size:renderSize,
            maskBase64,
            controlContract,
            identity
          });
      const artifactQa=await qaGeneratedImage({
        image,
        prompt,
        renderPrompt,
        requestedSize:renderSize
      });
      const semantic=await semanticVerify({image,prompt,referenceBase64,referenceMimeType});
      const semanticPending=!semantic.executed;
      const verified=Boolean(artifactQa.verified && semantic.executed && semantic.semantic_verified);
      const candidate={image,artifactQa,semantic,attempt,renderPrompt,renderSize,verified};
      best=candidate;

      if(verified){
        break;
      }
      // Do not waste renderer calls when semantic verification is unavailable
      // because the provider is not configured; preserve the artifact and surface
      // the exact pending state instead of claiming verification.
      if(semanticPending && artifactQa.verified) break;
      repairHistory.push({
        attempt,
        artifact_verified:Boolean(artifactQa.artifact_verified),
        semantic_executed:Boolean(semantic.executed),
        semantic_verified:Boolean(semantic.semantic_verified),
        semantic_score:semantic.score,
        errors:artifactQa.errors||[],
        mismatches:Array.isArray(semantic?.analysis?.mismatches)?semantic.analysis.mismatches.slice(0,12):[]
      });
    }catch(error){
      lastError=String(error?.message||error);
      repairHistory.push({attempt,error:lastError});
    }
  }

  if(!best){
    try{await db.query("UPDATE shadow_image_runs SET status='failed',updated_at=NOW(),result=$1::jsonb WHERE id=$2",[JSON.stringify({error:lastError||"render_failed"}),requestId]);}catch(_){}
    throw new Error(lastError||"image_engine_failed");
  }

  const memoryId=await saveVisualMemory({
    kind:best.semantic.executed&&best.semantic.semantic_verified?"verified-render":"render",
    title:plan.title,
    source:"shadow-image-engine-v3",
    imageSha:best.artifactQa?.sha256||"",
    metadata:{
      operation:op,
      visual_intent:visualIntent,
      artifact_qa:best.artifactQa,
      semantic_qa:best.semantic,
      repair_count:repairHistory.length,
      latency_ms:Date.now()-started
    },
    plan
  });

  const improvement=await continuousImprove({
    sourceType:"image-v3",
    request:prompt,
    result:{
      provider:best.image.provider,
      model:best.image.model,
      verification:{verified:best.verified},
      image_qa:best.artifactQa,
      attempts_total:best.attempt+1,
      tool_errors:lastError?[lastError]:[]
    },
    evidence:{image_qa:best.artifactQa,semantic_qa:best.semantic,visual_memory_id:memoryId}
  });

  const status=best.verified?"verified":(best.semantic.executed?"semantic_failed":"artifact_verified_semantic_pending");
  try{
    await db.query(
      "UPDATE shadow_image_runs SET status=$1,renderer=$2,model=$3,attempt=$4,result=$5::jsonb,updated_at=NOW() WHERE id=$6",
      [status,best.image.provider,best.image.model,best.attempt+1,JSON.stringify({
        artifact_qa:best.artifactQa,semantic_qa:best.semantic,repair_history:repairHistory,visual_memory_id:memoryId
      }),requestId]
    );
  }catch(_){}

  return {
    ok:true,
    engine:IMAGE_ENGINE_VERSION,
    request_id:requestId,
    operation:op,
    visual_intent:visualIntent,
    visual_plan:plan,
    reference:reference,
    scene_plan:scene,
    renderer:{
      provider:best.image.provider,
      model:best.image.model,
      role:best.image.renderer_role||"renderer",
      self_hosted:best.image.provider==="self-hosted"
    },
    image_result:best.image,
    image_qa:{
      ...best.artifactQa,
      semantic_verified:Boolean(best.semantic.semantic_verified),
      semantic_status:best.semantic.status,
      semantic_score:best.semantic.score,
      semantic_analysis:best.semantic.analysis||null
    },
    repair:{
      attempts:best.attempt+1,
      history:repairHistory,
      repaired:best.attempt>0
    },
    memory:{visual_memory_id:memoryId},
    evolution:improvement,
    verification:{
      verified:best.verified,
      artifact_verified:Boolean(best.artifactQa.artifact_verified),
      semantic_verified:Boolean(best.semantic.semantic_verified),
      method:best.semantic.executed
        ? (String(best.semantic.provider||"semantic-vision")+"+artifact-contract")
        : "artifact-contract-only",
      score:memorySignal(best.artifactQa,best.semantic)
    },
    architecture:{
      A_prompt_understanding:true,
      B_visual_planning:true,
      C_reference_understanding:true,
      D_renderer_routing:true,
      E_control_edit_contract:true,
      F_multi_step_rendering:true,
      G_semantic_vision:Boolean(best.semantic.executed && best.semantic.semantic_verified),
      H_compare_repair:repairHistory.length>0,
      I_visual_memory_and_skills:true,
      J_versioned_release:true,
      offline_ai:false,
      named_external_model_required:false
    }
  };
}

export async function getAdvancedImageEngineStatus(){
  const visionConfigured=Boolean(await secret("SHADOW_LOCAL_VISION_URL"));
  const rendererConfigured=Boolean(await secret("SHADOW_LOCAL_IMAGE_RENDER_URL"));
  const editorConfigured=Boolean(await secret("SHADOW_LOCAL_IMAGE_EDIT_URL"));
  return {
    version:IMAGE_ENGINE_VERSION,
    online_only:true,
    self_hosted:{
      vision_configured:visionConfigured,
      renderer_configured:rendererConfigured,
      editor_configured:editorConfigured
    },
    capabilities:{
      A_prompt_understanding:"active",
      B_visual_planning:"active",
      C_reference_understanding:"active_metadata_and_optional_semantics",
      D_renderer_routing:"active",
      E_control_edit_contract:"active",
      F_multi_step_rendering:"active",
      G_semantic_vision:visionConfigured?"self_hosted_active":"platform_ai_or_self_hosted",
      H_compare_repair:visionConfigured?"semantic_compare_repair_active":"platform_ai_or_self_hosted",
      I_visual_memory_and_skills:"active",
      J_versioned_release:"active"
    },
    semantic_qa:{
      mode:visionConfigured?"self-hosted-first":"platform-ai-first_with-self-hosted-fallback",
      independent_vision_model:true,
      platform_ai_path:true,
      provider_credentials_required_for_semantics:true,
      external_named_model_required:false
    }
  };
}