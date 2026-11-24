// FITNEX catalogue, part 4: extra variants that add real variety to existing families
// (they reuse the family's technique text through `extend`).
import { PEC, DA, DL, DP, BI, TRI, FA, LAT, TRAP, RHO, ERE, ABS, OBL, GMAX, GMED, QUA, HAM, ADD, CAL, HIP } from "./families-1.mjs";

export const extensions = [
  { extend: "chest_fly", variants: [
    ["incline_cable_fly", "Aperturas inclinadas en polea", "Incline cable fly", ["CABLE", "BENCH"]],
  ] },
  { extend: "chest_press", variants: [
    ["dumbbell_squeeze_press", "Press de pecho apretando mancuernas", "Dumbbell squeeze press", ["DUMBBELL", "BENCH"], { desc: "Mancuernas juntas apretándose entre sí durante todo el press." }],
  ] },
  { extend: "pulldown", variants: [
    ["behind_neck_pulldown", "Jalón tras nuca", "Behind-the-neck pulldown", ["CABLE"], { diff: "ADVANCED", desc: "Solo con buena movilidad de hombro y sin dolor." }],
  ] },
  { extend: "row", variants: [
    ["rope_cable_row", "Remo en polea baja con cuerda", "Rope cable row", ["CABLE"], { primary: [RHO, DP], secondary: [LAT, BI] }],
    ["band_face_pull", "Face pull con banda", "Band face pull", ["RESISTANCE_BAND"], { primary: [DP, RHO], secondary: [TRAP] }],
  ] },
  { extend: "shoulder_press", variants: [
    ["half_kneeling_landmine_press", "Press landmine de rodillas a una mano", "Half-kneeling landmine press", ["BARBELL"], { primary: [DA], secondary: [PEC, TRI], stab: [OBL] }],
    ["bradford_press", "Press Bradford", "Bradford press", ["BARBELL", "RACK"], { diff: "ADVANCED", desc: "La barra pasa de delante a detrás de la cabeza sin estirar del todo los brazos." }],
  ] },
  { extend: "curl", variants: [
    ["barbell_21s", "Curl 21", "21s barbell curl", ["BARBELL"], { diff: "INTERMEDIATE", desc: "7 repeticiones de abajo a la mitad, 7 de la mitad arriba y 7 completas." }],
    ["lying_cable_curl", "Curl de bíceps tumbado en polea", "Lying cable curl", ["CABLE"] ],
  ] },
  { extend: "triceps_ext", variants: [
    ["ez_overhead_extension", "Extensión de tríceps tras nuca con barra Z", "EZ bar overhead extension", ["EZ_BAR"] ],
    ["v_bar_pushdown", "Extensión de tríceps con barra V", "V-bar pushdown", ["CABLE"] ],
    ["cross_cable_triceps_extension", "Extensión de tríceps cruzada en polea", "Cross-body cable triceps extension", ["CABLE"], { diff: "INTERMEDIATE" }],
  ] },
  { extend: "forearm", variants: [
    ["finger_curl", "Curl de dedos", "Finger curl", ["BARBELL"] ],
  ] },
  { extend: "squat", variants: [
    ["barbell_hack_squat", "Hack squat con barra", "Barbell hack squat", ["BARBELL"], { diff: "ADVANCED", desc: "La barra se sujeta detrás de las piernas." }],
    ["reverse_nordic", "Nórdico inverso", "Reverse Nordic", ["BODYWEIGHT"], { diff: "INTERMEDIATE", type: "CALISTHENICS", tracking: "REPS", primary: [QUA], secondary: [HIP], desc: "De rodillas, inclinas el cuerpo recto hacia atrás estirando el cuádriceps." }],
    ["kettlebell_goblet_squat", "Sentadilla goblet con kettlebell", "Kettlebell goblet squat", ["KETTLEBELL"], { diff: "BEGINNER" }],
  ] },
  { extend: "lunge_family", variants: [
    ["step_down", "Descenso controlado del cajón", "Step-down", ["BODYWEIGHT", "OTHER"], { tracking: "REPS", primary: [QUA], secondary: [GMED] }],
    ["barbell_step_up", "Subidas al cajón con barra", "Barbell step-up", ["BARBELL", "OTHER"], { diff: "INTERMEDIATE" }],
    ["goblet_reverse_lunge", "Zancada atrás goblet", "Goblet reverse lunge", ["DUMBBELL"] ],
  ] },
  { extend: "hip_thrust_family", variants: [
    ["band_glute_bridge", "Puente de glúteo con banda", "Banded glute bridge", ["RESISTANCE_BAND"], { tracking: "REPS", secondary: [GMED, HAM] }],
    ["elevated_glute_bridge", "Puente de glúteo con pies elevados", "Feet-elevated glute bridge", ["BODYWEIGHT", "BENCH"], { tracking: "REPS", secondary: [HAM] }],
    ["kettlebell_hip_thrust", "Hip thrust con kettlebell", "Kettlebell hip thrust", ["KETTLEBELL", "BENCH"] ],
  ] },
  { extend: "calf_family", variants: [
    ["pogo_jumps", "Saltos de tobillo (pogo)", "Pogo jumps", ["BODYWEIGHT"], { tracking: "TIME", desc: "Saltos cortos y rápidos con las rodillas casi rectas." }],
    ["dumbbell_seated_calf_raise", "Gemelos sentado con mancuerna", "Seated dumbbell calf raise", ["DUMBBELL", "BENCH"] ],
  ] },
  { extend: "crunch_family", variants: [
    ["standing_cable_crunch", "Crunch de pie en polea", "Standing cable crunch", ["CABLE"], { tracking: "WEIGHT_REPS" }],
    ["medicine_ball_sit_up_throw", "Sit-up con lanzamiento de balón", "Medicine ball sit-up throw", ["MEDICINE_BALL"], { diff: "INTERMEDIATE", secondary: [HIP, DA] }],
    ["dragon_flag", "Dragon flag", "Dragon flag", ["BENCH"], { diff: "ADVANCED", secondary: [HIP, LAT], desc: "Tumbado sujeto al banco, bajas el cuerpo recto como una tabla." }],
    ["hollow_rocks", "Balanceos en hollow", "Hollow rocks", ["BODYWEIGHT"], { diff: "INTERMEDIATE", secondary: [HIP] }],
  ] },
  { extend: "anti_extension", variants: [
    ["plank_leg_lift", "Plancha con elevación de pierna", "Plank leg lift", ["BODYWEIGHT"], { secondary: [GMAX, OBL] }],
    ["star_side_plank", "Plancha lateral en estrella", "Star side plank", ["BODYWEIGHT"], { diff: "ADVANCED", primary: [OBL, GMED] }],
    ["cross_body_mountain_climber", "Escaladores cruzados", "Cross-body mountain climber", ["BODYWEIGHT"], { primary: [OBL, ABS], secondary: [HIP, DA] }],
  ] },
  { extend: "shrug_family", variants: [
    ["incline_dumbbell_shrug", "Encogimientos en banco inclinado", "Incline dumbbell shrug", ["DUMBBELL", "BENCH"], { primary: [TRAP, RHO] }],
  ] },
  { extend: "lower_back", variants: [
    ["alternating_superman", "Superman alterno", "Alternating superman", ["BODYWEIGHT"], { secondary: [GMAX, DP] }],
  ] },
  { extend: "kettlebell_ballistic", variants: [
    ["kettlebell_thruster", "Thruster con kettlebell", "Kettlebell thruster", ["KETTLEBELL"], { primary: [QUA, GMAX, DA], secondary: [TRI, ABS] }],
    ["kettlebell_figure_eight", "Ocho con kettlebell", "Kettlebell figure 8", ["KETTLEBELL"], { primary: [GMAX, OBL], secondary: [HAM, FA] }],
  ] },
  { extend: "conditioning", variants: [
    ["sled_harness_sprint", "Arrastre de trineo con arnés", "Sled harness drag", ["SLED"], { diff: "INTERMEDIATE", tracking: "TIME", primary: [GMAX, QUA], secondary: [HAM, CAL] }],
    ["rope_climb", "Subida de cuerda", "Rope climb", ["ROPE"], { diff: "ADVANCED", primary: [LAT, BI], secondary: [FA, ABS] }],
    ["lateral_hops", "Saltos laterales", "Lateral hops", ["BODYWEIGHT"], { type: "CARDIO", tracking: "TIME", primary: [CAL, GMED], secondary: [QUA] }],
  ] },
  { extend: "cardio_machines", variants: [
    ["stationary_bike_intervals", "Intervalos en bicicleta estática", "Stationary bike intervals", ["CARDIO_MACHINE"], { diff: "INTERMEDIATE", primary: [QUA], secondary: [GMAX, CAL] }],
  ] },
  { extend: "cardio_free", variants: [
    ["jogging", "Trote suave", "Jogging", ["BODYWEIGHT"] ],
    ["nordic_walking", "Marcha nórdica", "Nordic walking", ["OTHER"], { primary: [QUA, LAT], secondary: [TRI, CAL] }],
    ["skating", "Patinaje", "Skating", ["OTHER"], { diff: "INTERMEDIATE", primary: [GMED, QUA], secondary: [GMAX, ADD] }],
  ] },
  { extend: "mobility", variants: [
    ["hip_hinge_drill", "Bisagra de cadera con pica", "Dowel hip hinge drill", ["OTHER"], { primary: [HAM, GMAX], secondary: [ERE] }],
    ["prying_goblet_squat", "Sentadilla goblet con apertura de rodillas", "Prying goblet squat", ["KETTLEBELL"], { primary: [ADD, GMAX], secondary: [QUA] }],
    ["standing_trunk_rotation", "Rotaciones de tronco de pie", "Standing trunk rotations", ["BODYWEIGHT"], { primary: [OBL], secondary: [ERE] }],
    ["hip_internal_rotation_drill", "Rotación interna de cadera sentado", "Seated hip internal rotation", ["BODYWEIGHT"], { primary: [GMED], secondary: [ADD] }],
  ] },
  { extend: "stretch", variants: [
    ["straddle_stretch", "Estiramiento de aductores sentado (piernas abiertas)", "Straddle stretch", ["BODYWEIGHT"], { primary: [ADD], secondary: [HAM] }],
    ["neck_flexion_stretch", "Estiramiento de cuello hacia delante", "Neck flexion stretch", ["BODYWEIGHT"], { primary: [TRAP] }],
    ["seated_piriformis_stretch", "Estiramiento de piriforme sentado", "Seated piriformis stretch", ["BODYWEIGHT"], { primary: [GMAX, GMED] }],
    ["standing_hip_flexor_stretch", "Estiramiento de psoas de pie", "Standing hip flexor stretch", ["BODYWEIGHT"], { primary: [HIP], secondary: [QUA] }],
    ["chest_band_stretch", "Estiramiento de pecho con banda", "Band chest stretch", ["RESISTANCE_BAND"], { primary: [PEC], secondary: [DA] }],
  ] },
];
