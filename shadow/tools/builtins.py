"""Safe built-in tools for the SHADOW runtime.

MOD-16.3: concrete local capabilities used by the runtime/tool-calling loop.
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

def build_builtin_registry(*,memory:Any=None,devices:Any=None)->ToolRegistry:
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
    return registry
