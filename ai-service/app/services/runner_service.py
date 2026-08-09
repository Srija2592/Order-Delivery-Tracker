from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService

from app.agents.customer_agent import customer_agent
from app.config import APP_NAME

session_service = InMemorySessionService()

runner = Runner(
    app_name=APP_NAME,
    agent=customer_agent,
    session_service=session_service,
)