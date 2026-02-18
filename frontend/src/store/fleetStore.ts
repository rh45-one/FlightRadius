import { create } from "zustand";

type FleetGroup = {
  id: string;
  name: string;
  description?: string;
  color: string;
  icon: string;
};

type FleetAircraft = {
  id: string;
  callsign: string;
  groupId?: string;
  createdAt: string;
};

type FleetState = {
  groups: FleetGroup[];
  fleetAircraft: FleetAircraft[];
  addGroup: (group: Omit<FleetGroup, "id">) => string;
  addFleetAircraft: (callsigns: string[], groupId?: string) => void;
  removeFleetAircraft: (id: string) => void;
  getGroupById: (id?: string) => FleetGroup | undefined;
  setGroups: (groups: FleetGroup[]) => void;
  setFleetAircraft: (fleetAircraft: FleetAircraft[]) => void;
};

const createId = () => {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

export const useFleetStore = create<FleetState>((set, get) => ({
  groups: [],
  fleetAircraft: [],
  addGroup: (group) => {
    const id = createId();
    set((state) => ({
      groups: [
        {
          ...group,
          id
        },
        ...state.groups
      ]
    }));
    return id;
  },
  addFleetAircraft: (callsigns, groupId) => {
    const timestamp = new Date().toISOString();
    set((state) => ({
      fleetAircraft: [
        ...callsigns.map((callsign) => ({
          id: createId(),
          callsign,
          groupId,
          createdAt: timestamp
        })),
        ...state.fleetAircraft
      ]
    }));
  },
  removeFleetAircraft: (id) =>
    set((state) => ({
      fleetAircraft: state.fleetAircraft.filter((item) => item.id !== id)
    })),
  getGroupById: (id) => get().groups.find((group) => group.id === id),
  setGroups: (groups) => set(() => ({ groups })),
  setFleetAircraft: (fleetAircraft) => set(() => ({ fleetAircraft }))
}));

export type { FleetAircraft, FleetGroup };
