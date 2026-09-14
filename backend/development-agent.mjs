const GITHUB_API = 'https://api.github.com';
const REPO = String(process.env.SHADOW_GITHUB_REPO || 'meslammo/ShaDoW').trim();
const TOKEN = String(process.env.SHADOW_GITHUB_TOKEN || '').trim();
const ALLOWED_PREFIXES = ['app/','backend/','shadow/','tests/','.github/workflows/'];
const BLOCKED_PARTS = ['.env','secrets','credentials','keystore','.pem','.key'];

function configured(token=TOKEN){ return Boolean(String(token||'').trim()); }
function allowedPath(path){
  const p=String(path||'').replace(/^\/+/, '');
  if(!ALLOWED_PREFIXES.some(x=>p.startsWith(x))) return false;
  return !BLOCKED_PARTS.some(x=>p.toLowerCase().includes(x));
}
function headers(token=TOKEN){return {'Authorization':`Bearer ${String(token||'').trim()}`,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','Content-Type':'application/json'};}
async function gh(path, options={}, token=TOKEN){
  if(!configured(token)) throw new Error('github_write_not_configured');
  const r=await fetch(`${GITHUB_API}${path}`,{...options,headers:{...headers(token),...(options.headers||{})},signal:AbortSignal.timeout(30_000)});
  const body=await r.json().catch(()=>({}));
  if(!r.ok) throw new Error(`github_${r.status}:${body?.message||'request_failed'}`);
  return body;
}

export async function applyFiles({branch='shadow-agent-work',message='MOD-50: Development Agent change',files=[],token=''}){
  const authToken=String(token||TOKEN).trim();
  if(!configured(authToken)) throw new Error('github_write_not_configured');
  if(!Array.isArray(files)||!files.length) throw new Error('files_required');
  if(files.length>20) throw new Error('too_many_files');
  for(const f of files){
    if(!allowedPath(f?.path)) throw new Error(`path_not_allowed:${f?.path||''}`);
    if(typeof f?.content!=='string') throw new Error(`content_required:${f?.path||''}`);
    if(f.content.length>500_000) throw new Error(`file_too_large:${f.path}`);
  }
  const result=[];
  for(const f of files){
    const path=encodeURIComponent(f.path).replace(/%2F/g,'/');
    let current=null;
    try{current=await gh(`/repos/${REPO}/contents/${path}?ref=${encodeURIComponent(branch)}`,{},authToken);}catch(e){if(!String(e.message).startsWith('github_404:')) throw e;}
    const payload={message,content:Buffer.from(f.content,'utf8').toString('base64'),branch};
    if(current?.sha) payload.sha=current.sha;
    const saved=await gh(`/repos/${REPO}/contents/${path}`,{method:'PUT',body:JSON.stringify(payload)},authToken);
    result.push({path:f.path,commit_sha:saved?.commit?.sha||null,created:!current?.sha});
  }
  return {repo:REPO,branch,files:result};
}

export function status(){return {configured:configured(),server_token_configured:configured(),repo:REPO,allowed_prefixes:ALLOWED_PREFIXES};}
