import http from 'node:http';
const port = Number(process.env.PORT || 8788);
const server = http.createServer((req,res)=>{
  if(req.url==='/health' && req.method==='GET'){
    res.writeHead(200,{'content-type':'application/json'});
    return res.end(JSON.stringify({ok:true,service:'shadow-e2e-fixture',provider:'openai-fixture'}));
  }
  if(req.url==='/v1/chat/stream' && req.method==='POST'){
    res.writeHead(200,{'content-type':'text/event-stream; charset=utf-8','cache-control':'no-cache','connection':'keep-alive'});
    for(const event of [
      {type:'delta',text:'E2E OK from cloud fixture'},
      {type:'done',responseId:'fixture-response-1',provider:'openai',model:'e2e-fixture',usedWeb:false},
      {type:'eof'}
    ]) res.write('data: '+JSON.stringify(event)+'\n\n');
    return res.end();
  }
  if(req.url==='/v1/chat' && req.method==='POST'){
    res.writeHead(200,{'content-type':'application/json'});
    return res.end(JSON.stringify({ok:true,answer:'E2E OK from cloud fixture',response_id:'fixture-response-1',provider:'openai',model:'e2e-fixture',reasoning_effort:'none',used_web_search:false,pending_action:null}));
  }
  res.writeHead(404,{'content-type':'application/json'});
  res.end(JSON.stringify({ok:false,error:'not_found'}));
});
server.listen(port,'127.0.0.1',()=>console.log('SHADOW E2E fixture listening on '+port));
