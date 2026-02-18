import { useMemo, useState } from "react";
import AircraftCard from "../components/AircraftCard";
import AddAircraftModal from "../components/AddAircraftModal";
import { useAircraftStore } from "../store/aircraftStore";

const Monitoring = () => {
  const { aircraft, addAircraft, removeAircraft, ui } = useAircraftStore();
  const [isModalOpen, setIsModalOpen] = useState(false);

  const densityClasses = useMemo(() => {
    return ui.cardDensity === "compact"
      ? "gap-4 sm:grid-cols-2 lg:grid-cols-3"
      : "gap-6 sm:grid-cols-2 lg:grid-cols-3";
  }, [ui.cardDensity]);

  return (
    <section className="pt-8">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
            Monitoring
          </p>
          <h1 className="mt-2 text-3xl font-semibold text-white">
            Fleet Overview
          </h1>
          <p className="mt-2 max-w-xl text-sm text-slate-300">
            Track multiple aircraft in one place. Telemetry remains offline until
            data providers are connected.
          </p>
        </div>
        <button
          className="inline-flex items-center justify-center rounded-full bg-gradient-to-r from-cyan-400 to-blue-500 px-5 py-2.5 text-sm font-semibold text-slate-900 shadow-soft transition hover:opacity-90"
          onClick={() => setIsModalOpen(true)}
        >
          Add aircraft
        </button>
      </div>

      <div className="mt-8">
        {aircraft.length === 0 ? (
          <div className="rounded-3xl border border-white/10 bg-white/5 px-6 py-10 text-center shadow-glow backdrop-blur">
            <p className="text-sm text-slate-300">
              No aircraft tracked yet. Add the first ICAO24 to begin building
              your list.
            </p>
          </div>
        ) : (
          <div className={`grid grid-cols-1 ${densityClasses}`}>
            {aircraft.map((item) => (
              <AircraftCard
                key={item.id}
                aircraft={item}
                onRemove={() => removeAircraft(item.id)}
              />
            ))}
          </div>
        )}
      </div>

      <AddAircraftModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSave={(payload) => {
          addAircraft(payload);
          setIsModalOpen(false);
        }}
      />
    </section>
  );
};

export default Monitoring;
