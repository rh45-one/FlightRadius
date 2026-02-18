import { useEffect } from "react";
import { Route, Routes } from "react-router-dom";
import Navbar from "./components/Navbar";
import Monitoring from "./pages/Monitoring";
import Settings from "./pages/Settings";
import { useAircraftStore } from "./store/aircraftStore";
import { useLocationStore } from "./store/locationStore";
import { postUserLocation } from "./services/api";
import { setMockLocation, startPolling } from "./services/geolocation";

const App = () => {
  const theme = useAircraftStore((state) => state.ui.theme);
  const settings = useAircraftStore((state) => state.settings);
  const {
    currentPosition,
    setPosition,
    setPermissionStatus,
    setPollingActive,
    setError
  } = useLocationStore();

  useEffect(() => {
    const root = document.documentElement;
    if (theme === "dark") {
      root.classList.add("dark");
    } else {
      root.classList.remove("dark");
    }
  }, [theme]);

  useEffect(() => {
    if (import.meta.env.DEV) {
      window.__setMockLocation = (latitude: number, longitude: number) => {
        setMockLocation(latitude, longitude);
      };
    }
  }, []);

  useEffect(() => {
    let stopPolling = () => undefined;

    if (settings.locationMode === "manual") {
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
        return () => undefined;
      }

      setError(null);
      setPosition({
        latitude,
        longitude,
        accuracy_m: 0,
        timestamp: Date.now(),
        source: "manual"
      });

      return () => undefined;
    }

    setPermissionStatus("prompt");
    setPollingActive(true);

    stopPolling = startPolling(
      {
        intervalMs: Math.max(settings.gpsPollingIntervalSec, 5) * 1000,
        highAccuracy: settings.gpsAccuracyMode === "high",
        timeoutMs: 8000
      },
      {
        onUpdate: (position) => {
          setError(null);
          setPermissionStatus("granted");
          setPollingActive(true);
          setPosition(position);
        },
        onError: (error) => {
          setError(error.message);
          setPermissionStatus(error.permissionStatus);
          if (error.permissionStatus === "denied") {
            setPollingActive(false);
          }
          if (error.permissionStatus === "unsupported") {
            setPollingActive(false);
          }
        }
      }
    );

    return () => stopPolling();
  }, [
    settings.locationMode,
    settings.gpsPollingIntervalSec,
    settings.gpsAccuracyMode,
    settings.manualLatitude,
    settings.manualLongitude,
    setError,
    setPermissionStatus,
    setPollingActive,
    setPosition
  ]);

  useEffect(() => {
    if (!currentPosition) {
      return;
    }

    postUserLocation(currentPosition).catch(() => undefined);
  }, [currentPosition]);

  return (
    <div className="relative min-h-screen overflow-hidden bg-slate-950 text-slate-100">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute -top-40 left-1/2 h-96 w-[48rem] -translate-x-1/2 rounded-full bg-gradient-to-r from-cyan-500/20 via-indigo-500/20 to-slate-900 blur-3xl" />
        <div className="absolute bottom-0 right-0 h-72 w-72 rounded-full bg-gradient-to-br from-fuchsia-500/10 to-transparent blur-3xl" />
      </div>
      <div className="relative z-10">
        <Navbar />
        <main className="mx-auto max-w-6xl px-4 pb-16 sm:px-6 lg:px-8">
          <Routes>
            <Route path="/" element={<Monitoring />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};

export default App;
