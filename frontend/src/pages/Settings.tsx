import { ChangeEvent, useEffect, useState } from "react";
import { useAircraftStore } from "../store/aircraftStore";
import { updateApiSettings } from "../services/api";

const Settings = () => {
  const { settings, ui, updateSettings, updateUi } = useAircraftStore();
  const [apiSyncStatus, setApiSyncStatus] = useState<
    "idle" | "saving" | "saved" | "error"
  >("idle");

  const handleNumberChange = (
    key:
      | "refreshIntervalSec"
      | "maxTrackedWarning"
      | "gpsPollingIntervalSec"
  ) =>
    (event: ChangeEvent<HTMLInputElement>) => {
      updateSettings({ [key]: Number(event.target.value) });
    };

  useEffect(() => {
    const timeout = setTimeout(async () => {
      try {
        setApiSyncStatus("saving");
        await updateApiSettings({
          baseUrl: settings.apiBaseUrl,
          authUrl: settings.apiAuthUrl,
          username: settings.apiUsername,
          password: settings.apiPassword,
          clientId: settings.apiClientId,
          clientSecret: settings.apiClientSecret
        });
        setApiSyncStatus("saved");
      } catch (_error) {
        setApiSyncStatus("error");
      }
    }, 600);

    return () => clearTimeout(timeout);
  }, [
    settings.apiBaseUrl,
    settings.apiAuthUrl,
    settings.apiUsername,
    settings.apiPassword,
    settings.apiClientId,
    settings.apiClientSecret
  ]);

  return (
    <section className="pt-8">
      <div>
        <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
          Settings
        </p>
        <h1 className="mt-2 text-3xl font-semibold text-white">
          Flight Control Center
        </h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-300">
          Configure tracking cadence, location sources, API credentials, and UI
          preferences. All settings persist locally.
        </p>
      </div>

      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <div className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur">
          <h2 className="text-lg font-semibold text-white">Tracking</h2>
          <div className="mt-4 space-y-4 text-sm text-slate-200">
            <label className="flex flex-col gap-2">
              Refresh interval (seconds)
              <input
                type="number"
                min={5}
                value={settings.refreshIntervalSec}
                onChange={handleNumberChange("refreshIntervalSec")}
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              />
            </label>
            <label className="flex flex-col gap-2">
              Distance unit
              <select
                value={settings.distanceUnit}
                onChange={(event) =>
                  updateSettings({
                    distanceUnit: event.target.value as "km" | "mi"
                  })
                }
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              >
                <option value="km">Kilometers</option>
                <option value="mi">Miles</option>
              </select>
            </label>
            <label className="flex flex-col gap-2">
              Max tracked aircraft warning threshold
              <input
                type="number"
                min={1}
                value={settings.maxTrackedWarning}
                onChange={handleNumberChange("maxTrackedWarning")}
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              />
            </label>
          </div>
        </div>

        <div className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur">
          <h2 className="text-lg font-semibold text-white">Location</h2>
          <div className="mt-4 space-y-4 text-sm text-slate-200">
            <label className="flex flex-col gap-2">
              Location source
              <select
                value={settings.locationMode}
                onChange={(event) =>
                  updateSettings({
                    locationMode: event.target.value as "gps" | "manual"
                  })
                }
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              >
                <option value="gps">GPS (future)</option>
                <option value="manual">Manual override</option>
              </select>
            </label>
            <label className="flex flex-col gap-2">
              GPS polling interval (seconds)
              <input
                type="number"
                min={10}
                value={settings.gpsPollingIntervalSec}
                onChange={handleNumberChange("gpsPollingIntervalSec")}
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              />
            </label>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="flex flex-col gap-2">
                Manual latitude
                <input
                  type="text"
                  value={settings.manualLatitude}
                  onChange={(event) =>
                    updateSettings({ manualLatitude: event.target.value })
                  }
                  className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                />
              </label>
              <label className="flex flex-col gap-2">
                Manual longitude
                <input
                  type="text"
                  value={settings.manualLongitude}
                  onChange={(event) =>
                    updateSettings({ manualLongitude: event.target.value })
                  }
                  className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                />
              </label>
            </div>
            <label className="flex flex-col gap-2">
              Accuracy vs battery balance
              <input
                type="range"
                min={0}
                max={100}
                value={settings.accuracyBatteryBalance}
                onChange={(event) =>
                  updateSettings({
                    accuracyBatteryBalance: Number(event.target.value)
                  })
                }
              />
              <div className="flex justify-between text-xs text-slate-400">
                <span>Battery saver</span>
                <span>{settings.accuracyBatteryBalance}% accuracy</span>
                <span>High accuracy</span>
              </div>
            </label>
          </div>
        </div>

        <div className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur">
          <h2 className="text-lg font-semibold text-white">API</h2>
          <div className="mt-4 space-y-4 text-sm text-slate-200">
            <label className="flex flex-col gap-2">
              API base URL
              <input
                type="text"
                value={settings.apiBaseUrl}
                onChange={(event) =>
                  updateSettings({ apiBaseUrl: event.target.value })
                }
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              />
            </label>
            <label className="flex flex-col gap-2">
              OAuth2 token URL
              <input
                type="text"
                value={settings.apiAuthUrl}
                onChange={(event) =>
                  updateSettings({ apiAuthUrl: event.target.value })
                }
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              />
            </label>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="flex flex-col gap-2">
                API client ID
                <input
                  type="text"
                  value={settings.apiClientId}
                  onChange={(event) =>
                    updateSettings({ apiClientId: event.target.value })
                  }
                  className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                />
              </label>
              <label className="flex flex-col gap-2">
                API client secret
                <input
                  type="password"
                  value={settings.apiClientSecret}
                  onChange={(event) =>
                    updateSettings({ apiClientSecret: event.target.value })
                  }
                  className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                />
              </label>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="flex flex-col gap-2">
                Username
                <input
                  type="text"
                  value={settings.apiUsername}
                  onChange={(event) =>
                    updateSettings({ apiUsername: event.target.value })
                  }
                  className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                />
              </label>
              <label className="flex flex-col gap-2">
                Password
                <input
                  type="password"
                  value={settings.apiPassword}
                  onChange={(event) =>
                    updateSettings({ apiPassword: event.target.value })
                  }
                  className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                />
              </label>
            </div>
            <p className="text-xs text-slate-400">
              API client credentials are preferred. Username/password is legacy
              fallback.
            </p>
            <div className="rounded-2xl border border-white/10 bg-slate-900/60 px-4 py-3 text-xs text-slate-400">
              Rate limit: — requests/minute (placeholder)
            </div>
            <div className="text-xs text-slate-400">
              {apiSyncStatus === "saving" && "Syncing API settings..."}
              {apiSyncStatus === "saved" && "API settings synced."}
              {apiSyncStatus === "error" && "Failed to sync API settings."}
            </div>
          </div>
        </div>

        <div className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur">
          <h2 className="text-lg font-semibold text-white">UI</h2>
          <div className="mt-4 space-y-4 text-sm text-slate-200">
            <label className="flex flex-col gap-2">
              Theme
              <select
                value={ui.theme}
                onChange={(event) =>
                  updateUi({ theme: event.target.value as "dark" | "light" })
                }
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              >
                <option value="dark">Dark</option>
                <option value="light">Light</option>
              </select>
            </label>
            <label className="flex flex-col gap-2">
              Card density
              <select
                value={ui.cardDensity}
                onChange={(event) =>
                  updateUi({
                    cardDensity: event.target.value as
                      | "comfortable"
                      | "compact"
                  })
                }
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              >
                <option value="comfortable">Comfortable</option>
                <option value="compact">Compact</option>
              </select>
            </label>
            <label className="flex flex-col gap-2">
              Time format
              <select
                value={ui.timeFormat}
                onChange={(event) =>
                  updateUi({ timeFormat: event.target.value as "24h" | "12h" })
                }
                className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
              >
                <option value="24h">24-hour</option>
                <option value="12h">12-hour</option>
              </select>
            </label>
          </div>
          <div className="mt-6 flex items-center justify-between text-xs text-slate-400">
            <span>Settings auto-save locally.</span>
            <span>Last sync: just now</span>
          </div>
        </div>
      </div>
    </section>
  );
};

export default Settings;
