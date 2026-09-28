import { NavLink } from "react-router-dom";

const Navbar = () => {
  const navBase =
    "rounded-full px-4 py-2 text-xs uppercase tracking-[0.2em] transition";

  return (
    <header className="sticky top-0 z-20 border-b border-white/10 bg-slate-950/70 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
        <div>
          <p className="text-xs uppercase tracking-[0.35em] text-slate-400">
            Aircraft Monitor
          </p>
          <h1 className="text-lg font-semibold text-white">FlightRadius</h1>
        </div>
        <nav className="flex items-center gap-2 text-slate-300">
          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              `${navBase} ${
                isActive
                  ? "bg-white/10 text-white"
                  : "hover:bg-white/5"
              }`
            }
          >
            Monitoring
          </NavLink>
          <NavLink
            to="/settings"
            className={({ isActive }) =>
              `${navBase} ${
                isActive
                  ? "bg-white/10 text-white"
                  : "hover:bg-white/5"
              }`
            }
          >
            Settings
          </NavLink>
        </nav>
      </div>
    </header>
  );
};

export default Navbar;
