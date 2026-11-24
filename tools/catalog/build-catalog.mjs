// Builds core/data/src/main/assets/catalog/catalog.json (version 4) from:
//   - the existing hand-written exercises (kept exactly as they are: same ids, texts and photos);
//   - the exercise families in families-*.mjs (new exercises).
// Usage: node tools/catalog/build-catalog.mjs
// Fails on duplicate ids, duplicate names, unknown muscles, equipment, types or tracking modes.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { families1 } from "./families-1.mjs";
import { families2 } from "./families-2.mjs";
import { families3 } from "./families-3.mjs";
import { extensions } from "./families-4.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const OUT = join(here, "../../core/data/src/main/assets/catalog/catalog.json");
const VERSION = 4;

const EQUIPMENT = new Set(["BODYWEIGHT", "DUMBBELL", "BARBELL", "KETTLEBELL", "MACHINE", "CABLE", "RESISTANCE_BAND", "BENCH", "PULL_UP_BAR", "OTHER",
  "EZ_BAR", "DIP_BARS", "RACK", "MEDICINE_BALL", "ROPE", "SLED", "SANDBAG", "CARDIO_MACHINE"]);
const TYPES = new Set(["STRENGTH", "HYPERTROPHY", "CARDIO", "MOBILITY", "CALISTHENICS", "FUNCTIONAL", "STRETCHING"]);
const TRACKING = new Set(["WEIGHT_REPS", "REPS", "TIME", "TIME_DISTANCE"]);
const DIFF = new Set(["BEGINNER", "INTERMEDIATE", "ADVANCED"]);

const current = JSON.parse(readFileSync(OUT, "utf8"));
const MUSCLES = new Set(current.muscles.map((m) => m.id));
// Keep only the original hand-written exercises (re-running the script must not duplicate).
const originals = current.exercises.filter((e) => !e.generated);

// English names and search tags for the original exercises (search works in both languages).
const ORIGINAL_ALIASES = {
  bench_press: ["Bench press", "pecho"], incline_dumbbell_press: ["Incline dumbbell press", "pecho"], push_up: ["Push-up", "flexiones"],
  dumbbell_fly: ["Dumbbell fly", "aperturas"], cable_crossover: ["Cable crossover", "cruces"], pull_up: ["Pull-up", "dominadas"],
  lat_pulldown: ["Lat pulldown", "jalón"], barbell_row: ["Barbell row", "remo"], one_arm_dumbbell_row: ["One-arm dumbbell row", "remo"],
  seated_cable_row: ["Seated cable row", "remo"], deadlift: ["Deadlift", "peso muerto"], overhead_press: ["Overhead press", "press militar", "OHP"],
  dumbbell_shoulder_press: ["Dumbbell shoulder press"], lateral_raise: ["Lateral raise"], face_pull: ["Face pull", "deltoides posterior"],
  rear_delt_fly: ["Rear delt fly", "pájaros"], barbell_curl: ["Barbell curl", "bíceps"], hammer_curl: ["Hammer curl", "bíceps"],
  triceps_pushdown: ["Triceps pushdown", "tríceps"], overhead_triceps_extension: ["Overhead triceps extension", "tríceps"],
  bench_dip: ["Bench dip", "fondos"], back_squat: ["Back squat", "sentadilla"], bodyweight_squat: ["Bodyweight squat", "air squat"],
  goblet_squat: ["Goblet squat"], lunge: ["Lunge"], romanian_deadlift: ["Romanian deadlift", "RDL"], leg_press: ["Leg press", "prensa"],
  leg_extension: ["Leg extension", "cuádriceps"], leg_curl: ["Leg curl", "femoral"], hip_thrust: ["Hip thrust", "glúteo"],
  glute_bridge: ["Glute bridge", "puente"], band_lateral_walk: ["Band lateral walk", "abductores"], standing_calf_raise: ["Standing calf raise", "gemelos"],
  plank: ["Plank", "core"], crunch: ["Crunch", "abdominales"], russian_twist: ["Russian twist", "oblicuos"],
  hanging_leg_raise: ["Hanging leg raise", "abdomen"], kettlebell_swing: ["Kettlebell swing", "kettlebell"],
  dumbbell_bench_press: ["Dumbbell bench press", "pecho"], chin_up: ["Chin-up", "dominadas"], arnold_press: ["Arnold press", "hombros"],
  front_raise: ["Front raise"], preacher_curl: ["Preacher curl", "Scott"], skull_crusher: ["Skull crusher", "press francés"],
  close_grip_bench_press: ["Close-grip bench press", "tríceps"], split_squat: ["Bulgarian split squat", "búlgara"], step_up: ["Step-up"],
  sumo_deadlift: ["Sumo deadlift"], good_morning: ["Good morning", "lumbar"], seated_calf_raise: ["Seated calf raise", "sóleo"],
  mountain_climber: ["Mountain climber", "escaladores"], bicycle_crunch: ["Bicycle crunch"], side_plank: ["Side plank"],
  dead_bug: ["Dead bug"], upright_row: ["Upright row", "trapecio"], barbell_shrug: ["Barbell shrug", "trapecio"],
  dumbbell_curl: ["Dumbbell curl"], incline_push_up: ["Incline push-up"], superman: ["Superman", "lumbar"],
  inverted_row: ["Inverted row", "remo australiano"], back_extension: ["Back extension", "hiperextensiones"],
  walking_lunge: ["Walking lunge"], chest_dip: ["Chest dip", "paralelas"],
};
// How each original is logged (the rest are weight × reps).
const ORIGINAL_TRACKING = {
  push_up: "REPS", pull_up: "REPS", chin_up: "REPS", bench_dip: "REPS", bodyweight_squat: "REPS", lunge: "REPS", glute_bridge: "REPS",
  band_lateral_walk: "REPS", plank: "TIME", side_plank: "TIME", crunch: "REPS", russian_twist: "REPS", hanging_leg_raise: "REPS",
  mountain_climber: "TIME", bicycle_crunch: "REPS", dead_bug: "REPS", incline_push_up: "REPS", superman: "REPS",
  inverted_row: "REPS", back_extension: "REPS", walking_lunge: "REPS", chest_dip: "REPS", step_up: "REPS",
};

