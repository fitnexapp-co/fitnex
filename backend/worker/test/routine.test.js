import { test } from "node:test";
import assert from "node:assert/strict";
import { planPrompt, routinePrompt, sanitizePlan, sanitizeRoutine, validatePlanRequest, validateRoutineRequest } from "../src/routine.js";

const catalog = [
  { id: "push_up", name: "Flexiones", muscles: ["Pectoral"] },
  { id: "bodyweight_squat", name: "Sentadilla", muscles: ["Cuádriceps"] },
  { id: "glute_bridge", name: "Puente de glúteo" },
  { id: "plank", name: "Plancha" },
];
const base = { goal: "GENERAL", catalog, sessionMinutes: 40, daysPerWeek: 3, experience: "BEGINNER", ageProfile: "ADULT" };

test("request validation keeps only usable catalogue entries and clamps numbers", () => {
  const input = validateRoutineRequest({ ...base, sessionMinutes: 500, daysPerWeek: 0, catalog: [...catalog, { id: 5 }] });
  assert.equal(input.exercises.length, 4);
  assert.equal(input.minutes, 120);
  assert.equal(input.days, 1);
  assert.throws(() => validateRoutineRequest({ ...base, goal: "LOSE_10KG" }), /invalid goal/);
  assert.throws(() => validateRoutineRequest({ ...base, catalog: catalog.slice(0, 2) }), /not enough/);
});

test("the prompt lists exact ids and applies minor rules", () => {
  const p = routinePrompt(validateRoutineRequest({ ...base, ageProfile: "MINOR" }));
  assert.match(p, /- push_up: Flexiones/);
  assert.match(p, /menor de edad/);
});

test("model output is filtered to the catalogue, deduplicated and clamped", () => {
  const input = validateRoutineRequest(base);
  const out = sanitizeRoutine({
    name: "x".repeat(80),
    rationale: "Porque sí.",
    exercises: [
      { exerciseId: "push_up", sets: 12, reps: 100, restSeconds: 7 },
      { exerciseId: "push_up", sets: 3, reps: 10, restSeconds: 60 },
      { exerciseId: "made_up_move", sets: 3, reps: 10, restSeconds: 60 },
      { exerciseId: "bodyweight_squat", sets: 3, reps: 12, restSeconds: 95 },
      { exerciseId: "plank", sets: 2, reps: 1, restSeconds: 60 },
    ],
  }, input);
  assert.equal(out.name.length, 40);
  assert.deepEqual(out.exercises.map((e) => e.exerciseId), ["push_up", "bodyweight_squat", "plank"]);
  assert.deepEqual([out.exercises[0].sets, out.exercises[0].reps, out.exercises[0].restSeconds], [6, 30, 30]);
  assert.equal(out.exercises[1].restSeconds, 90);
});

test("minors get capped sets and no very low rep sets; incomplete plans are rejected", () => {
  const minor = validateRoutineRequest({ ...base, ageProfile: "MINOR" });
  const out = sanitizeRoutine({ name: "A", rationale: "", exercises: catalog.slice(0, 3).map((c) => ({ exerciseId: c.id, sets: 6, reps: 2, restSeconds: 120 })) }, minor);
  assert.ok(out.exercises.every((e) => e.sets <= 4 && e.reps >= 6));
  assert.throws(() => sanitizeRoutine({ exercises: [{ exerciseId: "push_up", sets: 3, reps: 10, restSeconds: 60 }] }, minor), /incomplete/);
});

test("weekly plan: session count clamped, history used, every session re-validated", () => {
  const input = validatePlanRequest({ ...base, sessions: 9, history: "6 entrenos en 4 semanas" });
  assert.equal(input.sessions, 6);
  assert.match(planPrompt(input), /PLAN SEMANAL de 6 sesiones/);
  assert.match(planPrompt(input), /6 entrenos en 4 semanas/);
  const ex = (ids) => ids.map((id) => ({ exerciseId: id, sets: 3, reps: 10, restSeconds: 60 }));
  const out = sanitizePlan({ name: "Plan", rationale: "r", sessions: [
    { name: "Día 1", exercises: ex(["push_up", "plank", "fake", "bodyweight_squat"]) },
    { name: "Día 2", exercises: ex(["glute_bridge", "bodyweight_squat", "plank"]) },
  ] }, input);
  assert.equal(out.sessions.length, 2);
  assert.deepEqual(out.sessions[0].exercises.map((e) => e.exerciseId), ["push_up", "plank", "bodyweight_squat"]);
  assert.throws(() => sanitizePlan({ sessions: [{ name: "x", exercises: ex(["push_up", "plank", "glute_bridge"]) }] }, input), /incomplete/);
});