from pydantic import BaseModel

class ChatRequest(BaseModel):
    message: str
    access_token: str