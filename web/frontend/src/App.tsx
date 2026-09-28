import { useEffect, useState } from "react";
import { Route, Routes } from "react-router-dom";
import Navbar from "./components/Navbar";
import Monitoring from "./pages/Monitoring";
import Settings from "./pages/Settings";
import { useAircraftStore } from "./store/aircraftStore";
import { getAppState, saveAppState } from "./services/api";
import { useFleetStore } from "./store/fleetStore";

const App = () => {
  const theme = useAircraftStore((state) => state.ui.theme);
  const settings = useAircraftStore((state) => state.settings);
  const ui = useAircraftStore((state) => state.ui);
  const aircraft = useAircraftStore((state) => state.aircraft);
  const setAircraft = useAircraftStore((state) => state.setAircraft);
  const setSettings = useAircraftStore((state) => state.setSettings);
  const setUi = useAircraftStore((state) => state.setUi);
  const fleetGroups = useFleetStore((state) => state.groups);
  const fleetAircraft = useFleetStore((state) => state.fleetAircraft);
  const setFleetGroups = useFleetStore((state) => state.setGroups);
  const setFleetAircraft = useFleetStore((state) => state.setFleetAircraft);
  const [isHydrated, setIsHydrated] = useState(false);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === "dark") {
      root.classList.add("dark");
    } else {
      root.classList.remove("dark");
    }
  }, [theme]);

  useEffect(() => {
    let isActive = true;

    getAppState()
      .then((state) => {
        if (!isActive) {
          return;
        }
        setSettings(state.settings);
        setUi(state.ui);
        setAircraft(state.aircraft);
        setFleetGroups(state.fleet.groups);
        setFleetAircraft(state.fleet.fleetAircraft);
        setIsHydrated(true);
      })
      .catch(() => {
        if (!isActive) {
          return;
        }
        setIsHydrated(true);
      });

    return () => {
      isActive = false;
    };
  }, [setAircraft, setFleetAircraft, setFleetGroups, setSettings, setUi]);

  useEffect(() => {
    if (!isHydrated) {
      return;
    }

    const timeout = setTimeout(() => {
      saveAppState({
        settings,
        ui,
        aircraft,
        fleet: {
          groups: fleetGroups,
          fleetAircraft
        }
      }).catch(() => undefined);
    }, 800);

    return () => clearTimeout(timeout);
  }, [aircraft, fleetAircraft, fleetGroups, isHydrated, settings, ui]);

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
