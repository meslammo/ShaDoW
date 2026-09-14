const GITHUB_OAUTH='https://github.com/login';
const CLIENT_ID=String(process.env.SHADOW_GITHUB_CLIENT_ID||'').trim();
const SCOPE=String(process.env.SHADOW_GITHUB_OAUTH_SCOPE||'repo').trim();

function configured(){return Boolean(CLIENT_ID);}
async function post(path,body){
  if(!configured()) throw new Error('github_oauth_not_configured');
  const r=await fetch(`${GITHUB_OAUTH}${path}`,{method:'POST',headers:{'Accept':'application/json','Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(body),signal:AbortSignal.timeout(20_000)});
  const data=await r.json().catch(()=>({}));
  if(!r.ok) throw new Error(`github_oauth_${r.status}`);
  return data;
}
export async function startDeviceAuthorization(){
  const data=await post('/device/code',{client_id:CLIENT_ID,scope:SCOPE});
  if(!data.device_code||!data.user_code||!data.verification_uri) throw new Error('github_oauth_invalid_device_response');
  return {device_code:data.device_code,user_code:data.user_code,verification_uri:data.verification_uri,verification_uri_complete:data.verification_uri_complete||null,expires_in:Number(data.expires_in||900),interval:Number(data.interval||5)};
}
export async function pollDeviceAuthorization(deviceCode){
  if(!deviceCode) throw new Error('device_code_required');
  const data=await post('/oauth/access_token',{client_id:CLIENT_ID,device_code:deviceCode,grant_type:'urn:ietf:params:oauth:grant-type:device_code'});
  if(data.access_token) return {status:'authorized',access_token:data.access_token,token_type:data.token_type||'bearer',scope:data.scope||SCOPE};
  const error=String(data.error||'authorization_pending');
  if(error==='authorization_pending') return {status:'pending'};
  if(error==='slow_down') return {status:'slow_down'};
  if(error==='expired_token') return {status:'expired'};
  if(error==='access_denied') return {status:'denied'};
  return {status:'error',error};
}
export function status(){return {configured,scope:SCOPE};}
