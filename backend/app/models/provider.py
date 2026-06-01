from pydantic import BaseModel
from typing import List

class OpenRouterModelsIn(BaseModel):
    api_key: str

class ProviderModelOut(BaseModel):
    id: str
    name: str

class ProviderModelsOut(BaseModel):
    data: List[ProviderModelOut]
