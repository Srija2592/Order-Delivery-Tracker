import requests

from app.config import SPRING_BOOT_URL


def get_order(order_id: str, authorization: str):

    response = requests.get(
        f"{SPRING_BOOT_URL}/api/user/orders/{order_id}",
        headers={
            "Authorization": authorization
        },
        timeout=10
    )

    response.raise_for_status()

    return response.json()