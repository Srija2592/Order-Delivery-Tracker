from uuid import uuid4

from fastapi import APIRouter
from google.adk.events import Event
from google.genai.types import Content, Part

from app.config import APP_NAME
from app.models.chat_request import ChatRequest
from app.services.runner_service import runner, session_service

router = APIRouter()


@router.post("/chat")
async def chat(request: ChatRequest):

    user_id = "demo-user"
    session_id = "demo-session"

    # Create session only if it doesn't already exist
    session = await session_service.get_session(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=session_id
    )

    if session is None:
        session = await session_service.create_session(
            app_name=APP_NAME,
            user_id=user_id,
            session_id=session_id,
            state={
                "authorization": request.access_token
            }
        )
    else:
        await session_service.append_event(
            session,
            Event(
                author="system",
                invocation_id=str(uuid4()),
                state={"authorization": request.access_token},
            ),
        )

    message = Content(
        role="user",
        parts=[
            Part(text=request.message)
        ]
    )

    final_response = ""

    async for event in runner.run_async(
        user_id=user_id,
        session_id=session_id,
        new_message=message
    ):
        if event.is_final_response():
            if event.content and event.content.parts:
                final_response = "".join(
                    part.text or ""
                    for part in event.content.parts
                )

    return {
        "response": final_response
    }