import { test } from "node:test";
import assert from "node:assert/strict";
import { ATTEMPT_TIMEOUT_MS, isBusy, modelOrder, withLowThinking } from "../src/index.js";

test("timeouts and 5xx move on to the next model; other errors don't; three attempts fit in the app's 60 s", () => {
  const timeout = new Error("The operation was aborted due to timeout");
  timeout.name = "TimeoutError";
  assert.ok(isBusy(timeout));
  assert.ok(isBusy(new Error("gemini 503 UNAVAILABLE")));
  assert.ok(!isBusy(new Error("gemini 400 INVALID_ARGUMENT")));
  assert.ok(3 * ATTEMPT_TIMEOUT_MS + 2 * 800 < 60_000);
});

test("a busy main model is tried once at most; Lite goes first for structured tasks or while main is busy", () => {
  assert.deepEqual(modelOrder("m", "l"), ["m", "l", "l"]);
  assert.deepEqual(modelOrder("m", "l", { mainBusy: true }), ["l", "l", "m"]);
  assert.deepEqual(modelOrder("m", "l", { fast: true }), ["l", "l", "m"]);
});

test("Flash models are asked to think little; Lite models and the rest of the config are untouched", () => {
  const body = { contents: [], generationConfig: { temperature: 0.5, responseMimeType: "application/json" } };
  const flash = withLowThinking("gemini-flash-latest", body);
  assert.deepEqual(flash.generationConfig, { temperature: 0.5, responseMimeType: "application/json", thinkingConfig: { thinkingLevel: "low" } });
  assert.equal(body.generationConfig.thinkingConfig, undefined, "the original request is not modified");
  assert.equal(withLowThinking("gemini-flash-lite-latest", body), body);
});
