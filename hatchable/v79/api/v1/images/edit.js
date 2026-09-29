import { buildVisualPlan, referenceReceipt, editImage, qaGeneratedImage } from "../../lib/visual-evolution.js";
export const access="public";
export const methods=["POST"];
export default async function handler(req,res){
  const body=req.body||{};
  const prompt=typeof body.prompt==="string"?body.prompt.trim():"";
  const referenceBase64=typeof body.reference_base64==="string"?body.reference_base64.trim():"";
  const referenceMimeType=typeof body.mime_type==="string"?body.mime_type.trim():"image/jpeg";
  const size=typeof body.size==="string"?body.size.trim():"1024x1024";
  const presetId=typeof body.preset_id==="string"?body.preset_id.trim():"";
  if(!prompt) return res.status(400).json({ok:false,error:"prompt_required"});
  if(!referenceBase64) return res.status(400).json({ok:false,error:"reference_base64_required"});
  try{
    const reference=await referenceReceipt({base64:referenceBase64,mimeType:referenceMimeType,name:body.filename||""});
    if(!reference.available) return res.status(400).json({ok:false,error:"invalid_reference_image",reference});
    const plan=await buildVisualPlan({message:prompt,size,presetId});
    const result=await editImage({prompt:plan.render_prompt,referenceBase64,referenceMimeType,size});
    if(!result.ok) return res.status(409).json({ok:false,...result,reference,visual_plan:plan});
    const qa=await qaGeneratedImage({image:result,prompt:prompt,renderPrompt:plan.render_prompt,requestedSize:size});
    return res.status(200).json({ok:true,operation:"edit",provider:result.provider,model:result.model,reference,visual_plan:plan,image_result:{image_base64:result.image_base64,image_url:result.image_url||null,mime_type:result.mime_type||null,provider:result.provider,model:result.model},image_qa:qa});
  }catch(e){
    return res.status(502).json({ok:false,error:"image_edit_failed",detail:String(e?.message||e).slice(0,400)});
  }
}