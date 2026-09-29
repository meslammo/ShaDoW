import { config } from "hatchable";
import { getShadowBrainStatus } from "../lib/shadow-brain-engine.js";

export const access="public";
export const methods=["GET"];

export default async function(_req,res){
  let llm7Key=false;
  let githubClientId=false;
  try{ llm7Key=Boolean(String(await config.get("LLM7_API_KEY")||"").trim()); }catch(_){}
  try{ githubClientId=Boolean(String(await config.get("SHADOW_GITHUB_CLIENT_ID")||"").trim()); }catch(_){}
  const brain=await getShadowBrainStatus();
  res.json({
    ok:true,
    release:"SHADOW 150-Core + Brain Engine",
    brain_version:"shadow-brain-v1.0",
    brain_engine:brain.brain_engine,
    brain_runtime:brain.runtime,
    brain_provider_contract:brain.provider_contract,
    external_ai_is_identity:brain.external_ai_is_identity,
    online_only:true,
    offline_ai_removed:true,
    agent_loop:{understand:true,search:true,evaluate:true,reuse:true,integrate:true,test:true,execute:true,verify:true},
    engines:{
      architecture:"provider-agnostic",
      mandatory_external_models:false,
      configured_runtime:"self-hosted when SHADOW_LOCAL_MODEL_URL exists; compatibility adapter otherwise",
      named_premium_provider_dependency:false
    },
    safeguards:{governance:true,confirmation_gate:true,self_repair:true,answer_verification:true,secrets_blocked:true},
    capabilities:{web:true,github:true,memory:true,images:true,streaming_agent:true,vision_route:true},
    configuration:{
      llm7_key_configured:llm7Key,
      github_oauth_client_configured:githubClientId,
      vision_key_required:true,
      platform_ai_provider_path:true
    },
    image_engine:{
      independent_renderer:true,
      visual_director:"shadow-native",
      prompt_planning:"shadow-native",
      artifact_qa:"deterministic",
      semantic_vision_platform_or_self_hosted:true,
      image_editing_platform_or_self_hosted:true,
      default_order:["imagine-draw","openai","pollinations"]
    },
    evolution:brain.evolution,
    generated_at:new Date().toISOString()
  });
}