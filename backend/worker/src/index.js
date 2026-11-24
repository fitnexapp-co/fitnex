// FITNEX backend on Cloudflare Workers (free plan, no card). AI: same contract and rules as the
// Firebase version in ../functions (shared pure logic). Phase 9: cloud sync + global AI quota on D1.
//   App --(Firebase ID token)--> verify token --> rate limit --> Gemini (key = Worker secret) --> validated JSON.
import { createRemoteJWKSet, jwtVerify } from "jose";
import {
  FOOD_SCHEMA, HttpError, RateLimiter, chatSystemPrompt, foodPrompt, sanitizeFoodResult, validateChat, validateFoodPhoto,
} from "../../functions/src/logic.js";
import { countAiRequest, erase, pull, push, validatePull, validatePush } from "./sync.js";
import {
  PLAN_SCHEMA, ROUTINE_SCHEMA, planPrompt, routinePrompt, sanitizePlan, sanitizeRoutine, validatePlanRequest, validateRoutineRequest,
} from "./routine.js";

const GOOGLE_KEYS = createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
const limiter = new RateLimiter(); // AI, per isolate: stops bursts (the daily quota is global, in D1)
const syncLimiter = new RateLimiter({ perMinute: 30, perDay: 3000 });
const MAX_BODY_BYTES = 6 * 1024 * 1024;

