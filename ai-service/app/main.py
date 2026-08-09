from fastapi import FastAPI
from dotenv import load_dotenv

from app.routes.chat import router as chat_router

load_dotenv()

app = FastAPI(
    title="AI Delivery Service",
    version="1.0.0"
)

app.include_router(chat_router)


@app.get("/health")
def health():
    return {
        "status": "UP"
    }