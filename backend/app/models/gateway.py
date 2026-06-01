from pydantic import BaseModel
from typing import Optional

class GatewayIn(BaseModel):
    action: str
    payload: Optional[dict] = None

class GatewayOut(BaseModel):
    ok: bool
    action: str
    result: dict
    mocked: bool = True
