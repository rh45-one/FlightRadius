export type DistanceUnit = "km" | "mi";

export const convertDistance = (distanceKm: number, unit: DistanceUnit) =>
  unit === "mi" ? distanceKm * 0.621371 : distanceKm;

export const formatDistance = (
  distanceKm: number | null | undefined,
  unit: DistanceUnit
) => {
  if (distanceKm === null || distanceKm === undefined) {
    return "—";
  }

  const value = convertDistance(distanceKm, unit);
  return `${value.toFixed(2)} ${unit}`;
};
