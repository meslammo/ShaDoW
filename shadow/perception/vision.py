"""MOD-37.6: multimodal perception result contract."""
from dataclasses import dataclass

@dataclass(frozen=True)
class VisionResult:
    mime_type:str
    width:int|None=None
    height:int|None=None
    text:str=""
    verified:bool=False

class VisionRouter:
    """Never invents OCR or visual facts; provider/local engines fill results."""
    def receipt(self,data:bytes,mime_type:str)->dict:
        return {"bytes":len(data),"mime_type":mime_type,"needs_model":True}
    def result(self,mime_type:str,text:str,width:int|None=None,height:int|None=None)->VisionResult:
        return VisionResult(mime_type,width,height,text,bool(text.strip()))
