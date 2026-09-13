"""MOD-33.4: E.V.-style deterministic analysis primitives.

Keeps numerical work separate from the language model: the model explains,
while this module computes. No network and no arbitrary code execution.
"""
from __future__ import annotations
from dataclasses import dataclass
from statistics import mean, median, pstdev
from typing import Iterable

@dataclass(frozen=True)
class AnalysisResult:
    count: int
    mean: float
    median: float
    minimum: float
    maximum: float
    standard_deviation: float
    trend: str


def analyze(values: Iterable[float]) -> AnalysisResult:
    xs=[float(v) for v in values]
    if not xs:
        raise ValueError("no numeric observations")
    trend="stable"
    if len(xs)>=2:
        first=mean(xs[:max(1,len(xs)//2)])
        last=mean(xs[-max(1,len(xs)//2):])
        delta=last-first
        scale=max(abs(mean(xs)),1e-9)
        if delta>scale*0.05: trend="rising"
        elif delta<-scale*0.05: trend="falling"
    return AnalysisResult(len(xs),mean(xs),median(xs),min(xs),max(xs),pstdev(xs),trend)


def jump_analysis(distances: Iterable[float]) -> str:
    r=analyze(distances)
    return (f"Analyzing jump distances for patterns…\n"
            f"Calculating…\n"
            f"count={r.count}; mean={r.mean:.2f}; median={r.median:.2f}; "
            f"min={r.minimum:.2f}; max={r.maximum:.2f}; sd={r.standard_deviation:.2f}; "
            f"trend={r.trend}\nCalculations complete.")
