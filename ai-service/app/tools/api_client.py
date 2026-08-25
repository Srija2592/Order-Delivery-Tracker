import requests
from google.adk.agents.context import Context


BASE_URL = "http://localhost:8080"


def get_auth_headers(context: Context):
    token = context.state.get("authorization")

    print("========== AUTH ==========")
    print("Token exists:", token is not None)

    if not token:
        raise Exception("Authorization token not found in session state")

    return {
        "Authorization": token,
        "Content-Type": "application/json"
    }


def get_order(order_id: str, context: Context):

    url = f"{BASE_URL}/api/user/orders/{order_id}"

    headers = get_auth_headers(context)

    print("Calling:", url)

    response = requests.get(
        url,
        headers=headers
    )

    print("Status:", response.status_code)
    print("Response:", response.text)

    response.raise_for_status()

    return response.json()