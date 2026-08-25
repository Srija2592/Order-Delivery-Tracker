from google.adk.agents.context import Context
import requests
from app.tools.api_client import get_order
from math import radians, sin, cos, sqrt, atan2

def get_order_status(order_id: str, context: Context):

   order = get_order(order_id, context)

   return {
        "order_id": order["id"],
        "status": order["status"]
    }



def get_driver_location(order_id: str, context: Context):
    order = get_order(order_id, context)

    return {
        "order_id": order["id"],
        "truck_id": order["truckId"],
        "current_latitude": order["curLat"],
        "current_longitude": order["curLon"]
    }
    

def get_order_details(order_id: str, context: Context):
    """Get basic details about an order."""

    token = context.state.get("authorization")

    url = f"http://localhost:8080/api/user/orders/{order_id}"

    response = requests.get(
        url,
        headers={
            "Authorization": token
        }
    )

    response.raise_for_status()

    data = response.json()

    return {
        "order_id": data["id"],
        "items": data.get("items"),
        "status": data.get("status"),
        "creation_date": data.get("creationDate"),
        "truck_id": data.get("truckId")
    }

def calculate_eta(order_id: str, context: Context):
    order = get_order(order_id, context)

    current_lat = order["curLat"]
    current_lon = order["curLon"]

    destination_lat = order["desLat"]
    destination_lon = order["desLon"]

    # Earth radius in kilometers
    R = 6371.0

    lat1 = radians(current_lat)
    lat2 = radians(destination_lat)

    delta_lat = radians(destination_lat - current_lat)
    delta_lon = radians(destination_lon - current_lon)

    a = (
        sin(delta_lat / 2) ** 2
        + cos(lat1)
        * cos(lat2)
        * sin(delta_lon / 2) ** 2
    )

    c = 2 * atan2(sqrt(a), sqrt(1 - a))

    distance_km = R * c

    # Temporary average speed assumption
    average_speed_kmph = 40

    eta_hours = distance_km / average_speed_kmph
    eta_minutes = round(eta_hours * 60)

    return {
        "order_id": order_id,
        "distance_km": round(distance_km, 2),
        "average_speed_kmph": average_speed_kmph,
        "eta_minutes": eta_minutes
    }