// Default first step when the variant brings its own kind of equipment and no specific setup.
const SETUP = {
  MACHINE: "Ajusta el asiento y los apoyos de la máquina a tu altura antes de empezar.",
  CABLE: "Coloca la polea a la altura adecuada y elige el accesorio de agarre.",
  RESISTANCE_BAND: "Ancla la banda en un punto firme y comprueba que no está dañada.",
  KETTLEBELL: "Elige una kettlebell que puedas controlar en todo el recorrido.",
};
const SETUP_FAMILIES_SKIP = new Set(["cardio_machines", "cardio_free", "mobility", "stretch", "conditioning", "kettlebell_ballistic"]);

const norm = (s) => s.normalize("NFD").replace(/\p{Mn}/gu, "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
const fail = (msg) => { throw new Error(msg); };

const out = originals.map((e) => ({
  ...e,
  tracking: ORIGINAL_TRACKING[e.id] ?? (e.type === "CARDIO" ? "TIME" : "WEIGHT_REPS"),
  aliases: ORIGINAL_ALIASES[e.id] ?? [],
}));
const ids = new Set(out.map((e) => e.id));
const names = new Map(out.map((e) => [norm(e.name), e.id]));

// Extra variants join their family (same technique text).
const allFamilies = [...families1, ...families2, ...families3].map((f) => ({ ...f, variants: [...f.variants] }));
for (const x of extensions) {
  const target = allFamilies.find((f) => f.key === x.extend) ?? fail("unknown family ${x.extend}");
  target.variants.push(...x.variants);
}

for (const f of allFamilies) {
  for (const v of f.variants) {
    const [id, name, en, equipment, o = {}] = v;
    if (ids.has(id)) fail(`duplicate id ${id}`);
    const n = norm(name);
    if (names.has(n)) fail(`duplicate name "${name}" (${id} vs ${names.get(n)})`);
    const primary = o.primary ?? f.primary;
    const secondary = (o.secondary ?? f.secondary ?? []).filter((m) => !primary.includes(m));
    const stabilizer = (o.stab ?? f.stab ?? []).filter((m) => !primary.includes(m) && !secondary.includes(m));
    const type = o.type ?? f.type;
    const tracking = o.tracking ?? f.tracking;
    const difficulty = o.diff ?? f.diff;
    for (const m of [...primary, ...secondary, ...stabilizer]) if (!MUSCLES.has(m)) fail(`${id}: unknown muscle ${m}`);
    for (const q of equipment) if (!EQUIPMENT.has(q)) fail(`${id}: unknown equipment ${q}`);
    if (!TYPES.has(type)) fail(`${id}: unknown type ${type}`);
    if (!TRACKING.has(tracking)) fail(`${id}: unknown tracking ${tracking}`);
    if (!DIFF.has(difficulty)) fail(`${id}: unknown difficulty ${difficulty}`);
    if (primary.length === 0) fail(`${id}: no primary muscle`);

    const setup = o.setup ?? (SETUP_FAMILIES_SKIP.has(f.key) ? null : equipment.map((q) => SETUP[q]).find(Boolean) ?? null);
    const instructions = setup ? [setup, ...f.steps] : [...f.steps];
    out.push({
      id, name, type, difficulty, camera: false, media: false,
      equipment, description: o.desc ?? f.desc, instructions, tips: f.tips, mistakes: f.mistakes,
      primary, secondary, stabilizer, tracking,
      aliases: [en, f.group, ...(f.tags ?? []), ...(o.tags ?? [])].filter((a, i, all) => a && all.indexOf(a) === i),
      generated: true,
    });
    ids.add(id);
    names.set(n, id);
  }
}

// Compact, readable output: one exercise per line block.
const lines = ["{", `  "version": ${VERSION},`, `  "muscles": [`];
current.muscles.forEach((m, i) => lines.push(`    ${JSON.stringify(m)}${i < current.muscles.length - 1 ? "," : ""}`));
lines.push("  ],", `  "exercises": [`);
out.forEach((e, i) => lines.push(`    ${JSON.stringify(e)}${i < out.length - 1 ? "," : ""}`));
lines.push("  ]", "}", "");
writeFileSync(OUT, lines.join("\n"), "utf8");

const byType = {}; const byTracking = {};
out.forEach((e) => { byType[e.type] = (byType[e.type] ?? 0) + 1; byTracking[e.tracking] = (byTracking[e.tracking] ?? 0) + 1; });
console.log(`catalog v${VERSION}: ${out.length} exercises (${originals.length} originales + ${out.length - originals.length} nuevos)`);
console.log("por tipo:", byType);
console.log("por registro:", byTracking);
