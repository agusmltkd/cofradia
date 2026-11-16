// POST /functions/v1/sincronizar
// Body: { dispositivo_id, secreto, version_app? }
//
// Renueva el token (otros 30 días) y devuelve los itinerarios publicados.
// La app lo llama siempre que tiene red; si falla, sigue con lo que tiene.
import { admin, caducidad, cors, error, firmar, json, paquete, sha256Hex } from "../_shared/comun.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return error("metodo", "Usa POST", 405);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return error("peticion", "Cuerpo JSON no válido");
  }
  const id = String(body.dispositivo_id ?? "");
  const secreto = String(body.secreto ?? "");
  if (!/^[0-9a-f-]{36}$/.test(id) || secreto.length < 20) return error("credenciales", "Credenciales no válidas", 401);

  const db = admin();
  const { data: d } = await db.from("dispositivos").select("*").eq("id", id).maybeSingle();
  if (!d || d.secreto_hash !== (await sha256Hex(secreto))) {
    return error("credenciales", "Credenciales no válidas", 401);
  }
  if (d.revocado) return error("revocado", "La hermandad ha dado de baja este móvil", 403);

  const { data: lic } = await db.rpc("licencia_vigente", { h: d.hermandad_id }).maybeSingle();
  if (!lic) return error("sin_licencia", "La licencia de la hermandad no está vigente", 402);
  const l = lic as { fin: string };

  await db
    .from("dispositivos")
    .update({
      ultimo_contacto: new Date().toISOString(),
      version_app: body.version_app ? String(body.version_app).slice(0, 20) : d.version_app,
    })
    .eq("id", d.id);

  const p = await paquete(db, d.hermandad_id);
  const ahora = Math.floor(Date.now() / 1000);
  const token = await firmar(db, {
    v: 1,
    kid: "licencias-v1",
    did: d.id,
    hid: d.hermandad_id,
    hs: p.hermandad.slug,
    hn: p.hermandad.nombre,
    rol: d.rol,
    sec: d.seccion,
    iat: ahora,
    exp: caducidad(l.fin),
    lic: l.fin,
  });

  return json({ token, dispositivo: { id: d.id, rol: d.rol, seccion: d.seccion, etiqueta: d.nombre }, ...p, servidor_ahora: ahora });
});
