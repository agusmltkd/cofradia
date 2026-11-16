// Prueba de extremo a extremo del backend: activa un código, sincroniza y
// verifica la firma del token con la MISMA clave pública que lleva la app.
//
//   node supabase/prueba-backend.mjs ABCD-1234
//
// Se usa desde el workflow "Prueba del backend" (Actions → Run workflow).
// Ojo: consume un uso del código y deja un dispositivo dado de alta; bórralo
// después desde el panel (Dispositivos → Dar de baja).
import { readFileSync } from "node:fs";
import { createPublicKey, verify } from "node:crypto";

const props = Object.fromEntries(
  readFileSync(new URL("../android/gradle.properties", import.meta.url), "utf8")
    .split("\n").filter((l) => /^compas\.\w+=/.test(l))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1).trim()]),
);
const URL_BASE = props["compas.supabaseUrl"];
const CLAVE = props["compas.supabaseKey"];
const PUB = props["compas.clavePublicaLicencia"];
const codigo = process.argv[2];
if (!codigo) throw new Error("Falta el código");

async function llamar(fn, cuerpo) {
  const r = await fetch(`${URL_BASE}/functions/v1/${fn}`, {
    method: "POST",
    headers: { "content-type": "application/json", apikey: CLAVE },
    body: JSON.stringify(cuerpo),
  });
  const j = await r.json();
  if (!r.ok) throw new Error(`${fn}: ${r.status} ${JSON.stringify(j)}`);
  return j;
}

function comprobarToken(token) {
  const [cuerpo, firma] = token.split(".");
  const ok = verify(
    "sha256",
    Buffer.from(cuerpo),
    { key: createPublicKey({ key: Buffer.from(PUB, "base64"), format: "der", type: "spki" }), dsaEncoding: "ieee-p1363" },
    Buffer.from(firma, "base64url"),
  );
  if (!ok) throw new Error("FIRMA NO VÁLIDA");
  const carga = JSON.parse(Buffer.from(cuerpo, "base64url").toString("utf8"));
  const dias = ((carga.exp - Date.now() / 1000) / 86400).toFixed(1);
  console.log(`  firma OK · ${carga.hn} · rol ${carga.rol} · sección ${carga.sec ?? "-"} · caduca en ${dias} días`);
}

console.log("1. activar");
const a = await llamar("activar", { codigo, nombre: "Prueba automática", version_app: "ci" });
comprobarToken(a.token);
console.log(`  ${a.itinerarios.length} itinerarios: ${a.itinerarios.map((i) => i.nombre).join(", ")}`);

console.log("2. sincronizar");
const s = await llamar("sincronizar", { dispositivo_id: a.dispositivo.id, secreto: a.dispositivo.secreto, version_app: "ci" });
comprobarToken(s.token);

console.log("3. credenciales falsas deben fallar");
try {
  await llamar("sincronizar", { dispositivo_id: a.dispositivo.id, secreto: "x".repeat(43) });
  throw new Error("¡Aceptó un secreto falso!");
} catch (e) {
  if (String(e).includes("¡Aceptó")) throw e;
  console.log("  rechazado, bien");
}
console.log(`\nTodo correcto. Dispositivo de prueba: ${a.dispositivo.id}`);
