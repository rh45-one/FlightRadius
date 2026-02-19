import { create } from "zustand";
import { persist } from "zustand/middleware";
import { PositionData, PermissionStatus } from "../services/geolocation";

type LocationState = {
  currentPosition: PositionData | null;
  permissionStatus: PermissionStatus;
  pollingActive: boolean;
  lastUpdated: number | null;
  errorState: string | null;
  setPosition: (position: PositionData | null) => void;
  setPermissionStatus: (status: PermissionStatus) => void;
  setPollingActive: (active: boolean) => void;
  setError: (message: string | null) => void;
};

export const useLocationStore = create<LocationState>()(
  persist(
    (set) => ({
      currentPosition: null,
      permissionStatus: "unknown",
      pollingActive: false,
      lastUpdated: null,
      errorState: null,
      setPosition: (position) =>
        set(() => ({
          currentPosition: position,
          lastUpdated: position ? position.timestamp : null
        })),
      setPermissionStatus: (status) =>
        set(() => ({ permissionStatus: status })),
      setPollingActive: (active) => set(() => ({ pollingActive: active })),
      setError: (message) => set(() => ({ errorState: message }))
    }),
    {
      name: "flightRadius.location",
      partialize: (state) => ({
        currentPosition: state.currentPosition,
        permissionStatus: state.permissionStatus,
        lastUpdated: state.lastUpdated
      })
    }
  )
);
