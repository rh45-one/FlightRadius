import { useEffect, useMemo, useState } from "react";
import AircraftCard from "../components/AircraftCard";
import AddAircraftModal from "../components/AddAircraftModal";
import BulkAddAircraftModal from "../components/BulkAddAircraftModal";
import { useAircraftStore } from "../store/aircraftStore";
import {
  getAircraftStatus,
  getAircraftStatusByCallsign,
  AircraftTelemetry
} from "../services/api";
import { useLocationStore } from "../store/locationStore";
import { useFleetStore } from "../store/fleetStore";

type TelemetryState = {
  status: "loading" | "live" | "stale" | "offline";
  data?: AircraftTelemetry;
  errorMessage?: string;
};

type CombinedAircraft = {
  id: string;
  callsign?: string;
  icao24?: string;
  notes?: string;
  createdAt: string;
  groupId?: string;
  source: "bulk" | "single";
};

const Monitoring = () => {
  const { aircraft, addAircraft, removeAircraft, ui } = useAircraftStore();
  const { fleetAircraft, removeFleetAircraft, getGroupById, groups } =
    useFleetStore();
  const {
    currentPosition,
    permissionStatus,
    pollingActive,
    lastUpdated,
    errorState
  } = useLocationStore();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isBulkOpen, setIsBulkOpen] = useState(false);
  const [telemetry, setTelemetry] = useState<Record<string, TelemetryState>>(
    {}
  );

  const locationBadge = () => {
    if (permissionStatus === "manual") {
      return { label: "Manual", tone: "bg-cyan-400/10 text-cyan-200" };
    }
    if (permissionStatus === "denied") {
      return { label: "Denied", tone: "bg-rose-400/10 text-rose-200" };
    }
    if (permissionStatus === "unsupported") {
      return { label: "Unsupported", tone: "bg-rose-400/10 text-rose-200" };
    }
    if (!pollingActive) {
      return { label: "Disabled", tone: "bg-slate-400/10 text-slate-200" };
    }
    if (currentPosition) {
      return { label: "Active", tone: "bg-emerald-400/10 text-emerald-200" };
    }
    return { label: "Pending", tone: "bg-slate-400/10 text-slate-200" };
  };

  const locationStatus = locationBadge();
  const lastUpdatedLabel = lastUpdated
    ? new Date(lastUpdated).toLocaleTimeString()
    : "—";

  const combinedAircraft = useMemo<CombinedAircraft[]>(() => {
    const bulk = fleetAircraft.map((item) => ({
      ...item,
      source: "bulk" as const
    }));
    const singles = aircraft.map((item) => ({
      ...item,
      source: "single" as const
    }));
    return [...bulk, ...singles];
  }, [aircraft, fleetAircraft]);

  const densityClasses = useMemo(() => {
    return ui.cardDensity === "compact"
      ? "gap-4 sm:grid-cols-2 lg:grid-cols-3"
      : "gap-6 sm:grid-cols-2 lg:grid-cols-3";
  }, [ui.cardDensity]);

  useEffect(() => {
    let isActive = true;

    if (combinedAircraft.length === 0) {
      setTelemetry({});
      return;
    }

    setTelemetry((prev) => {
      const next: Record<string, TelemetryState> = { ...prev };
      for (const entry of combinedAircraft) {
        next[entry.id] = {
          status: "loading",
          data: prev[entry.id]?.data
        };
      }
      return next;
    });

    const loadTelemetry = async () => {
      await Promise.all(
        combinedAircraft.map(async (entry) => {
          try {
            const data = entry.icao24
              ? await getAircraftStatus(entry.icao24)
              : entry.callsign
              ? await getAircraftStatusByCallsign(entry.callsign)
              : null;

            if (!data) {
              throw new Error("Missing identifier");
            }
            if (!isActive) {
              return;
            }

            const nowSec = Date.now() / 1000;
            const isStale = nowSec - data.last_contact > 30;

            setTelemetry((prev) => ({
              ...prev,
              [entry.id]: {
                status: isStale ? "stale" : "live",
                data
              }
            }));
          } catch (error) {
            if (!isActive) {
              return;
            }

            setTelemetry((prev) => ({
              ...prev,
              [entry.id]: {
                status: "offline",
                errorMessage: (error as Error).message
              }
            }));
          }
        })
      );
    };

    loadTelemetry();

    return () => {
      isActive = false;
    };
  }, [combinedAircraft]);

  return (
    <section className="pt-8">
      <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
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
        <div className="w-full rounded-3xl border border-white/10 bg-white/5 p-4 shadow-glow backdrop-blur sm:max-w-sm">
          <div className="flex items-center justify-between">
            <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
              Location
            </p>
            <span
              className={`rounded-full px-3 py-1 text-xs ${locationStatus.tone}`}
            >
              {locationStatus.label}
            </span>
          </div>
          <div className="mt-3 space-y-2 text-xs text-slate-300">
            <div className="flex items-center justify-between">
              <span>Coordinates</span>
              <span className="text-slate-100">
                {currentPosition
                  ? `${currentPosition.latitude.toFixed(4)}, ${currentPosition.longitude.toFixed(4)}`
                  : "—"}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span>Accuracy</span>
              <span className="text-slate-100">
                {currentPosition ? `${currentPosition.accuracy_m} m` : "—"}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span>Source</span>
              <span className="text-slate-100">
                {currentPosition?.source || "—"}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span>Last update</span>
              <span className="text-slate-100">{lastUpdatedLabel}</span>
            </div>
          </div>
          {errorState ? (
            <p className="mt-3 text-xs text-rose-200">{errorState}</p>
          ) : null}
        </div>
        <div className="flex w-full flex-col gap-2 sm:w-auto sm:items-end">
          <button
            className="inline-flex w-full items-center justify-center rounded-full bg-gradient-to-r from-cyan-400 to-blue-500 px-5 py-2.5 text-sm font-semibold text-slate-900 shadow-soft transition hover:opacity-90 sm:w-auto"
            onClick={() => setIsModalOpen(true)}
          >
            Add aircraft
          </button>
          <button
            className="inline-flex w-full items-center justify-center rounded-full border border-white/20 px-5 py-2 text-xs uppercase tracking-[0.2em] text-slate-200 transition hover:border-white/40 sm:w-auto"
            onClick={() => setIsBulkOpen(true)}
          >
            Bulk import
          </button>
        </div>
      </div>

      {groups.length > 0 ? (
        <div className="mt-6 rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
                Group tracking
              </p>
              <h2 className="mt-2 text-lg font-semibold text-white">
                Map view (placeholder)
              </h2>
            </div>
            <span className="rounded-full border border-white/10 px-3 py-1 text-xs text-slate-300">
              Coming soon
            </span>
          </div>
          <div className="mt-4 h-48 rounded-2xl border border-dashed border-white/10 bg-slate-950/40 sm:h-64" />
        </div>
      ) : null}

      <div className="mt-8">
        {combinedAircraft.length === 0 ? (
          <div className="rounded-3xl border border-white/10 bg-white/5 px-6 py-10 text-center shadow-glow backdrop-blur">
            <p className="text-sm text-slate-300">
              No aircraft tracked yet. Add an ICAO24 or import callsigns to
              begin building your list.
            </p>
          </div>
        ) : (
          <div className={`grid grid-cols-1 ${densityClasses}`}>
            {combinedAircraft.map((item) => {
              const group = item.groupId ? getGroupById(item.groupId) : undefined;

              return (
                <AircraftCard
                  key={item.id}
                  aircraft={item}
                  onRemove={() =>
                    item.source === "bulk"
                      ? removeFleetAircraft(item.id)
                      : removeAircraft(item.id)
                  }
                  telemetry={telemetry[item.id]?.data}
                  status={telemetry[item.id]?.status || "loading"}
                  errorMessage={telemetry[item.id]?.errorMessage}
                  groupLabel={group?.name}
                  groupColor={group?.color}
                />
              );
            })}
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
      <BulkAddAircraftModal
        isOpen={isBulkOpen}
        onClose={() => setIsBulkOpen(false)}
      />
    </section>
  );
};

export default Monitoring;
