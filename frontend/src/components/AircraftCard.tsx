import { Aircraft } from "../store/aircraftStore";
import { AircraftTelemetry } from "../services/api";

type AircraftCardProps = {
  aircraft: Aircraft;
  onRemove: () => void;
  telemetry?: AircraftTelemetry;
  status: "loading" | "live" | "stale" | "offline";
  errorMessage?: string;
};

const AircraftCard = ({
  aircraft,
  onRemove,
  telemetry,
  status,
  errorMessage
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

  const lastContact = telemetry?.last_contact
    ? new Date(telemetry.last_contact * 1000).toLocaleTimeString()
    : "—";

  return (
    <div className="flex h-full flex-col justify-between rounded-3xl border border-white/10 bg-white/5 p-5 shadow-glow backdrop-blur">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
            {aircraft.icao24}
          </p>
          <h3 className="mt-2 text-lg font-semibold text-white">
            {aircraft.callsign || "Unassigned callsign"}
          </h3>
          {aircraft.notes ? (
            <p className="mt-2 text-sm text-slate-300">{aircraft.notes}</p>
          ) : (
            <p className="mt-2 text-sm text-slate-500">
              No notes added yet.
            </p>
          )}
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

      <div className="mt-6 grid gap-3 text-xs text-slate-300">
        <div className="flex items-center justify-between">
          <span>Distance</span>
          <span className="text-slate-100">— km</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Coordinates</span>
          <span className="text-slate-100">
            {telemetry ? `${telemetry.latitude}, ${telemetry.longitude}` : "— , —"}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span>Altitude</span>
          <span className="text-slate-100">
            {telemetry ? `${telemetry.altitude_m} m` : "—"}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span>Heading</span>
          <span className="text-slate-100">
            {telemetry ? `${telemetry.heading_deg} deg` : "—"}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span>Velocity</span>
          <span className="text-slate-100">
            {telemetry ? `${telemetry.velocity_mps} m/s` : "—"}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span>Last contact</span>
          <span className="text-slate-100">{lastContact}</span>
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
