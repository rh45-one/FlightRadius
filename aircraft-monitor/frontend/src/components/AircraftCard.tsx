import { Aircraft } from "../store/aircraftStore";

type AircraftCardProps = {
  aircraft: Aircraft;
  onRemove: () => void;
};

const AircraftCard = ({ aircraft, onRemove }: AircraftCardProps) => {
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
        <span className="inline-flex items-center gap-2 rounded-full bg-emerald-400/10 px-3 py-1 text-xs text-emerald-200">
          <span className="h-2 w-2 rounded-full bg-emerald-400" />
          Offline (placeholder)
        </span>
      </div>

      <div className="mt-6 grid gap-3 text-xs text-slate-300">
        <div className="flex items-center justify-between">
          <span>Distance</span>
          <span className="text-slate-100">— km</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Coordinates</span>
          <span className="text-slate-100">— , —</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Last update</span>
          <span className="text-slate-100">—</span>
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
