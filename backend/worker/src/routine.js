// FITNEX AI routine builder (spec "Creación inteligente de rutinas").
// The app sends ONLY exercises from its own catalogue that fit the user's equipment; Gemini must pick
// from that list (JSON schema), and everything it returns is re-validated here: unknown ids dropped,
// duplicates removed, sets/reps/rest clamped, text trimmed. Minors get moderate, technique-first plans.
import { HttpError } from "../../functions/src/logic.js";

export const GOALS = ["STRENGTH", "HYPERTROPHY", "ENDURANCE", "GENERAL"];
const GOAL_ES = {
  STRENGTH: "ganar fuerza",
  HYPERTROPHY: "ganar masa muscular",
  ENDURANCE: "mejorar la resistencia muscular",
  GENERAL: "ponerse en forma de manera general",
};
const LEVEL_ES = { BEGINNER: "principiante", INTERMEDIATE: "intermedio", ADVANCED: "avanzado" };

export const ROUTINE_LIMITS = { catalogMax: 80, minExercises: 3, maxExercises: 10, focusChars: 300 };

const str = (v, max) => (typeof v === "string" ? v.trim().slice(0, max) : "");

export function validateRoutineRequest(body) {
  if (!body || typeof body !== "object") throw new HttpError(400, "invalid_body", "invalid body");
  const goal = GOALS.includes(body.goal) ? body.goal : null;
  if (!goal) throw new HttpError(400, "invalid_goal", "invalid goal");
  const catalog = Array.isArray(body.catalog) ? body.catalog.slice(0, ROUTINE_LIMITS.catalogMax) : [];
  const exercises = catalog
    .filter((e) => e && typeof e.id === "string" && typeof e.name === "string")
    .map((e) => ({
      id: e.id.slice(0, 60),
      name: e.name.slice(0, 80),
      muscles: Array.isArray(e.muscles) ? e.muscles.filter((m) => typeof m === "string").slice(0, 6) : [],
      difficulty: str(e.difficulty, 20),
    }));
  if (exercises.length < ROUTINE_LIMITS.minExercises) throw new HttpError(400, "catalog_too_small", "not enough exercises");
  const minutes = Number.isInteger(body.sessionMinutes) ? Math.min(Math.max(body.sessionMinutes, 15), 120) : 45;
  const days = Number.isInteger(body.daysPerWeek) ? Math.min(Math.max(body.daysPerWeek, 1), 7) : 3;
  return {
    goal,
    minutes,
    days,
    level: LEVEL_ES[body.experience] ? body.experience : "BEGINNER",
    focus: str(body.focus, ROUTINE_LIMITS.focusChars),
    minor: body.ageProfile !== "ADULT",
    exercises,
  };
}

export const ROUTINE_SCHEMA = {
  type: "OBJECT",
  properties: {
    name: { type: "STRING" },
    rationale: { type: "STRING" },
    exercises: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          exerciseId: { type: "STRING" },
          sets: { type: "INTEGER" },
          reps: { type: "INTEGER" },
          restSeconds: { type: "INTEGER" },
          note: { type: "STRING" },
        },
        required: ["exerciseId", "sets", "reps", "restSeconds"],
      },
    },
  },
  required: ["name", "rationale", "exercises"],
};

/** How many exercises fit in a session of that length (about 10 minutes each, warm-up included). */
export function exercisesFor(minutes) {
  return Math.min(Math.max(Math.round(minutes / 10), 4), 8);
}

export function routinePrompt(input) {
  const list = input.exercises.map((e) => `- ${e.id}: ${e.name}${e.muscles.length ? ` (${e.muscles.join(", ")})` : ""}${e.difficulty ? ` [${e.difficulty}]` : ""}`).join("\n");
  const target = exercisesFor(input.minutes);
  return [
    "Eres el entrenador de FITNEX. Diseña UNA sesión de entrenamiento en español.",
    "Escribe con ortografía correcta del español: tildes y eñes incluidas (por ejemplo «Día», «Musculación», «diseñado»).",
    `Para ${input.minutes} minutos, pon unos ${target} ejercicios por sesión (entre ${Math.max(target - 1, ROUTINE_LIMITS.minExercises)} y ${Math.min(target + 1, ROUTINE_LIMITS.maxExercises)}).`,
    `Objetivo: ${GOAL_ES[input.goal]}. Nivel: ${LEVEL_ES[input.level]}. Duración aproximada: ${input.minutes} minutos. Entrena ${input.days} días por semana.`,
    input.focus ? `Lo que pide el usuario (tenlo en cuenta solo si es sobre entrenamiento): "${input.focus}"` : "",
    input.minor
      ? "La persona es menor de edad o no consta su edad: prioriza la técnica, cargas moderadas, sin entrenamientos al fallo ni objetivos de peso corporal, calorías o estética corporal."
      : "No hables de calorías ni de pérdida de peso salvo que lo pida.",
    `Elige entre ${ROUTINE_LIMITS.minExercises} y ${ROUTINE_LIMITS.maxExercises} ejercicios SOLO de esta lista, usando su id exacto, ordenados como se harían (multiarticulares primero):`,
    list,
    "Series 1-6, repeticiones 1-30, descanso 30-300 segundos según el objetivo. 'name': nombre corto de la rutina (máx. 40 caracteres). 'rationale': 2-4 frases explicando por qué está diseñada así. 'note' opcional por ejercicio (una frase de técnica).",
    "No inventes ejercicios ni ids. No des consejos médicos.",
  ].filter(Boolean).join("\n");
}

