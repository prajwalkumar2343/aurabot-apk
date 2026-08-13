from pydantic import BaseModel, EmailStr, Field
from typing import Optional


class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=6)
    name: Optional[str] = None


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class GoogleLoginIn(BaseModel):
    id_token: str = Field(min_length=1, max_length=16_000)
    nonce: str = Field(min_length=32, max_length=512)


class GoogleChallengeOut(BaseModel):
    nonce: str
    expires_in_seconds: int


class RefreshIn(BaseModel):
    refresh_token: Optional[str] = None


class UserOut(BaseModel):
    id: str
    email: str
    name: Optional[str] = None
    role: str = "user"
    service_mode: str = "local"


class AuthOut(UserOut):
    access_token: str
    refresh_token: str
