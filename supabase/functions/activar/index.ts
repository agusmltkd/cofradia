// POST /functions/v1/activar
// Body: { codigo: "ABCD-1234", nombre?: "Móvil de Juan", modelo?: "Pixel 8", version_app?: "1.0.0" }
//
// Canjea un código de invitación: da de alta el dispositivo, le entrega un
// secreto (que solo viaja esta vez) y el primer token de licencia.
import { admin, caducidad, cors, error, firmar, json, paquete, secretoAleatorio, sha256Hex } from "../_shared/comun.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return error("metodo", "Usa POST", 405);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return error("peticion", "Cuerpo JSON no válido");
  }
  const codigo = String(body.codigo ?? "").trim().toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (codigo.length !== 8) return error("codigo_formato", "El código tiene 8 caracteres, como ABCD-1234");
  const codigoFmt = `${codigo.slice(0, 4)}-${codigo.slice(4)}`;

  const db = admin();
  const { data: c } = await db.from("codigos").select("*").eq("codigo", codigoFmt).maybeSingle();
  if (!c || !c.activo) return error("codigo_invalido", "Ese código no existe o está desactivado", 404);
  if (c.caduca && Date.parse(c.caduca) < Date.now()) return error("codigo_caducado", "Ese código ha caducado", 410);
  if (c.usos >= c.usos_max) return error("codigo_agotado", "Ese código ya se ha usado todas las veces permitidas", 410);

  const { data: lic } = await db.rpc("licencia_vigente", { h: c.hermandad_id }).maybeSingle();
  if (!lic) return error("sin_licencia", "La hermandad no tiene una licencia vigente", 402);
  const l = lic as { id: string; fin: string; max_dispositivos: number };

  const { count } = await db
    .from("dispositivos")
    .select("id", { count: "exact", head: true })
    .eq("hermandad_id", c.hermandad_id)
    .eq("revocado", false);
  if ((count ?? 0) >= l.max_dispositivos) {
    return error("limite_dispositivos", `La licencia permite ${l.max_dispositivos} móviles y ya están todos en uso`, 409);
  }

  // Reservar el uso del código de forma atómica (evita dos canjes simultáneos).
  const { data: reservado } = await db
    .from("codigos")
    .update({ usos: c.usos + 1 })
    .eq("id", c.id)
    .eq("usos", c.usos)
    .select("id")
    .maybeSingle();
  if (!reservado) return error("reintentar", "Inténtalo de nuevo", 409);

  const secreto = secretoAleatorio();
  const { data: d, error: eD } = await db
    .from("dispositivos")
    .insert({
      hermandad_id: c.hermandad_id,
      codigo_id: c.id,
      nombre: String(body.nombre ?? c.etiqueta).slice(0, 60),
      modelo: body.modelo ? String(body.modelo).slice(0, 60) : null,
      rol: c.rol,
      seccion: c.seccion,
      secreto_hash: await sha256Hex(secreto),
      version_app: body.version_app ? String(body.version_app).slice(0, 20) : null,
    })
    .select("id")
    .single();
  if (eD || !d) return error("interno", "No se pudo registrar el móvil", 500);

  const p = await paquete(db, c.hermandad_id);
  const ahora = Math.floor(Date.now() / 1000);
  const token = await firmar(db, {
    v: 1,
    kid: "licencias-v1",
    did: d.id,
    hid: c.hermandad_id,
    hs: p.hermandad.slug,
    hn: p.hermandad.nombre,
    rol: c.rol,
    sec: c.seccion,
    iat: ahora,
    exp: caducidad(l.fin),
    lic: l.fin,
  });

  return json({
    token,
    dispositivo: { id: d.id, secreto, rol: c.rol, seccion: c.seccion, etiqueta: c.etiqueta },
    ...p,
    servidor_ahora: ahora,
  });
});
