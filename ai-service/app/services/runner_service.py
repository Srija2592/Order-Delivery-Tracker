
from google.adk.runners import Runner
from google.adk.sessions import DatabaseSessionService

from app.agents.customer_agent import customer_agent
from app.config import APP_NAME

session_service = DatabaseSessionService(
    db_url="sqlite+aiosqlite:///./sessions.db"
)

runner = Runner(
    app_name=APP_NAME,
    agent=customer_agent,
    session_service=session_service,
)
