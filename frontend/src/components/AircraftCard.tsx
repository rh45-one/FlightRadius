import { AircraftTelemetry, DistanceResult } from "../services/api";
import { formatDistance } from "../utils/distance";

type AircraftLike = {
  id: string;
  icao24?: string;
  callsign?: string;
  notes?: string;
  createdAt: string;
};

type AircraftCardProps = {
  aircraft: AircraftLike;
  onRemove: () => void;
  telemetry?: AircraftTelemetry;
  distanceData?: DistanceResult;
  distanceKm?: number;
  distanceUnit: "km" | "mi";
  rank?: number;
  dataSourceLabel?: string;
  status: "loading" | "live" | "stale" | "offline";
  errorMessage?: string;
  groupLabel?: string;
  groupColor?: string;
};

const AircraftCard = ({
  aircraft,
  onRemove,
  telemetry,
  distanceData,
  distanceKm,
  distanceUnit,
  rank,
  dataSourceLabel,
  status,
  errorMessage,
  groupLabel,
  groupColor
}: AircraftCardProps) => {
  const statusStyles = {
    loading: "bg-slate-400/10 text-slate-200",
    live: "bg-emerald-400/10 text-emerald-200",
    stale: "bg-amber-400/10 text-amber-200",
    offline: "bg-rose-400/10 text-rose-200"
  };

  const statusLabel = {
    loading: "Loading",
    live: "Live",
    stale: "Stale",
    offline: "Offline"
  };

  const distanceKmValue =
    distanceKm !== undefined ? distanceKm : distanceData?.distance_km;
  const distanceLabel =
    distanceKmValue !== undefined
      ? formatDistance(distanceKmValue, distanceUnit)
      : "—";
  const altitudeFeet = telemetry
    ? Math.round(telemetry.altitude_m * 3.28084)
    : distanceData
    ? Math.round(distanceData.altitude_m * 3.28084)
    : null;
  const coordinatesLabel = telemetry
    ? `${telemetry.latitude.toFixed(4)}, ${telemetry.longitude.toFixed(4)}`
    : distanceData
    ? `${distanceData.lat.toFixed(4)}, ${distanceData.lon.toFixed(4)}`
    : "— , —";
  const lastUpdate = telemetry?.last_contact
    ? new Date(telemetry.last_contact * 1000).toLocaleTimeString()
    : distanceData?.last_update
    ? new Date(distanceData.last_update).toLocaleTimeString()
    : "—";

  const primaryId = aircraft.callsign || aircraft.icao24 || "Unknown";

  return (
    <div className="flex h-full flex-col justify-between rounded-3xl border border-white/10 bg-white/5 p-5 shadow-glow backdrop-blur">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
            {primaryId}
          </p>
          <h3 className="mt-2 text-lg font-semibold text-white">
            {aircraft.callsign || aircraft.icao24 || "Unassigned callsign"}
          </h3>
          {dataSourceLabel ? (
            <span className="mt-2 inline-flex rounded-full border border-emerald-400/40 px-2 py-0.5 text-[10px] uppercase tracking-[0.25em] text-emerald-200">
              {dataSourceLabel}
            </span>
          ) : null}
          {aircraft.notes ? (
            <p className="mt-2 text-sm text-slate-300">{aircraft.notes}</p>
          ) : (
            <p className="mt-2 text-sm text-slate-500">
              No notes added yet.
            </p>
          )}
          {groupLabel ? (
            <span
              className="mt-3 inline-flex items-center rounded-full px-3 py-1 text-xs"
              style={{
                backgroundColor: groupColor ? `${groupColor}22` : "#0f172a",
                color: groupColor || "#94a3b8",
                border: groupColor ? `1px solid ${groupColor}55` : "1px solid #1f2937"
              }}
            >
              {groupLabel}
            </span>
          ) : null}
        </div>
        <span
          className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs ${
            statusStyles[status]
          }`}
        >
          <span
            className={`h-2 w-2 rounded-full ${
              status === "loading"
                ? "bg-slate-300 animate-pulse"
                : status === "live"
                ? "bg-emerald-400"
                : status === "stale"
                ? "bg-amber-400"
                : "bg-rose-400"
            }`}
          />
          {statusLabel[status]}
        </span>
      </div>

      {status === "offline" && errorMessage ? (
        <p className="mt-3 text-xs text-rose-200">{errorMessage}</p>
      ) : null}

      <div className="mt-3 text-xs text-yellow-200">
        DEBUG DISTANCE: {distanceKm !== undefined ? `${distanceKm} km` : "NO DATA"}
      </div>

      <div className="mt-6 grid gap-3 text-xs text-slate-300">
        <div className="flex items-center justify-between">
          <span>Distance</span>
          <span className="text-slate-100">{distanceLabel}</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Coordinates</span>
          <span className="text-slate-100">{coordinatesLabel}</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Altitude</span>
          <span className="text-slate-100">
            {altitudeFeet !== null ? `${altitudeFeet} ft` : "—"}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span>Last update</span>
          <span className="text-slate-100">{lastUpdate}</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Rank</span>
          <span className="text-slate-100">
            {rank ? `#${rank} closest` : "—"}
          </span>
        </div>
      </div>

      <div className="mt-6 flex items-center justify-between text-xs text-slate-400">
        <span>Added {new Date(aircraft.createdAt).toLocaleDateString()}</span>
        <button
          onClick={onRemove}
          className="rounded-full border border-white/10 px-3 py-1 text-xs text-slate-300 transition hover:border-rose-400/60 hover:text-rose-200"
        >
          Remove
        </button>
      </div>
    </div>
  );
};

export default AircraftCard;
