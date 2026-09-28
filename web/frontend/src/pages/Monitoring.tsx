import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import AircraftCard from "../components/AircraftCard";
import AddAircraftModal from "../components/AddAircraftModal";
import BulkAddAircraftModal from "../components/BulkAddAircraftModal";
import { useAircraftStore } from "../store/aircraftStore";
import {
  computeAircraftDistances,
  computeFleetDistances,
  getAircraftStatus,
  getAircraftStatusByCallsign,
  AircraftTelemetry,
  DistanceResult,
  FleetProximityResult
} from "../services/api";
import { useLocationStore } from "../store/locationStore";
import { useFleetStore } from "../store/fleetStore";
import {
  GeolocationError,
  PermissionStatus,
  isGeolocationSupported,
  requestLocation,
  stopWatching,
  watchLocation
} from "../services/geolocation";
import { createDistanceScheduler } from "../services/distanceScheduler";
import { useDistanceStore } from "../store/distanceStore";
import { formatDistance } from "../utils/distance";

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
  const { aircraft, addAircraft, removeAircraft, ui, settings } =
    useAircraftStore();
  const { fleetAircraft, removeFleetAircraft, getGroupById, groups } =
    useFleetStore();
  const {
    currentPosition,
    permissionStatus,
    pollingActive,
    lastUpdated,
    errorState,
    setPosition,
    setPermissionStatus,
    setPollingActive,
    setError
  } = useLocationStore();
  const {
    aircraftDistances,
    aircraftRanked,
    fleetDistances,
    closestOverall,
    missingCallsigns,
    lastComputedAt,
    errorMessage: distanceError,
    setDistanceResults,
    setError: setDistanceError
  } = useDistanceStore();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isBulkOpen, setIsBulkOpen] = useState(false);
  const [telemetry, setTelemetry] = useState<Record<string, TelemetryState>>(
    {}
  );
  const [expandedFleets, setExpandedFleets] = useState<Record<string, boolean>>(
    {}
  );
  const [distanceMap, setDistanceMap] = useState<Record<string, number>>({});
  const isDebug = useMemo(() => {
    if (typeof window === "undefined") {
      return false;
    }
    return new URLSearchParams(window.location.search).get("debug") === "true";
  }, []);
  const fetchInFlightRef = useRef(false);
  const schedulerRef = useRef<ReturnType<typeof createDistanceScheduler> | null>(
    null
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
    if (pollingActive || currentPosition) {
      return { label: "Enabled", tone: "bg-emerald-400/10 text-emerald-200" };
    }
    return { label: "Not enabled", tone: "bg-slate-400/10 text-slate-200" };
  };

  const locationStatus = locationBadge();
  const lastUpdatedLabel = lastUpdated
    ? new Date(lastUpdated).toLocaleTimeString()
    : "—";

  useEffect(() => {
    if (!isGeolocationSupported()) {
      setPermissionStatus("unsupported");
      return;
    }

    if (!("permissions" in navigator)) {
      return;
    }

    let isActive = true;
    let permissionStatusRef: PermissionStatus | null = null;

    const updatePermissionStatus = (state: PermissionState) => {
      if (state === "granted") {
        permissionStatusRef = "granted";
        setPermissionStatus("granted");
        return;
      }
      if (state === "denied") {
        permissionStatusRef = "denied";
        setPermissionStatus("denied");
        return;
      }
      permissionStatusRef = "prompt";
      setPermissionStatus("prompt");
    };

    navigator.permissions
      .query({ name: "geolocation" })
      .then((permission) => {
        if (!isActive) {
          return;
        }
        updatePermissionStatus(permission.state);
        permission.onchange = () => {
          if (!isActive) {
            return;
          }
          updatePermissionStatus(permission.state);
        };
      })
      .catch(() => {
        if (!isActive) {
          return;
        }
        if (!permissionStatusRef) {
          setPermissionStatus("unknown");
        }
      });

    return () => {
      isActive = false;
    };
  }, [setPermissionStatus]);

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
      return () => undefined;
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
    const intervalId = window.setInterval(
      loadTelemetry,
      Math.max(settings.refreshIntervalSec, 10) * 1000
    );

    return () => {
      isActive = false;
      window.clearInterval(intervalId);
    };
  }, [combinedAircraft, settings.refreshIntervalSec]);

  useEffect(() => {
    if (settings.locationMode !== "manual") {
      return;
    }

    stopWatching();
    setPollingActive(false);
    setPermissionStatus("manual");

    const latitude = Number(settings.manualLatitude);
    const longitude = Number(settings.manualLongitude);
    const isValidLatitude =
      Number.isFinite(latitude) && latitude >= -90 && latitude <= 90;
    const isValidLongitude =
      Number.isFinite(longitude) && longitude >= -180 && longitude <= 180;

    if (!isValidLatitude || !isValidLongitude) {
      setError("Invalid manual coordinates");
      setPosition(null);
      return;
    }

    setError(null);
    setPosition({
      latitude,
      longitude,
      accuracy_m: 0,
      timestamp: Date.now(),
      source: "manual"
    });
  }, [
    settings.locationMode,
    settings.manualLatitude,
    settings.manualLongitude,
    setError,
    setPermissionStatus,
    setPollingActive,
    setPosition
  ]);

  useEffect(() => {
    if (settings.locationMode === "gps" && permissionStatus === "manual") {
      setPermissionStatus("unknown");
      setError(null);
    }
  }, [permissionStatus, setError, setPermissionStatus, settings.locationMode]);

  const startWatching = useCallback(() => {
    stopWatching();

    watchLocation(
      {
        highAccuracy: settings.gpsAccuracyMode === "high"
      },
      {
        onUpdate: (position) => {
          setError(null);
          setPermissionStatus("granted");
          setPollingActive(true);
          setPosition(position);
        },
        onError: (error: GeolocationError) => {
          setError(error.message);
          if (error.permissionStatus === "denied") {
            setPermissionStatus("denied");
            setPollingActive(false);
            stopWatching();
          }
          if (error.permissionStatus === "unsupported") {
            setPermissionStatus("unsupported");
            setPollingActive(false);
            stopWatching();
          }
        }
      }
    );

    setPollingActive(true);
  }, [
    setError,
    setPermissionStatus,
    setPollingActive,
    setPosition,
    settings.gpsAccuracyMode
  ]);

  const handleEnableLocation = useCallback(async () => {
    if (!isGeolocationSupported()) {
      setPermissionStatus("unsupported");
      setError("Geolocation is not supported on this device.");
      return;
    }

    setError(null);
    try {
      const position = await requestLocation({
        highAccuracy: settings.gpsAccuracyMode === "high"
      });
      setPosition(position);
      setPermissionStatus("granted");
      startWatching();
    } catch (error) {
      const geoError = error as GeolocationError;
      setError(geoError.message);
      setPermissionStatus(geoError.permissionStatus || "denied");
      setPollingActive(false);
    }
  }, [
    setError,
    setPermissionStatus,
    setPollingActive,
    setPosition,
    settings.gpsAccuracyMode,
    startWatching
  ]);

  useEffect(() => {
    if (settings.locationMode !== "gps") {
      return;
    }

    if (!settings.autoEnableLocationOnDashboard) {
      return;
    }

    if (permissionStatus !== "granted" || pollingActive) {
      return;
    }

    if (!isGeolocationSupported()) {
      setPermissionStatus("unsupported");
      return;
    }

    startWatching();
  }, [
    permissionStatus,
    pollingActive,
    setPermissionStatus,
    settings.autoEnableLocationOnDashboard,
    settings.locationMode,
    startWatching
  ]);

  useEffect(() => {
    return () => {
      stopWatching();
      schedulerRef.current?.stop();
      schedulerRef.current = null;
    };
  }, []);

  const callsigns = useMemo(() => {
    const unique = new Set<string>();
    combinedAircraft.forEach((item) => {
      if (item.callsign) {
        unique.add(item.callsign.toUpperCase());
      }
    });
    return Array.from(unique);
  }, [combinedAircraft]);

  const icao24s = useMemo(() => {
    const unique = new Set<string>();
    combinedAircraft.forEach((item) => {
      if (item.icao24) {
        unique.add(item.icao24.toLowerCase());
      }
    });
    return Array.from(unique);
  }, [combinedAircraft]);

  const groupPayload = useMemo(
    () =>
      groups
        .map((group) => ({
          name: group.name,
          callsigns: fleetAircraft
            .filter((item) => item.groupId === group.id)
            .map((item) => item.callsign)
        }))
        .filter((group) => group.callsigns.length > 0),
    [fleetAircraft, groups]
  );

  const callsignFleetLookup = useMemo(() => {
    const lookup = new Map<string, string>();
    fleetAircraft.forEach((item) => {
      const group = getGroupById(item.groupId);
      lookup.set(item.callsign.toUpperCase(), group?.name || "Ungrouped");
    });
    return lookup;
  }, [fleetAircraft, getGroupById]);

  const nearestAircraft = closestOverall || aircraftRanked[0] || null;
  const closestDistanceLabel = formatDistance(
    nearestAircraft?.distance_km,
    settings.distanceUnit
  );
  const closestFleetLabel = nearestAircraft
    ? callsignFleetLookup.get(nearestAircraft.callsign) || "Ungrouped"
    : "—";
  const hasAircraftResults = aircraftRanked.length > 0;
  const hasFleetResults = fleetDistances.length > 0;

  const fleetResultLookup = useMemo(
    () => new Map(fleetDistances.map((fleet) => [fleet.group_name, fleet])),
    [fleetDistances]
  );

  const rankLookup = useMemo(() => {
    return aircraftRanked.reduce<Record<string, number>>((acc, entry, index) => {
      acc[entry.callsign] = index + 1;
      return acc;
    }, {});
  }, [aircraftRanked]);

  const refreshDistances = useCallback(async () => {
    if (!currentPosition) {
      setDistanceError("User location unavailable");
      return;
    }

    if (fetchInFlightRef.current) {
      return;
    }

    if (callsigns.length === 0 && icao24s.length === 0 && groupPayload.length === 0) {
      setDistanceResults({
        aircraftResults: [],
        fleetResults: [],
        closestOverall: null,
        missing: []
      });
      return;
    }

    fetchInFlightRef.current = true;

    try {
      const [aircraftResponse, fleetResponse] = await Promise.all([
        callsigns.length > 0 || icao24s.length > 0
          ? computeAircraftDistances({
              lat: currentPosition.latitude,
              lon: currentPosition.longitude,
              callsigns,
              icao24s
            })
          : Promise.resolve({ results: [] }),
        groupPayload.length > 0
          ? computeFleetDistances({
              lat: currentPosition.latitude,
              lon: currentPosition.longitude,
              fleets: groupPayload
            })
          : Promise.resolve({ fleets: [] as FleetProximityResult[] })
      ]);

      const aircraftResults = aircraftResponse.results || [];
      const distanceMapNext: Record<string, number> = {};
      aircraftResults.forEach((entry) => {
        distanceMapNext[entry.callsign.toUpperCase()] = entry.distance_km;
        if (entry.icao24) {
          distanceMapNext[entry.icao24.toLowerCase()] = entry.distance_km;
        }
      });

      setDistanceMap(distanceMapNext);
      if (isDebug) {
        console.log("DISTANCE MAP:", distanceMapNext);
      }

      const resultKeys = new Set<string>();
      aircraftResults.forEach((entry) => {
        resultKeys.add(entry.callsign.toUpperCase());
        if (entry.icao24) {
          resultKeys.add(entry.icao24.toLowerCase());
        }
      });

      const missing = [...callsigns, ...icao24s].filter((identifier) => {
        const upper = identifier.toUpperCase();
        const lower = identifier.toLowerCase();
        return !resultKeys.has(upper) && !resultKeys.has(lower);
      });
      const closest = aircraftResults.length > 0 ? aircraftResults[0] : null;

      setDistanceResults({
        aircraftResults,
        fleetResults: fleetResponse.fleets,
        closestOverall: closest,
        missing
      });
    } catch (error) {
      setDistanceError((error as Error).message);
    } finally {
      fetchInFlightRef.current = false;
    }
  }, [
    callsigns,
    icao24s,
    currentPosition,
    groupPayload,
    setDistanceError,
    setDistanceResults
  ]);

  useEffect(() => {
    if (isDebug) {
      console.log("User location:", currentPosition);
    }
    if (currentPosition) {
      refreshDistances();
    }
  }, [currentPosition, refreshDistances]);

  useEffect(() => {
    if (isDebug) {
      console.log("Distances updated", distanceMap);
    }
  }, [distanceMap]);

  useEffect(() => {
    if (!currentPosition) {
      schedulerRef.current?.stop();
      schedulerRef.current = null;
      return;
    }

    if (!schedulerRef.current) {
      schedulerRef.current = createDistanceScheduler(
        {
          intervalMs: Math.max(settings.distanceUpdateIntervalSec, 5) * 1000,
          debounceMs: 5000
        },
        refreshDistances
      );
      schedulerRef.current.start();
    }

    if (settings.autoRefreshOnMovement) {
      schedulerRef.current.trigger();
    }

    return () => undefined;
  }, [
    currentPosition,
    refreshDistances,
    settings.autoRefreshOnMovement,
    settings.distanceUpdateIntervalSec
  ]);

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
            Track multiple aircraft in one place. Distances now use live
            OpenSky positions.
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
          {settings.locationMode !== "manual" &&
          permissionStatus !== "granted" ? (
            <button
              className="mt-3 inline-flex w-full items-center justify-center rounded-full border border-white/20 px-4 py-2 text-xs uppercase tracking-[0.2em] text-slate-200 transition hover:border-white/40 disabled:cursor-not-allowed disabled:opacity-50"
              onClick={handleEnableLocation}
              disabled={permissionStatus === "unsupported"}
            >
              Enable Location
            </button>
          ) : null}
          <div className="mt-3 space-y-2 text-xs text-slate-300">
            <div className="flex items-center justify-between">
              <span>Status</span>
              <span className="text-slate-100">{locationStatus.label}</span>
            </div>
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
                {currentPosition
                  ? `± ${Math.round(currentPosition.accuracy_m)} m`
                  : "—"}
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

      <div className="mt-6 rounded-3xl border border-white/10 bg-white/5 p-5 shadow-glow backdrop-blur">
        <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
          Closest Aircraft To You
        </p>
        {closestOverall ? (
          <div className="mt-3 text-sm text-slate-200">
            <p className="text-lg font-semibold text-white">
              {nearestAircraft?.callsign}
            </p>
            <div className="mt-2 flex flex-wrap gap-4 text-xs text-slate-300">
              <span>Distance: {closestDistanceLabel}</span>
              <span>Fleet: {closestFleetLabel}</span>
              <span>
                Altitude:{" "}
                {nearestAircraft
                  ? Math.round(nearestAircraft.altitude_m * 3.28084)
                  : "—"} ft
              </span>
            </div>
            <span className="mt-3 inline-flex rounded-full border border-emerald-400/40 px-2 py-0.5 text-[10px] uppercase tracking-[0.25em] text-emerald-200">
              Live OpenSky
            </span>
          </div>
        ) : (
          <p className="mt-3 text-sm text-slate-400">
            No distance data yet. Enable location to calculate proximity.
          </p>
        )}
      </div>

      {distanceError ? (
        <p className="mt-4 text-xs text-rose-200">{distanceError}</p>
      ) : null}
      {!distanceError && currentPosition && (callsigns.length > 0 || icao24s.length > 0) && !hasAircraftResults ? (
        <p className="mt-4 text-xs text-amber-200">
          No OpenSky positions found for the tracked identifiers.
        </p>
      ) : null}
      {!distanceError && currentPosition && groups.length > 0 && !hasFleetResults ? (
        <p className="mt-2 text-xs text-amber-200">
          No fleet distance results available yet.
        </p>
      ) : null}

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

      {groups.length > 0 ? (
        <div className="mt-6 grid gap-4 lg:grid-cols-2">
          {groups.map((group) => {
            const result = fleetResultLookup.get(group.name);
            const closest = result?.closest_aircraft || null;
            const distanceLabel = formatDistance(
              closest?.distance_km,
              settings.distanceUnit
            );
            const isExpanded = Boolean(expandedFleets[group.id]);

            return (
              <div
                key={group.id}
                className="rounded-3xl border border-white/10 bg-white/5 p-5 shadow-glow backdrop-blur"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
                      {group.name}
                    </p>
                    <h3 className="mt-2 text-lg font-semibold text-white">
                      Closest aircraft
                    </h3>
                  </div>
                  <span
                    className="rounded-full px-3 py-1 text-xs"
                    style={{
                      backgroundColor: `${group.color}22`,
                      color: group.color,
                      border: `1px solid ${group.color}55`
                    }}
                  >
                    {group.icon}
                  </span>
                </div>
                <div className="mt-4 text-sm text-slate-200">
                  <p>
                    Closest Aircraft: {closest?.callsign || "—"}
                  </p>
                  <p className="mt-1 text-slate-300">Distance: {distanceLabel}</p>
                </div>
                <div className="mt-4 border-t border-white/10 pt-4 text-sm text-slate-300">
                  <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
                    Fleet Members
                  </p>
                  {result && result.members_ranked.length > 0 ? (
                    <div className="mt-3">
                      <button
                        className="text-xs uppercase tracking-[0.3em] text-cyan-200"
                        onClick={() =>
                          setExpandedFleets((prev) => ({
                            ...prev,
                            [group.id]: !prev[group.id]
                          }))
                        }
                        aria-expanded={isExpanded}
                      >
                        {isExpanded ? "Hide list" : "Show list"}
                      </button>
                      {isExpanded ? (
                        <ol className="mt-3 space-y-2">
                          {result.members_ranked.map((entry, index) => (
                            <li
                              key={entry.callsign}
                              className={`flex justify-between ${
                                index === 0 ? "text-emerald-200" : "text-slate-300"
                              }`}
                            >
                              <span>
                                {index + 1}. {entry.callsign}
                              </span>
                              <span>
                                {formatDistance(
                                  entry.distance_km,
                                  settings.distanceUnit
                                )}
                              </span>
                            </li>
                          ))}
                        </ol>
                      ) : null}
                    </div>
                  ) : (
                    <p className="mt-3 text-xs text-slate-500">
                      No distance data yet.
                    </p>
                  )}
                </div>
              </div>
            );
          })}
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
              const telemetryState = telemetry[item.id];
              const resolvedCallsign =
                item.callsign || telemetryState?.data?.callsign || null;
              const normalizedCallsign = resolvedCallsign
                ? resolvedCallsign.toUpperCase()
                : null;
              const resolvedIcao24 =
                item.icao24 || telemetryState?.data?.icao24 || null;
              const normalizedIcao24 = resolvedIcao24
                ? resolvedIcao24.toLowerCase()
                : null;
              const distanceData: DistanceResult | undefined =
                normalizedCallsign
                  ? aircraftDistances[normalizedCallsign]
                  : normalizedIcao24
                  ? aircraftDistances[normalizedIcao24]
                  : undefined;
              const distanceKm = normalizedCallsign
                ? distanceMap[normalizedCallsign]
                : normalizedIcao24
                ? distanceMap[normalizedIcao24]
                : undefined;
              const isMissing = Boolean(
                (normalizedCallsign &&
                  missingCallsigns.includes(normalizedCallsign)) ||
                  (normalizedIcao24 &&
                    missingCallsigns.includes(normalizedIcao24))
              );
              const hasComputed = Boolean(lastComputedAt);
              const status = telemetryState?.status
                ? telemetryState.status
                : !normalizedCallsign && !normalizedIcao24
                ? "offline"
                : distanceData
                ? "live"
                : isMissing
                ? "offline"
                : distanceError
                ? "offline"
                : hasComputed
                ? "offline"
                : currentPosition
                ? "loading"
                : "offline";
              const errorMessage = telemetryState?.errorMessage
                ? telemetryState.errorMessage
                : !normalizedCallsign && !normalizedIcao24
                ? "Callsign or ICAO24 required for OpenSky distance"
                : isMissing
                ? "No OpenSky data for this identifier"
                : distanceError
                ? distanceError
                : hasComputed
                ? "No distance data available"
                : undefined;
              const rank = normalizedCallsign
                ? rankLookup[normalizedCallsign]
                : distanceData
                ? rankLookup[distanceData.callsign]
                : undefined;

              return (
                <AircraftCard
                  key={item.id}
                  aircraft={item}
                  onRemove={() =>
                    item.source === "bulk"
                      ? removeFleetAircraft(item.id)
                      : removeAircraft(item.id)
                  }
                  telemetry={telemetryState?.data}
                  distanceData={distanceData}
                  distanceKm={distanceKm}
                  showDebug={isDebug}
                  distanceUnit={settings.distanceUnit}
                  rank={rank}
                  dataSourceLabel={distanceData ? "Live OpenSky" : undefined}
                  status={status}
                  errorMessage={errorMessage}
                  groupLabel={group?.name}
                  groupColor={group?.color}
                />
              );
            })}
          </div>
        )}
      </div>

      {isDebug ? (
        <div className="fixed bottom-4 right-4 z-50 w-64 max-h-64 overflow-auto rounded-2xl border border-yellow-400/40 bg-slate-950/90 p-3 text-xs text-yellow-200 shadow-lg">
          <p className="text-[10px] uppercase tracking-[0.3em] text-yellow-200/80">
            Debug distances
          </p>
          {Object.entries(distanceMap).length > 0 ? (
            <div className="mt-2 space-y-1">
              {Object.entries(distanceMap).map(([key, value]) => (
                <div key={key} className="flex items-center justify-between">
                  <span className="truncate pr-2">{key}</span>
                  <span>{value} km</span>
                </div>
              ))}
            </div>
          ) : (
            <p className="mt-2 text-yellow-200/70">No distance data</p>
          )}
        </div>
      ) : null}

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
