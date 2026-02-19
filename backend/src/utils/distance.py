import logging
import math

EARTH_RADIUS_KM = 6371.0
logger = logging.getLogger(__name__)


class MissingCoordinateError(ValueError):
    """Structured error for missing or invalid coordinates."""

    def __init__(self, message: str, details: dict):
        super().__init__(message)
        self.details = details


def _ensure_number(value: object, field: str) -> float:
    if value is None or not isinstance(value, (int, float)):
        raise MissingCoordinateError("Missing coordinate", {"field": field})
    if not math.isfinite(value):
        raise MissingCoordinateError("Invalid coordinate", {"field": field})
    return float(value)


def calculate_distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate the great-circle distance between two points in kilometers."""
    R = 6371

    lat1 = math.radians(lat1)
    lon1 = math.radians(lon1)
    lat2 = math.radians(lat2)
    lon2 = math.radians(lon2)

    dlat = lat2 - lat1
    dlon = lon2 - lon1

    a = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    distance = R * c
    print(f"[DISTANCE] {lat1},{lon1} -> {lat2},{lon2} = {distance} km")
    return round(distance, 2)


def compute_distances(user_lat: float, user_lon: float, aircraft_list: list[dict]) -> list[dict]:
    user_lat_val = _ensure_number(user_lat, "user_lat")
    user_lon_val = _ensure_number(user_lon, "user_lon")

    results: list[dict] = []
    for aircraft in aircraft_list or []:
        lat = aircraft.get("lat")
        lon = aircraft.get("lon")
        callsign = aircraft.get("callsign", "unknown")

        if lat is None or lon is None or not isinstance(lat, (int, float)) or not isinstance(lon, (int, float)):
            logger.info("Skipping aircraft with missing coordinates: %s", callsign)
            continue
        if not math.isfinite(lat) or not math.isfinite(lon):
            logger.info("Skipping aircraft with invalid coordinates: %s", callsign)
            continue

        distance = calculate_distance_km(user_lat_val, user_lon_val, lat, lon)
        altitude = aircraft.get("altitude")
        if altitude is None:
            altitude = aircraft.get("altitude_m")

        results.append(
            {
                "callsign": callsign,
                "distance_km": distance,
                "altitude": altitude,
            }
        )

    results.sort(key=lambda item: item["distance_km"])
    return results


def compute_group_proximity(user_lat: float, user_lon: float, groups: list[dict]) -> list[dict]:
    user_lat_val = _ensure_number(user_lat, "user_lat")
    user_lon_val = _ensure_number(user_lon, "user_lon")

    output: list[dict] = []
    for group in groups or []:
        name = group.get("name") or group.get("group_name") or "Unnamed Fleet"
        aircraft_list = group.get("aircraft") or group.get("aircraft_list") or []

        ranked = compute_distances(user_lat_val, user_lon_val, aircraft_list)
        closest = ranked[0] if ranked else None

        output.append(
            {
                "group_name": name,
                "closest_aircraft": closest,
                "members_ranked": ranked,
            }
        )

    return output
