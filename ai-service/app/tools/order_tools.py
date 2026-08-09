from google.adk.agents.context import Context
import requests


def get_order_status(order_id: str, context: Context):

    print("========== TOOL START ==========")
    print("Order ID:", order_id)

    token = context.state.get("authorization")
    print("Token:", token)

    if not token:
        return {"error": "No authorization token found in context"}

    # Make sure it starts with "Bearer "
    if not token.startswith("Bearer "):
        token = f"Bearer {token}"

    url = f"http://localhost:8080/api/user/orders/{order_id}"
    print("Calling:", url)
    print("Using header:", token[:30] + "...")   # print only first part for safety

    response = requests.get(
        url,
        headers={
            "Authorization": token
        }
    )

    print("Status Code:", response.status_code)
    print("Response:", response.text)

    if response.status_code == 403:
        return {
            "error": "Access denied (403). Token may be invalid or you don't have permission for this order.",
            "status_code": 403
        }

    response.raise_for_status()

    try:
        return response.json()
    except Exception:
        return {"error": response.text}