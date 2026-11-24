// FITNEX backend: one HTTPS function, "api" (spec §8). Flow:
//   App --(Firebase ID token)--> verify user --> rate limit --> Gemini (key in Secret Manager) --> validated JSON.
// The Gemini key never reaches the app; prompts and output rules live only here.
import { onRequest } from "firebase-functions/v2/https";
import { defineSecret, defineString } from "firebase-functions/params";
import { logger } from "firebase-functions";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { GoogleGenAI } from "@google/genai";
import {
  FOOD_SCHEMA, HttpError, RateLimiter, chatSystemPrompt, foodPrompt, sanitizeFoodResult, validateChat, validateFoodPhoto,
} from "./logic.js";

initializeApp();
const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");
// Model name is server config, so it can be changed without shipping a new app.
const GEMINI_MODEL = defineString("GEMINI_MODEL", { default: "gemini-2.5-flash" });
const limiter = new RateLimiter();

async function authenticate(req) {
  const header = req.get("Authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) throw new HttpError(401, "unauthenticated", "Sign in required");
  try {
    return await getAuth().verifyIdToken(token);
  } catch {
    throw new HttpError(401, "unauthenticated", "Invalid or expired session");
  }
}

function gemini() {
  return new GoogleGenAI({ apiKey: GEMINI_API_KEY.value() });
}

async function chat(req, res) {
  const input = validateChat(req.body);
  const contents = [
    ...input.history.map((t) => ({ role: t.role, parts: [{ text: t.text }] })),
    { role: "user", parts: [{ text: input.message }] },
  ];
  const result = await gemini().models.generateContent({
    model: GEMINI_MODEL.value(),
    contents,
    config: { systemInstruction: chatSystemPrompt(input), temperature: 0.6, maxOutputTokens: 900 },
  });
  const reply = (result.text ?? "").trim();
  if (!reply) throw new HttpError(502, "empty_reply", "The assistant did not answer");
  res.json({ reply });
}

async function analyzeFood(req, res) {
  const input = validateFoodPhoto(req.body);
  const result = await gemini().models.generateContent({
    model: GEMINI_MODEL.value(),
    contents: [{
      role: "user",
      parts: [
        { inlineData: { mimeType: input.mimeType, data: input.bytes.toString("base64") } },
        { text: foodPrompt(input) },
      ],
    }],
    config: { responseMimeType: "application/json", responseSchema: FOOD_SCHEMA, temperature: 0.2 },
  });
  let raw;
  try {
    raw = JSON.parse(result.text ?? "{}");
  } catch {
    throw new HttpError(502, "bad_model_output", "Could not read the analysis");
  }
  res.json(sanitizeFoodResult(raw, input));
}

export const api = onRequest(
  { region: "us-central1", secrets: [GEMINI_API_KEY], timeoutSeconds: 60, memory: "512MiB", maxInstances: 5, cors: false },
  async (req, res) => {
    try {
      if (req.method === "GET" && req.path === "/v1/health") return res.json({ status: "ok", apiVersion: "1" });
      if (req.method !== "POST") throw new HttpError(405, "method_not_allowed", "POST only");
      const user = await authenticate(req);
      limiter.check(user.uid);
      if (req.path === "/v1/ai/chat") return await chat(req, res);
      if (req.path === "/v1/ai/analyze-food") return await analyzeFood(req, res);
      throw new HttpError(404, "not_found", "Unknown endpoint");
    } catch (e) {
      if (e instanceof HttpError) return res.status(e.status).json({ error: e.code });
      // Never log request bodies (health data, photos): only the error.
      logger.error("api failure", { path: req.path, message: e?.message });
      return res.status(502).json({ error: "ai_unavailable" });
    }
  },
);
