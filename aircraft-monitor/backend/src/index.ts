import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import aircraftRoutes from "./routes/aircraft";

dotenv.config();

const app = express();
const port = Number(process.env.PORT) || 3000;

app.use(cors());
app.use(express.json());

app.get("/api/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.use("/api/aircraft", aircraftRoutes);

app.listen(port, () => {
  console.log(`Backend listening on port ${port}`);
});
