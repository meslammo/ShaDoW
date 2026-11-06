import { runAdvancedImageEngine } from "../../lib/image-engine-v3.js";
import { buildImageControlContract } from "../../lib/shadow-runtime-v53.js";

export const access="public";
export const methods=["POST"];

export default async function handler(req,res){
  const body=req.body||{};
  const prompt=typeof body.prompt==="string"?body.prompt.trim():"";
  const referenceBase64=typeof body.reference_base64==="string"?body.reference_base64.trim():"";
  if(!prompt) return res.status(400).json({ok:false,error:"prompt_required"});
  if(!referenceBase64) return res.status(400).json({ok:false,error:"reference_base64_required"});
  if(prompt.length>12000) return res.status(413).json({ok:false,error:"prompt_too_large"});
  const maskBase64=typeof body.mask_base64==="string"?body.mask_base64.trim():"";
  const markupBase64=typeof body.markup_base64==="string"?body.markup_base64.trim():"";
  if(referenceBase64.length>68*1024*1024||maskBase64.length>16*1024*1024||markupBase64.length>16*1024*1024){
    return res.status(413).json({ok:false,error:"image_input_too_large"});
  }
  const controls=body.control_layers&&typeof body.control_layers==="object"?body.control_layers:{};
  const controlContract=buildImageControlContract({controls,identity:null,maskBase64});
  try{
    const result=await runAdvancedImageEngine({
      message:prompt,
      operation:"edit",
      provider:typeof body.provider==="string"?body.provider:"auto",
      size:typeof body.size==="string"?body.size:"1024x1024",
      presetId:typeof body.preset_id==="string"?body.preset_id:"",
      referenceBase64,
      referenceMimeType:typeof body.mime_type==="string"?body.mime_type:"image/jpeg",
      controlContract,
      maskBase64,
      markupBase64,
      markupMimeType:typeof body.markup_mime_type==="string"?body.markup_mime_type:"image/png",
      quality:typeof body.quality==="string"?body.quality:"auto",
      complexity:typeof body.complexity==="string"?body.complexity:"high",
      outputFormat:typeof body.output_format==="string"?body.output_format:"png",
      background:typeof body.background==="string"?body.background:"auto",
      frameCount:body.frame_count??9,
      frameDurationMs:body.frame_duration_ms??200
    });
    return res.status(200).json({
      ok:Boolean(result.ok),
      engine:result.engine,
      request_id:result.request_id,
      operation:result.operation,
      renderer:result.renderer,
      image_result:result.image_result,
      image_qa:result.image_qa,
      repair:result.repair,
      verification:result.verification
    });
  }catch(error){
    return res.status(502).json({
      ok:false,
      error:"image_edit_failed",
      detail:String(error?.message||error).slice(0,400),
      engine:"shadow-image-engine-v3.1"
    });
  }
}