/** Verifies a Firebase Auth ID token for this project (signature, issuer, audience, expiry). */
async function authenticate(request, env) {
  const header = request.headers.get("Authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) throw new HttpError(401, "unauthenticated", "Sign in required");
  try {
    const { payload } = await jwtVerify(token, GOOGLE_KEYS, {
      issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
      audience: env.FIREBASE_PROJECT_ID,
    });
    if (!payload.sub) throw new Error("no subject");
    return { uid: payload.sub };
  } catch {
    throw new HttpError(401, "unauthenticated", "Invalid or expired session");
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** How long to skip the main model after it said it was overloaded (per isolate). */
const OVERLOAD_COOLDOWN_MS = 3 * 60_000;
let mainOverloadedUntil = 0;

/**
 * Order in which to try the models. Free-tier Flash often answers 503 (overloaded) after several
 * seconds; Lite answers in ~1 s. So: on 503 go straight to Lite (no second wait on the busy model),
 * and while the main model is known to be busy, start with Lite.
 */
/** Overloaded, failing or simply too slow: worth trying the next model. */
export function isBusy(e) {
  return /^gemini (500|503)/.test(e?.message ?? "") || e?.name === "TimeoutError" || e?.name === "AbortError";
}

/** Per-attempt cap. Three attempts must fit in the app's 60 s wait; Lite usually answers in 1–3 s. */
export const ATTEMPT_TIMEOUT_MS = 15_000;

export function modelOrder(main, lite, { fast = false, mainBusy = false } = {}) {
  return fast || mainBusy ? [lite, lite, main] : [main, lite, lite];
}

async function gemini(env, body, { fast = false } = {}) {
  const main = env.GEMINI_MODEL || "gemini-flash-latest";
  const lite = env.GEMINI_FALLBACK_MODEL || "gemini-flash-lite-latest";
  const models = modelOrder(main, lite, { fast, mainBusy: Date.now() < mainOverloadedUntil });
  let last;
  for (const [i, model] of models.entries()) {
    try {
      return await callGemini(env, model, body);
    } catch (e) {
      last = e;
      if (!isBusy(e)) throw e;
      if (model === main) mainOverloadedUntil = Date.now() + OVERLOAD_COOLDOWN_MS;
      if (models[i + 1] === model) await sleep(800); // only pause before retrying the same model
    }
  }
  throw last;
}

/**
 * Flash models think at "medium" or "high" by default, which adds many seconds before the first
 * word. Our tasks (short chat, food photo, JSON routines) don't need deep reasoning, so ask for
 * "low" (supported by every Flash model). Lite models already default to near-off: leave them as is.
 */
export function withLowThinking(model, body) {
  if (/lite/i.test(model)) return body;
  return { ...body, generationConfig: { ...body.generationConfig, thinkingConfig: { thinkingLevel: "low" } } };
}

async function callGemini(env, model, body) {
  try {
    return await requestGemini(env, model, withLowThinking(model, body));
  } catch (e) {
    // If a model ever rejects the thinking setting, answer anyway with its default.
    if (/^gemini 400/.test(e.message) && body !== withLowThinking(model, body)) return requestGemini(env, model, body);
    throw e;
  }
}

async function requestGemini(env, model, body) {
  const started = Date.now();
  // A hung call must fail fast and move on to the next model, not leave the user waiting.
  let res;
  try {
    res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(ATTEMPT_TIMEOUT_MS),
    });
  } catch (e) {
    console.log("gemini", model, e?.name ?? "error", `${Date.now() - started} ms`);
    throw e;
  }
  console.log("gemini", model, res.status, `${Date.now() - started} ms`); // timing only, never content
  if (res.status === 429) throw new HttpError(429, "rate_limited", "Gemini quota reached");
  if (!res.ok) {
    // Google's error status and reason only (e.g. API_KEY_INVALID); never the request body.
    const err = await res.json().catch(() => ({}));
    const reason = err.error?.details?.find((d) => d.reason)?.reason ?? "";
    throw new Error(`gemini ${res.status} ${err.error?.status ?? ""} ${reason}`.trim());
  }
  const data = await res.json();
  return (data.candidates?.[0]?.content?.parts ?? []).map((p) => p.text ?? "").join("").trim();
}

async function chat(request, env) {
  const input = validateChat(await request.json());
  const contents = [
    ...input.history.map((t) => ({ role: t.role, parts: [{ text: t.text }] })),
    { role: "user", parts: [{ text: input.message }] },
  ];
  const reply = await gemini(env, {
    systemInstruction: { parts: [{ text: chatSystemPrompt(input) }] },
    contents,
    generationConfig: { temperature: 0.6, maxOutputTokens: 900 },
  });
  if (!reply) throw new HttpError(502, "empty_reply", "The assistant did not answer");
  return json({ reply });
}

async function analyzeFood(request, env) {
  const input = validateFoodPhoto(await request.json());
  const text = await gemini(env, {
    contents: [{ role: "user", parts: [{ inlineData: { mimeType: input.mimeType, data: input.bytes.toString("base64") } }, { text: foodPrompt(input) }] }],
    generationConfig: { responseMimeType: "application/json", responseSchema: FOOD_SCHEMA, temperature: 0.2 },
  });
  let raw;
  try { raw = JSON.parse(text || "{}"); } catch { throw new HttpError(502, "bad_model_output", "Could not read the analysis"); }
  return json(sanitizeFoodResult(raw, input));
}

async function routine(request, env) {
  const input = validateRoutineRequest(await request.json());
  console.log("routine request", input.exercises.length, "exercises");
  const text = await gemini(env, {
    contents: [{ role: "user", parts: [{ text: routinePrompt(input) }] }],
    generationConfig: { responseMimeType: "application/json", responseSchema: ROUTINE_SCHEMA, temperature: 0.5 },
  }, { fast: true });
  let raw;
  try { raw = JSON.parse(text || "{}"); } catch { throw new HttpError(502, "bad_model_output", "Could not read the plan"); }
  return json(sanitizeRoutine(raw, input));
}

async function plan(request, env) {
  const input = validatePlanRequest(await request.json());
  const text = await gemini(env, {
    contents: [{ role: "user", parts: [{ text: planPrompt(input) }] }],
    generationConfig: { responseMimeType: "application/json", responseSchema: PLAN_SCHEMA, temperature: 0.5 },
  }, { fast: true });
  let raw;
  try { raw = JSON.parse(text || "{}"); } catch { throw new HttpError(502, "bad_model_output", "Could not read the plan"); }
  return json(sanitizePlan(raw, input));
}

const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json" } });
const rawJson = (text) => new Response(text, { headers: { "Content-Type": "application/json" } });

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname.replace(/^\/api/, "");
    try {
      if (request.method === "GET" && path === "/v1/health") return json({ status: "ok", apiVersion: "1" });
      if (request.method !== "POST") throw new HttpError(405, "method_not_allowed", "POST only");
      if (Number(request.headers.get("Content-Length") ?? 0) > MAX_BODY_BYTES) throw new HttpError(413, "body_too_large", "Body too large");
      const user = await authenticate(request, env);
      if (path.startsWith("/v1/sync/")) {
        syncLimiter.check(user.uid);
        if (path === "/v1/sync/push") return json(await push(env.DB, user.uid, validatePush(await request.json())));
        if (path === "/v1/sync/pull") return rawJson(await pull(env.DB, user.uid, validatePull(await request.json().catch(() => ({})))));
        if (path === "/v1/sync/erase") return json(await erase(env.DB, user.uid));
        throw new HttpError(404, "not_found", "Unknown endpoint");
      }
      limiter.check(user.uid);
      if (path.startsWith("/v1/ai/")) await countAiRequest(env.DB, user.uid);
      if (path === "/v1/ai/plan") return await plan(request, env);
      if (path === "/v1/ai/chat") return await chat(request, env);
      if (path === "/v1/ai/routine") return await routine(request, env);
      if (path === "/v1/ai/analyze-food") return await analyzeFood(request, env);
      throw new HttpError(404, "not_found", "Unknown endpoint");
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.code }, e.status);
      if (e instanceof SyntaxError) return json({ error: "invalid_json" }, 400);
      console.error("api failure", path, e?.message); // never log bodies (health data, photos)
      return json({ error: "ai_unavailable" }, 502);
    }
  },
};
