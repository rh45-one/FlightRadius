import { useEffect, useMemo, useState } from "react";
import { validateCallsigns } from "../services/api";
import { useFleetStore } from "../store/fleetStore";

type BulkAddAircraftModalProps = {
  isOpen: boolean;
  onClose: () => void;
};

type TabKey = "paste" | "upload" | "template";

type ValidationState = "valid" | "no-data" | "invalid" | "checking";

type CallsignEntry = {
  callsign: string;
  status: ValidationState;
};

const colorOptions = [
  "#22d3ee",
  "#38bdf8",
  "#a855f7",
  "#f97316",
  "#34d399",
  "#f43f5e"
];

const iconOptions = ["plane", "cargo", "military", "training", "vip"];

const parseCallsigns = (value: string) => {
  return value
    .split(/[\s,]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
    .map((item) => item.toUpperCase());
};

const isValidFormat = (callsign: string) => /^[A-Z0-9]{2,8}$/.test(callsign);

const BulkAddAircraftModal = ({ isOpen, onClose }: BulkAddAircraftModalProps) => {
  const { groups, addGroup, addFleetAircraft } = useFleetStore();
  const [activeTab, setActiveTab] = useState<TabKey>("paste");
  const [rawInput, setRawInput] = useState("");
  const [fileContent, setFileContent] = useState("");
  const [csvHeader, setCsvHeader] = useState<string[]>([]);
  const [selectedColumn, setSelectedColumn] = useState("");
  const [entries, setEntries] = useState<CallsignEntry[]>([]);
  const [validationMessage, setValidationMessage] = useState<string | null>(
    null
  );
  const [groupMode, setGroupMode] = useState<
    "new" | "existing" | "ungrouped"
  >("ungrouped");
  const [groupName, setGroupName] = useState("");
  const [groupDescription, setGroupDescription] = useState("");
  const [groupColor, setGroupColor] = useState(colorOptions[0]);
  const [groupIcon, setGroupIcon] = useState(iconOptions[0]);
  const [existingGroupId, setExistingGroupId] = useState<string | undefined>(
    groups[0]?.id
  );

  const parsedCallsigns = useMemo(() => {
    if (activeTab === "upload" && fileContent) {
      if (csvHeader.length > 0 && selectedColumn) {
        const columnIndex = csvHeader.indexOf(selectedColumn);
        const rows = fileContent.split(/\r?\n/).slice(1);
        return rows
          .map((row) => row.split(",")[columnIndex] || "")
          .flatMap((value) => parseCallsigns(value));
      }
      return parseCallsigns(fileContent);
    }
    return parseCallsigns(rawInput);
  }, [activeTab, rawInput, fileContent, csvHeader, selectedColumn]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const nextEntries = parsedCallsigns.map((callsign) => ({
      callsign,
      status: isValidFormat(callsign) ? "checking" : "invalid"
    }));
    setEntries(nextEntries);
    setValidationMessage(null);

    if (nextEntries.length === 0) {
      return;
    }

    const valid = nextEntries.filter((entry) => entry.status !== "invalid");
    if (valid.length === 0) {
      return;
    }

    if (valid.length > 200) {
      setValidationMessage("Too many callsigns to validate at once.");
      setEntries((prev) =>
        prev.map((entry) =>
          entry.status === "checking"
            ? { ...entry, status: "no-data" }
            : entry
        )
      );
      return;
    }

    const timeout = setTimeout(async () => {
      try {
        const result = await validateCallsigns(
          valid.map((entry) => entry.callsign)
        );
        const lookup = new Map(
          result.results.map((item) => [item.callsign, item.status])
        );

        setEntries((prev) =>
          prev.map((entry) => {
            if (entry.status === "invalid") {
              return entry;
            }
            const status = lookup.get(entry.callsign) || "no-data";
            return { ...entry, status };
          })
        );
      } catch (error) {
        setValidationMessage((error as Error).message);
        setEntries((prev) =>
          prev.map((entry) =>
            entry.status === "checking"
              ? { ...entry, status: "no-data" }
              : entry
          )
        );
      }
    }, 600);

    return () => clearTimeout(timeout);
  }, [parsedCallsigns, isOpen]);

  useEffect(() => {
    if (!isOpen) {
      setRawInput("");
      setFileContent("");
      setCsvHeader([]);
      setSelectedColumn("");
      setEntries([]);
      setValidationMessage(null);
      setGroupMode("ungrouped");
      setGroupName("");
      setGroupDescription("");
      setGroupColor(colorOptions[0]);
      setGroupIcon(iconOptions[0]);
    }
  }, [isOpen]);

  const handleFileUpload = (file: File | null) => {
    if (!file) {
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const text = String(reader.result || "");
      setFileContent(text);

      const firstLine = text.split(/\r?\n/)[0] || "";
      if (firstLine.includes(",")) {
        const header = firstLine.split(",").map((item) => item.trim());
        setCsvHeader(header);
        setSelectedColumn(header[0] || "");
      } else {
        setCsvHeader([]);
        setSelectedColumn("");
      }
    };
    reader.readAsText(file);
  };

  const validEntries = entries.filter((entry) => entry.status !== "invalid");
  const validCallsigns = validEntries.map((entry) => entry.callsign);
  const disabledSave = validCallsigns.length === 0;

  const statusColor = (status: ValidationState) => {
    if (status === "valid") {
      return "text-emerald-300";
    }
    if (status === "no-data") {
      return "text-amber-300";
    }
    if (status === "invalid") {
      return "text-rose-300";
    }
    return "text-slate-400";
  };

  const handleSave = () => {
    if (disabledSave) {
      return;
    }

    let groupId: string | undefined;
    if (groupMode === "new" && groupName.trim()) {
      groupId = addGroup({
        name: groupName.trim(),
        description: groupDescription.trim() || undefined,
        color: groupColor,
        icon: groupIcon
      });
    }

    if (groupMode === "existing") {
      groupId = existingGroupId;
    }

    addFleetAircraft(validCallsigns, groupId);
    onClose();
  };

  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4">
      <div className="w-full max-w-5xl rounded-3xl border border-white/10 bg-slate-900/80 p-6 shadow-soft backdrop-blur">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-white">Bulk add aircraft</h2>
          <button
            onClick={onClose}
            className="rounded-full border border-white/10 px-3 py-1 text-xs text-slate-300 transition hover:border-white/30"
          >
            Close
          </button>
        </div>

        <div className="mt-6 grid gap-6 lg:grid-cols-[1.2fr_1fr]">
          <div className="space-y-4">
            <div className="flex gap-2 text-xs">
              {([
                { key: "paste", label: "Paste" },
                { key: "upload", label: "Upload" },
                { key: "template", label: "From Group Template" }
              ] as { key: TabKey; label: string }[]).map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  className={`rounded-full px-4 py-2 uppercase tracking-[0.2em] ${
                    activeTab === tab.key
                      ? "bg-white/10 text-white"
                      : "text-slate-400 hover:bg-white/5"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {activeTab === "paste" ? (
              <div className="space-y-3">
                <label className="text-xs uppercase tracking-[0.3em] text-slate-400">
                  Paste callsigns
                </label>
                <textarea
                  rows={10}
                  value={rawInput}
                  onChange={(event) => setRawInput(event.target.value)}
                  placeholder="Enter callsigns separated by commas, spaces, or line breaks."
                  className="w-full rounded-2xl border border-white/10 bg-slate-950/80 px-4 py-3 text-sm text-slate-100"
                />
                <p className="text-xs text-slate-400">
                  Tip: callsigns are typically 2–8 alphanumeric characters.
                </p>
              </div>
            ) : null}

            {activeTab === "upload" ? (
              <div className="space-y-3">
                <label className="text-xs uppercase tracking-[0.3em] text-slate-400">
                  Upload list
                </label>
                <input
                  type="file"
                  accept=".csv,.txt"
                  onChange={(event) =>
                    handleFileUpload(event.target.files?.[0] || null)
                  }
                  className="text-xs text-slate-300"
                />
                {csvHeader.length > 0 ? (
                  <label className="flex flex-col gap-2 text-sm text-slate-200">
                    Select callsign column
                    <select
                      value={selectedColumn}
                      onChange={(event) => setSelectedColumn(event.target.value)}
                      className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                    >
                      {csvHeader.map((column) => (
                        <option key={column} value={column}>
                          {column || "(empty)"}
                        </option>
                      ))}
                    </select>
                  </label>
                ) : (
                  <p className="text-xs text-slate-400">
                    CSV files without headers will be parsed line-by-line.
                  </p>
                )}
                <p className="text-xs text-slate-400">
                  XLSX import is optional and not enabled yet.
                </p>
              </div>
            ) : null}

            {activeTab === "template" ? (
              <div className="rounded-2xl border border-dashed border-white/10 bg-slate-950/40 px-4 py-6 text-sm text-slate-400">
                Group templates are coming soon. For now, paste or upload a list.
              </div>
            ) : null}

            <div className="rounded-2xl border border-white/10 bg-slate-950/40 px-4 py-4 text-sm text-slate-200">
              <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
                Assign to group
              </p>
              <div className="mt-4 space-y-3">
                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    checked={groupMode === "new"}
                    onChange={() => setGroupMode("new")}
                  />
                  <span>Create new group</span>
                </label>
                {groupMode === "new" ? (
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="flex flex-col gap-2">
                      Group name
                      <input
                        type="text"
                        value={groupName}
                        onChange={(event) => setGroupName(event.target.value)}
                        className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                      />
                    </label>
                    <label className="flex flex-col gap-2">
                      Description
                      <input
                        type="text"
                        value={groupDescription}
                        onChange={(event) =>
                          setGroupDescription(event.target.value)
                        }
                        className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                      />
                    </label>
                    <label className="flex flex-col gap-2">
                      Color tag
                      <select
                        value={groupColor}
                        onChange={(event) => setGroupColor(event.target.value)}
                        className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                      >
                        {colorOptions.map((color) => (
                          <option key={color} value={color}>
                            {color}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="flex flex-col gap-2">
                      Icon
                      <select
                        value={groupIcon}
                        onChange={(event) => setGroupIcon(event.target.value)}
                        className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                      >
                        {iconOptions.map((icon) => (
                          <option key={icon} value={icon}>
                            {icon}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                ) : null}

                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    checked={groupMode === "existing"}
                    onChange={() => setGroupMode("existing")}
                  />
                  <span>Add to existing group</span>
                </label>
                {groupMode === "existing" ? (
                  <select
                    value={existingGroupId}
                    onChange={(event) => setExistingGroupId(event.target.value)}
                    className="rounded-xl border border-white/10 bg-slate-900/80 px-3 py-2"
                  >
                    {groups.length === 0 ? (
                      <option value="">No groups available</option>
                    ) : (
                      groups.map((group) => (
                        <option key={group.id} value={group.id}>
                          {group.name}
                        </option>
                      ))
                    )}
                  </select>
                ) : null}

                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    checked={groupMode === "ungrouped"}
                    onChange={() => setGroupMode("ungrouped")}
                  />
                  <span>Leave ungrouped</span>
                </label>
              </div>
            </div>
          </div>

          <div className="rounded-3xl border border-white/10 bg-white/5 p-4">
            <div className="flex items-center justify-between">
              <p className="text-xs uppercase tracking-[0.3em] text-slate-400">
                Preview
              </p>
              <span className="text-xs text-slate-400">
                {entries.length} entries
              </span>
            </div>
            {validationMessage ? (
              <p className="mt-3 text-xs text-rose-200">{validationMessage}</p>
            ) : null}
            <div className="mt-4 max-h-[340px] overflow-auto text-sm">
              {entries.length === 0 ? (
                <p className="text-xs text-slate-400">
                  Paste or upload callsigns to preview validation.
                </p>
              ) : (
                <table className="w-full text-left text-xs">
                  <thead className="text-slate-400">
                    <tr>
                      <th className="py-2">Callsign</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entries.map((entry) => (
                      <tr key={entry.callsign} className="border-b border-white/5">
                        <td className="py-2 text-slate-100">{entry.callsign}</td>
                        <td className={statusColor(entry.status)}>
                          {entry.status === "valid"
                            ? "Valid"
                            : entry.status === "no-data"
                            ? "No live data"
                            : entry.status === "checking"
                            ? "Checking"
                            : "Invalid format"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
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
            disabled={disabledSave}
            className="rounded-full bg-gradient-to-r from-cyan-400 to-blue-500 px-4 py-2 text-sm font-semibold text-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
          >
            Import
          </button>
        </div>
      </div>
    </div>
  );
};

export default BulkAddAircraftModal;
