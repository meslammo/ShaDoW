"""Authenticated SHADOW gateway: HTTP + WebSocket, rate limits and fail-closed production policy."""
from __future__ import annotations
import os, time
from collections import defaultdict, deque
from typing import Any
try:
    from fastapi import FastAPI, Header, HTTPException, WebSocket, WebSocketDisconnect
    from pydantic import BaseModel, Field
except ImportError:
    FastAPI=None
from shadow.runtime.integration import ShadowService
from shadow.runtime.transport import ShadowRequest, response_from_result

if FastAPI:
    app=FastAPI(title='SHADOW Gateway',version='0.41')
    service=ShadowService(); token=os.getenv('SHADOW_GATEWAY_TOKEN','').strip(); require_auth=os.getenv('SHADOW_REQUIRE_AUTH','true').lower()!='false'
    windows=defaultdict(deque); RATE=int(os.getenv('SHADOW_RATE_LIMIT','60')); PERIOD=60
    class RequestModel(BaseModel):
        text:str|None=None; action:str|None=None; payload:dict[str,Any]=Field(default_factory=dict)
        session_id:str|None=None; device_id:str|None=None; context:dict[str,Any]=Field(default_factory=dict)
    def authorize(authorization:str|None, client='unknown'):
        if require_auth and not token: raise HTTPException(503,'gateway authentication is not configured')
        if token and authorization != f'Bearer {token}': raise HTTPException(401,'unauthorized')
        now=time.time(); q=windows[client]
        while q and now-q[0]>PERIOD: q.popleft()
        if len(q)>=RATE: raise HTTPException(429,'rate limit exceeded')
        q.append(now)
    def run(body:RequestModel):
        text=body.text
        if not text and body.action=='run_goal': text=str(body.payload.get('goal','')).strip()
        if not text: raise HTTPException(400,'text or run_goal payload.goal is required')
        ctx=dict(body.context)
        if body.action: ctx['action']=body.action
        req=ShadowRequest(text,session_id=body.session_id,device_id=body.device_id,context=ctx)
        result=service.request(text,session_id=body.session_id,device_id=body.device_id,context=ctx)
        return response_from_result(req,result).__dict__
    @app.get('/health')
    def health(): return {'service':'SHADOW','status':'ok','version':'0.41','auth_required':require_auth}
    @app.post('/v1/request')
    def request(body:RequestModel,authorization:str|None=Header(default=None)):
        authorize(authorization); return run(body)
    @app.websocket('/v1/stream')
    async def stream(ws:WebSocket):
        auth=ws.headers.get('authorization'); client=ws.client.host if ws.client else 'unknown'
        try:
            if require_auth and not token: await ws.close(code=1011); return
            if token and auth!=f'Bearer {token}': await ws.close(code=1008); return
            await ws.accept()
            while True:
                body=RequestModel(**(await ws.receive_json())); authorize(auth,client)
                result=run(body); await ws.send_json(result)
        except WebSocketDisconnect: return
        except Exception as exc:
            try: await ws.send_json({'error':'request failed safely','detail':type(exc).__name__}); await ws.close(code=1011)
            except Exception: pass
else: app=None
