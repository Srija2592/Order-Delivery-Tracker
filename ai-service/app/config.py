from dotenv import load_dotenv
import os

load_dotenv()

APP_NAME = "order-delivery-ai"

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
GEMINI_MODEL = os.getenv("GEMINI_MODEL")

SPRING_BOOT_URL = os.getenv(
    "ORDER_SERVICE_URL",
    "http://localhost:8080"
)