const clamp = (v, lo, hi, fallback) => (Number.isFinite(v) ? Math.min(Math.max(Math.round(v), lo), hi) : fallback);

/** Re-validates the model output against the catalogue the app sent. */
export function sanitizeRoutine(raw, input) {
  const allowed = new Map(input.exercises.map((e) => [e.id, e]));
  const seen = new Set();
  const exercises = (Array.isArray(raw?.exercises) ? raw.exercises : [])
    .filter((e) => e && allowed.has(e.exerciseId) && !seen.has(e.exerciseId) && seen.add(e.exerciseId))
    .slice(0, ROUTINE_LIMITS.maxExercises)
    .map((e) => ({
      exerciseId: e.exerciseId,
      sets: clamp(e.sets, 1, input.minor ? 4 : 6, 3),
      reps: clamp(e.reps, input.minor ? 6 : 1, 30, 10),
      restSeconds: clamp(Math.round((Number(e.restSeconds) || 90) / 15) * 15, 30, 300, 90),
      note: str(e.note, 160),
    }));
  if (exercises.length < ROUTINE_LIMITS.minExercises) throw new HttpError(502, "bad_model_output", "The plan was incomplete");
  return {
    name: str(raw?.name, 40) || "Rutina FITNEX AI",
    rationale: str(raw?.rationale, 700),
    exercises,
  };
}

// ---------------------------------------------------------------- weekly plan (several sessions)

export const PLAN_SCHEMA = {
  type: "OBJECT",
  properties: {
    name: { type: "STRING" },
    rationale: { type: "STRING" },
    sessions: { type: "ARRAY", items: { type: "OBJECT", properties: { name: { type: "STRING" }, exercises: ROUTINE_SCHEMA.properties.exercises }, required: ["name", "exercises"] } },
  },
  required: ["name", "rationale", "sessions"],
};

/** Same validation as one session, plus how many sessions and an optional summary of recent training. */
export function validatePlanRequest(body) {
  const input = validateRoutineRequest(body);
  const sessions = Number.isInteger(body.sessions) ? Math.min(Math.max(body.sessions, 2), 6) : Math.min(Math.max(input.days, 2), 6);
  return { ...input, sessions, history: str(body.history, 1200) };
}

export function planPrompt(input) {
  return [
    routinePrompt(input).replace("Diseña UNA sesión de entrenamiento en español.", `Diseña un PLAN SEMANAL de ${input.sessions} sesiones distintas en español, repartiendo los grupos musculares para que cada músculo descanse entre sesiones.`),
    input.history
      ? `Historial reciente del usuario (datos reales): ${input.history}\nAjusta el plan a ese nivel con progresión prudente; si hay poco historial, empieza conservador.`
      : "No hay historial: empieza con un plan conservador.",
    `Devuelve exactamente ${input.sessions} sesiones en 'sessions', cada una con su 'name' corto (p. ej. 'Día 1 · Empuje') y sus ejercicios. 'name' del plan: nombre corto. 'rationale': 2-4 frases sobre el reparto y la progresión.`,
  ].join("\n");
}

export function sanitizePlan(raw, input) {
  const sessions = (Array.isArray(raw?.sessions) ? raw.sessions : []).slice(0, input.sessions).map((s, i) => {
    const one = sanitizeRoutine({ name: s?.name, rationale: "", exercises: s?.exercises }, input);
    return { name: str(s?.name, 40) || `Día ${i + 1}`, exercises: one.exercises };
  });
  if (sessions.length < 2) throw new HttpError(502, "bad_model_output", "The plan was incomplete");
  return { name: str(raw?.name, 40) || "Plan FITNEX AI", rationale: str(raw?.rationale, 700), sessions };
}