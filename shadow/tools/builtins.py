"""Safe built-in tools for the SHADOW runtime.

MOD-17.2: expose Home/Car as real runtime tool boundaries while keeping
control fail-closed until an authorized external adapter is connected.
"""
from __future__ import annotations
import ast
import datetime as _dt
import operator
from typing import Any
from shadow.tools.registry import ToolRegistry, ToolSpec

_BIN={ast.Add:operator.add,ast.Sub:operator.sub,ast.Mult:operator.mul,ast.Div:operator.truediv,ast.FloorDiv:operator.floordiv,ast.Mod:operator.mod,ast.Pow:operator.pow}
_UN={ast.UAdd:operator.pos,ast.USub:operator.neg}

def _calc_node(node:ast.AST)->float:
    if isinstance(node,ast.Constant) and isinstance(node.value,(int,float)): return node.value
    if isinstance(node,ast.BinOp) and type(node.op) in _BIN:
        left,right=_calc_node(node.left),_calc_node(node.right)
        if isinstance(node.op,ast.Pow) and abs(right)>100: raise ValueError("exponent too large")
        return _BIN[type(node.op)](left,right)
    if isinstance(node,ast.UnaryOp) and type(node.op) in _UN: return _UN[type(node.op)](_calc_node(node.operand))
    raise ValueError("only numeric arithmetic is allowed")

def calculate(expression:str)->str:
    tree=ast.parse(str(expression),mode="eval"); value=_calc_node(tree.body)
    if isinstance(value,float) and value.is_integer(): value=int(value)
    return str(value)

def current_time()->str:
    return _dt.datetime.now().astimezone().isoformat(timespec="seconds")

def build_builtin_registry(*,memory:Any=None,devices:Any=None,domains:Any=None)->ToolRegistry:
    registry=ToolRegistry()
    registry.register(ToolSpec("calculator","Evaluate safe numeric arithmetic.",calculate,False,"utility",
        {"type":"object","properties":{"expression":{"type":"string"}},"required":["expression"],"additionalProperties":False}))
    registry.register(ToolSpec("time_now","Return the local system time and date.",current_time,False,"utility",
        {"type":"object","properties":{},"required":[],"additionalProperties":False}))
    if memory is not None:
        registry.register(ToolSpec("memory_search","Search SHADOW persistent memory.",
            lambda query,limit: [m.text for m in memory.search(query,int(limit))],False,"memory",
            {"type":"object","properties":{"query":{"type":"string"},"limit":{"type":"integer","minimum":1,"maximum":20}},"required":["query","limit"],"additionalProperties":False}))
    if devices is not None:
        registry.register(ToolSpec("device_status","Return registered device status.",lambda:devices.snapshot(),False,"device",
            {"type":"object","properties":{},"required":[],"additionalProperties":False}))
    if domains is not None:
        registry.register(ToolSpec("home_status","Return Smart Home adapter status.",lambda:domains.status("home"),False,"home",
            {"type":"object","properties":{},"required":[],"additionalProperties":False}))
        registry.register(ToolSpec("car_status","Return vehicle adapter status.",lambda:domains.status("car"),False,"car",
            {"type":"object","properties":{},"required":[],"additionalProperties":False}))
        registry.register(ToolSpec("home_control","Request an authorized Smart Home action.",
            lambda capability,payload:domains.execute("home",capability,payload,authorized=True),True,"home",
            {"type":"object","properties":{"capability":{"type":"string"},"payload":{"type":"object"}},"required":["capability","payload"],"additionalProperties":False}))
        registry.register(ToolSpec("car_control","Request an authorized vehicle action.",
            lambda capability,payload:domains.execute("car",capability,payload,authorized=True),True,"car",
            {"type":"object","properties":{"capability":{"type":"string"},"payload":{"type":"object"}},"required":["capability","payload"],"additionalProperties":False}))
    return registry
