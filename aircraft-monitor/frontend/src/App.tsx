import { useEffect } from "react";
import { Route, Routes } from "react-router-dom";
import Navbar from "./components/Navbar";
import Monitoring from "./pages/Monitoring";
import Settings from "./pages/Settings";
import { useAircraftStore } from "./store/aircraftStore";

const App = () => {
  const theme = useAircraftStore((state) => state.ui.theme);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === "dark") {
      root.classList.add("dark");
    } else {
      root.classList.remove("dark");
    }
  }, [theme]);

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
