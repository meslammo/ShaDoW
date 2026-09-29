import { runAdvancedImageEngine } from "../lib/image-engine-v3.js";
import { resolveVisualIdentity, buildImageControlContract, compareVisuals } from "../lib/shadow-runtime-v53.js";

export const access="public";
export const methods=["POST"];

export default async function(req,res){
  const body=req.body||{};
  const prompt=typeof body.prompt==="string"?body.prompt.trim():"";
  if(!prompt) return res.status(400).json({ok:false,error:"prompt_required"});
  if(prompt.length>10000) return res.status(413).json({ok:false,error:"prompt_too_large"});
  try{
    const controls=body.control_layers&&typeof body.control_layers==="object"?body.control_layers:(body.controls&&typeof body.controls==="object"?body.controls:{});
    const identity=body.identity_label?await resolveVisualIdentity({
      identityId:String(body.identity_id||""),
      kind:String(body.identity_kind||"object"),
      label:String(body.identity_label||""),
      attributes:body.identity_attributes&&typeof body.identity_attributes==="object"?body.identity_attributes:{}
    }):null;
    const controlContract=buildImageControlContract({
      controls,
      identity,
      maskBase64:String(body.mask_base64||"")
    });
    const directive=controlContract.prompt_directives.length
      ? "\n\nCONTROL LAYERS:\n"+controlContract.prompt_directives.join("\n")
      : "";
    const referenceBase64=typeof body.reference_base64==="string"?body.reference_base64.trim():"";
    const referenceMimeType=typeof body.mime_type==="string"?body.mime_type.trim():"image/jpeg";
    const r=await runAdvancedImageEngine({
      message:prompt+directive,
      operation:typeof req.body?.operation==="string"?req.body.operation.trim():"auto",
      provider:typeof req.body?.provider==="string"?req.body.provider.trim().toLowerCase():"auto",
      size:typeof req.body?.size==="string"?req.body.size.trim():"1024x1024",
      presetId:typeof req.body?.preset_id==="string"?req.body.preset_id.trim():"",
      referenceBase64,
      referenceMimeType,
      controlContract,
      identity,
      maskBase64:String(body.mask_base64||"")
    });
    return res.json({
      ok:Boolean(r.ok),
      engine:r.engine,
      request_id:r.request_id,
      operation:r.operation,
      visual_intent:r.visual_intent,
      visual_plan:r.visual_plan,
      reference:r.reference,
      scene_plan:r.scene_plan,
      renderer:r.renderer,
      image_result:r.image_result,
      image_qa:r.image_qa,
      repair:r.repair,
      memory:r.memory,
      evolution:r.evolution,
      verification:r.verification,
      architecture:r.architecture,
      image_control_contract:controlContract,
      visual_identity:identity,
      visual_compare:referenceBase64&&r.image_result?.image_base64
        ? await compareVisuals({
            requestId:r.request_id,
            prompt,
            referenceBase64,
            referenceMimeType:referenceMimeType,
            generatedImage:r.image_result
          })
        : null
    });
  }catch(e){
    return res.status(502).json({ok:false,error:"image_generation_failed",detail:String(e?.message||e).slice(0,400),engine:"shadow-image-engine-v3.0"});
  }
}