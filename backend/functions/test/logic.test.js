import { test } from "node:test";
import assert from "node:assert/strict";
import {
  HttpError, LIMITS, RateLimiter, chatSystemPrompt, foodPrompt, sanitizeFoodResult, validateChat, validateFoodPhoto,
} from "../src/logic.js";

test("chat validation trims, bounds history and treats unknown age as minor", () => {
  const history = Array.from({ length: 20 }, (_, i) => ({ role: i % 2 ? "model" : "user", text: `t${i}` }));
  const v = validateChat({ message: "  hola  ", screen: "nope", history: [...history, { role: "system", text: "x" }] });
  assert.equal(v.message, "hola");
  assert.equal(v.screen, "progress");
  assert.equal(v.minor, true);
  assert.equal(v.history.length, LIMITS.historyTurns - 1); // last 12, minus the invalid "system" turn
  assert.throws(() => validateChat({ message: "" }), HttpError);
  assert.throws(() => validateChat({ message: "x".repeat(LIMITS.messageChars + 1) }), HttpError);
});

test("system prompt applies minor rules and includes only given context", () => {
  const minor = chatSystemPrompt({ screen: "nutrition", minor: true, context: "Agua hoy: 500 ml" });
  assert.match(minor, /MENOR DE EDAD/);
  assert.match(minor, /NO des objetivos de calorías/);
  assert.match(minor, /Agua hoy: 500 ml/);
  const adult = chatSystemPrompt({ screen: "progress", minor: false, context: "" });
  assert.doesNotMatch(adult, /MENOR DE EDAD/);
  assert.match(adult, /sustituyes a profesionales/);
});

test("photo validation checks type and size", () => {
  const ok = validateFoodPhoto({ mimeType: "image/jpeg", imageBase64: Buffer.from("abc").toString("base64"), ageProfile: "ADULT" });
  assert.equal(ok.minor, false);
  assert.throws(() => validateFoodPhoto({ mimeType: "image/gif", imageBase64: "AAAA" }), HttpError);
  const big = Buffer.alloc(LIMITS.imageBytes + 1).toString("base64");
  assert.throws(() => validateFoodPhoto({ mimeType: "image/png", imageBase64: big }), (e) => e.status === 413);
  assert.match(foodPrompt({ note: "", minor: true }), /kcal = 0/);
});

test("model output is clamped, minors get no kcal, unknown food asks instead of inventing", () => {
  const raw = { identifiable: true, foods: [{ name: " Arroz ", portionGrams: 99999, proteinG: -3, carbsG: 40, fatG: 1, fiberG: 1, kcal: 200, confidence: 3 }, { name: "" }] };
  const minor = sanitizeFoodResult(raw, { minor: true });
  assert.equal(minor.foods.length, 1);
  assert.deepEqual([minor.foods[0].name, minor.foods[0].portionGrams, minor.foods[0].proteinG, minor.foods[0].kcal, minor.foods[0].confidence], ["Arroz", 2000, 0, 0, 1]);
  assert.equal(sanitizeFoodResult(raw, { minor: false }).foods[0].kcal, 200);
  const unknown = sanitizeFoodResult({ identifiable: false, foods: [{ name: "algo", portionGrams: 1, proteinG: 1, carbsG: 1, fatG: 1, fiberG: 1, confidence: 0.1 }] }, { minor: false });
  assert.equal(unknown.identifiable, false);
  assert.equal(unknown.foods.length, 0);
  assert.ok(unknown.question.length > 0);
});

test("rate limiter blocks bursts per user and per day", () => {
  let t = 0;
  const rl = new RateLimiter({ perMinute: 2, perDay: 3, now: () => t });
  rl.check("a"); rl.check("a");
  assert.throws(() => rl.check("a"), (e) => e.status === 429);
  rl.check("b"); // other users are independent
  t += 61_000;
  rl.check("a");
  t += 61_000;
  assert.throws(() => rl.check("a"), (e) => e.status === 429); // 3 per day reached
});
