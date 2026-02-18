import { useState } from "react";
import { Aircraft } from "../store/aircraftStore";

type AddAircraftModalProps = {
  isOpen: boolean;
  onClose: () => void;
  onSave: (payload: Omit<Aircraft, "id" | "createdAt">) => void;
};

const AddAircraftModal = ({ isOpen, onClose, onSave }: AddAircraftModalProps) => {
  const [icao24, setIcao24] = useState("");
  const [callsign, setCallsign] = useState("");
  const [notes, setNotes] = useState("");

  if (!isOpen) {
    return null;
  }

  const handleSave = () => {
    if (!icao24.trim()) {
      return;
    }

    onSave({
      icao24: icao24.trim().toLowerCase(),
      callsign: callsign.trim() || undefined,
      notes: notes.trim() || undefined
    });

    setIcao24("");
    setCallsign("");
    setNotes("");
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4">
      <div className="w-full max-w-lg rounded-3xl border border-white/10 bg-slate-900/80 p-6 shadow-soft backdrop-blur">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-white">Add aircraft</h2>
          <button
            onClick={onClose}
            className="rounded-full border border-white/10 px-3 py-1 text-xs text-slate-300 transition hover:border-white/30"
          >
            Close
          </button>
        </div>
        <div className="mt-5 space-y-4 text-sm text-slate-200">
          <label className="flex flex-col gap-2">
            ICAO24 hex
            <input
              type="text"
              value={icao24}
              onChange={(event) => setIcao24(event.target.value)}
              className="rounded-xl border border-white/10 bg-slate-950/80 px-3 py-2"
              placeholder="e.g. a1b2c3"
            />
          </label>
          <label className="flex flex-col gap-2">
            Callsign (optional)
            <input
              type="text"
              value={callsign}
              onChange={(event) => setCallsign(event.target.value)}
              className="rounded-xl border border-white/10 bg-slate-950/80 px-3 py-2"
            />
          </label>
          <label className="flex flex-col gap-2">
            Notes (optional)
            <textarea
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              rows={3}
              className="rounded-xl border border-white/10 bg-slate-950/80 px-3 py-2"
            />
          </label>
        </div>
        <div className="mt-6 flex justify-end gap-3">
          <button
            onClick={onClose}
            className="rounded-full border border-white/10 px-4 py-2 text-sm text-slate-200"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="rounded-full bg-gradient-to-r from-cyan-400 to-blue-500 px-4 py-2 text-sm font-semibold text-slate-900"
          >
            Save
          </button>
        </div>
      </div>
    </div>
  );
};

export default AddAircraftModal;
