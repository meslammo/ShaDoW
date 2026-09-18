import test,{before,after} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

let upstream,app,tempDir;
const appPort=18787;
const upstreamBodies=[];

function readJson(req){return new Promise((resolve,reject)=>{let data='';req.setEncoding('utf8');req.on('data',c=>data+=c);req.on('end',()=>{try{resolve(JSON.parse(data||'{}'));}catch(e){reject(e);}});req.on('error',reject);});}
async function listenEphemeral(server){await new Promise((resolve,reject)=>server.listen(0,'127.0.0.1',e=>e?reject(e):resolve()));return server.address().port;}

before(async()=>{
  tempDir=await mkdtemp(join(tmpdir(),'shadow-e2e-'));
  upstream=http.createServer(async(req,res)=>{
    if(req.url!=='/v1/responses'||req.method!=='POST'){res.writeHead(404);return res.end();}
    const body=await readJson(req); upstreamBodies.push(body);
    const input=body.input;
    if(typeof input==='string'&&input.includes('remember E2E fact')){
      res.writeHead(200,{'content-type':'application/json'});
      return res.end(JSON.stringify({id:'fixture-memory-1',output:[{type:'function_call',name:'memory_save',call_id:'memory-call-1',arguments:JSON.stringify({fact:'E2E memory fact 42',reason:'automated end-to-end validation'})}]}));
    }
    if(Array.isArray(input)){
      res.writeHead(200,{'content-type':'application/json'});
      return res.end(JSON.stringify({id:'fixture-memory-2',output_text:'Memory save completed'}));
    }
    if(typeof input==='string'&&input.includes('recall E2E fact')){
      assert.equal(String(body.instructions||'').includes('E2E memory fact 42'),true,'memory must reach the online model request');
      res.writeHead(200,{'content-type':'application/json'});
      return res.end(JSON.stringify({id:'fixture-memory-3',output_text:'E2E memory fact 42 recalled'}));
    }
    if (body.stream === true) {
      res.writeHead(200,{'content-type':'text/event-stream; charset=utf-8','cache-control':'no-cache'});
      for (const event of [
        {type:'response.output_text.delta',delta:'E2E streamed response'},
        {type:'response.completed',response:{id:'fixture-stream-1'}}
      ]) res.write('data: '+JSON.stringify(event)+'\\n\\n');
      return res.end();
    }
    res.writeHead(200,{'content-type':'application/json'});
    res.end(JSON.stringify({id:'fixture-chat-1',output_text:'E2E chat response'}));
  });
  const upstreamPort=await listenEphemeral(upstream);
  app=spawn(process.execPath,['server.mjs'],{
    cwd:join(process.cwd(),'backend'),
    env:{...process.env,PORT:String(appPort),OPENAI_API_KEY:'e2e-test-key',OPENAI_MODEL:'e2e-fixture-model',
      SHADOW_OPENAI_RESPONSES_URL:'http://127.0.0.1:'+upstreamPort+'/v1/responses',
      SHADOW_MEMORY_DIR:join(tempDir,'memory'),SHADOW_WORKSPACE_DIR:join(tempDir,'workspace'),
      XAI_API_KEY:'',DEEPSEEK_API_KEY:'',DATABASE_URL:''},
    stdio:['ignore','pipe','pipe']
  });
  let stdout='';
  app.stdout.on('data',d=>{stdout+=d.toString();const m=stdout.match(/listening on (\d+)/);if(m)appPort=Number(m[1]);});
  app.stderr.on('data',d=>process.stderr.write(d));
  const deadline=Date.now()+15000;
  while(!appPort&&Date.now()<deadline)await new Promise(r=>setTimeout(r,100));
  if(!appPort)throw new Error('backend_server_port_not_detected');
});

after(async()=>{
  if(app)app.kill('SIGTERM');
  if(upstream)await new Promise(r=>upstream.close(r));
  if(tempDir)await rm(tempDir,{recursive:true,force:true});
});

test('health exposes online provider and streaming',async()=>{
  const body=await(await fetch('http://127.0.0.1:'+appPort+'/health')).json();
  assert.equal(body.ok,true); assert.equal(body.capabilities.streaming_chat,true); assert.equal(body.providers.openai.configured,true);
});
test('online chat saves and recalls governed memory',async()=>{
  const save=await(await fetch('http://127.0.0.1:'+appPort+'/v1/chat',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({message:'remember E2E fact'})})).json();
  assert.equal(save.ok,true);
  const recall=await(await fetch('http://127.0.0.1:'+appPort+'/v1/chat',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({message:'recall E2E fact'})})).json();
  assert.equal(recall.answer,'E2E memory fact 42 recalled'); assert.ok(upstreamBodies.length>=3);
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
