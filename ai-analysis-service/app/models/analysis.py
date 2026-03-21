from pydantic import BaseModel
from typing import List

class Risk(BaseModel):
    type: str
    description: str
    severity: str | None = None
    mitigation: str | None = None

class AnalysisResult(BaseModel):
    components: List[str]
    risks: List[Risk]
    recommendations: List[str]
