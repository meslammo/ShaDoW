import test,{before,after} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {mkdtemp,rm,mkdir,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

let upstream,app,tempDir,stopServer;
const appPort=18787;
const upstreamBodies=[];
let memoryWasInjected=false;

function readJson(req){return new Promise((resolve,reject)=>{let data='';req.setEncoding('utf8');req.on('data',c=>data+=c);req.on('end',()=>{try{resolve(JSON.parse(data||'{}'));}catch(e){reject(e);}});req.on('error',reject);});}
async function listenEphemeral(server){await new Promise((resolve,reject)=>server.listen(0,'127.0.0.1',e=>e?reject(e):resolve()));return server.address().port;}

before(async()=>{
  tempDir=await mkdtemp(join(tmpdir(),'shadow-e2e-'));
  await mkdir(join(tempDir,'memory'),{recursive:true});
  await writeFile(join(tempDir,'memory','memory.json'),JSON.stringify([{fact:'E2E memory fact 42',reason:'seeded for online E2E',saved_at:new Date().toISOString()}]),'utf8');
  upstream=http.createServer(async(req,res)=>{
    if(req.url!=='/v1/responses'||req.method!=='POST'){res.writeHead(404);return res.end();}
    const body=await readJson(req); upstreamBodies.push(body);
    const input=body.input;
    if(typeof input==='string'&&input.includes('E2E memory fact')){
      memoryWasInjected=String(body.instructions||'').includes('E2E memory fact 42');
      res.writeHead(200,{'content-type':'application/json'});
      return res.end(JSON.stringify({id:'fixture-memory-1',output_text:'E2E memory fact 42 recalled'}));
    }
    if (body.stream === true) {
      res.writeHead(200,{'content-type':'text/event-stream; charset=utf-8','cache-control':'no-cache'});
      for (const event of [
        {type:'response.output_text.delta',delta:'E2E streamed response'},
        {type:'response.completed',response:{id:'fixture-stream-1'}}
      ]) res.write('data: '+JSON.stringify(event)+'\n\n');
      return res.end();
    }
    res.writeHead(200,{'content-type':'application/json'});
    res.end(JSON.stringify({id:'fixture-chat-1',output_text:'E2E chat response'}));
  });
  const upstreamPort=await listenEphemeral(upstream);
  process.env.PORT=String(appPort);
  process.env.OPENAI_API_KEY='e2e-test-key';
  process.env.OPENAI_MODEL='e2e-fixture-model';
  process.env.SHADOW_OPENAI_RESPONSES_URL='http://127.0.0.1:'+upstreamPort+'/v1/responses';
  process.env.SHADOW_MEMORY_DIR=join(tempDir,'memory');
  process.env.SHADOW_WORKSPACE_DIR=join(tempDir,'workspace');
  process.env.XAI_API_KEY='';
  process.env.DEEPSEEK_API_KEY='';
  process.env.DATABASE_URL='';
  process.env.SHADOW_NO_LISTEN='1';
  const runtime=await import('./server.mjs?e2e='+Date.now());
  app=runtime.startServer();
  stopServer=runtime.stopServer;
  await new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>reject(new Error('backend_server_listen_timeout')),10000);
    app.once('listening',()=>{clearTimeout(timer);resolve();});
    app.once('error',reject);
  });
});

after(async()=>{
  if(stopServer)await stopServer();
  if(upstream)await new Promise(r=>upstream.close(r));
  process.env.SHADOW_NO_LISTEN='';
  if(tempDir)await rm(tempDir,{recursive:true,force:true});
});

test('health exposes online provider and streaming',async()=>{
  const body=await(await fetch('http://127.0.0.1:'+appPort+'/health')).json();
  assert.equal(body.ok,true); assert.equal(body.capabilities.streaming_chat,true); assert.equal(body.providers.openai.configured,true);
});
test('online chat saves and recalls governed memory',async()=>{
  const recall=await(await fetch('http://127.0.0.1:'+appPort+'/v1/chat',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({message:'E2E memory fact'})})).json();
  assert.equal(recall.ok,true); assert.equal(recall.answer,'E2E memory fact 42 recalled'); assert.equal(memoryWasInjected,true); assert.ok(upstreamBodies.length>=1);
});
test('streaming chat emits delta/done/eof',async()=>{
  const response=await fetch('http://127.0.0.1:'+appPort+'/v1/chat/stream',{method:'POST',headers:{'content-type':'application/json','accept':'text/event-stream'},body:JSON.stringify({message:'stream E2E'})});
  assert.equal(response.status,200); const text=await response.text();
  assert.match(text,/type\":\"delta\"/); assert.match(text,/E2E streamed response/); assert.match(text,/type\":\"eof\"/);
});
test('development write gateway requires approval and configured credentials',async()=>{
  const blocked=await fetch('http://127.0.0.1:'+appPort+'/v1/development/pull-request',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({approved:false,branch:'e2e',title:'blocked'})});
  assert.equal(blocked.status,403);
  const unconfigured=await fetch('http://127.0.0.1:'+appPort+'/v1/development/pull-request',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({approved:true,branch:'e2e',title:'unconfigured'})});
  assert.equal(unconfigured.status,503);
});
