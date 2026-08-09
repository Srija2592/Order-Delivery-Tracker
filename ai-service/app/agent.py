from google.adk.agents import Agent
from app.config import GEMINI_MODEL

from app.tools.order_tools import get_order_status

root_agent = Agent(
    name="order_delivery_agent",
    model=GEMINI_MODEL,   # or whichever model you're using
 instruction="""
You are an Order Delivery Assistant.

Whenever a user asks about an order, its status, location,
ETA, or delivery information, ALWAYS call get_order_status.

Never answer from your own knowledge.
""",
    tools=[
        get_order_status
    ]
)