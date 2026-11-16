// Utilidades compartidas por `activar` y `sincronizar`.
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";

export const DIAS_OFFLINE = 30;

export const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json; charset=utf-8" },
  });
}

/** Error con código estable: la app traduce el código a un mensaje. */
export function error(codigo: string, mensaje: string, status = 400): Response {
  return json({ error: codigo, mensaje }, status);
}

export function admin(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );
}

const b64url = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

export async function sha256Hex(texto: string): Promise<string> {
  const h = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
  return [...new Uint8Array(h)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function secretoAleatorio(): string {
  const b = new Uint8Array(32);
  crypto.getRandomValues(b);
  return b64url(b);
}

let claveCache: CryptoKey | null = null;

async function clavePrivada(db: SupabaseClient): Promise<CryptoKey> {
  if (claveCache) return claveCache;
  const { data, error } = await db.rpc("clave_firma", { clave: "licencias-v1" });
  if (error || !data) throw new Error("No hay clave de firma configurada");
  claveCache = await crypto.subtle.importKey(
    "jwk",
    data as JsonWebKey,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  return claveCache;
}

export interface CargaLicencia {
  v: 1;
  kid: string;
  did: string; // dispositivo
  hid: string; // hermandad
  hs: string; // slug
  hn: string; // nombre
  rol: string;
  sec: string | null;
  iat: number; // segundos epoch
  exp: number; // segundos epoch: el token deja de valer aquí
  lic: string; // fin de la licencia (YYYY-MM-DD)
}

/**
 * Token compacto: base64url(JSON) "." base64url(firma ECDSA P-256 r||s).
 * La app lo verifica con la clave pública que lleva embebida, sin red.
 */
export async function firmar(db: SupabaseClient, carga: CargaLicencia): Promise<string> {
  const cuerpo = b64url(new TextEncoder().encode(JSON.stringify(carga)));
  const firma = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    await clavePrivada(db),
    new TextEncoder().encode(cuerpo),
  );
  return `${cuerpo}.${b64url(new Uint8Array(firma))}`;
}

/** Caducidad del token: 30 días o el fin de la licencia, lo que llegue antes. */
export function caducidad(finLicencia: string, ahora = Date.now()): number {
  const fin = Date.parse(`${finLicencia}T23:59:59Z`);
  return Math.floor(Math.min(ahora + DIAS_OFFLINE * 86400_000, fin) / 1000);
}

/** Todo lo que un dispositivo necesita para funcionar 30 días sin red. */
export async function paquete(db: SupabaseClient, hermandadId: string) {
  const { data: h, error: e1 } = await db
    .from("hermandades")
    .select("id, slug, nombre, nombre_corto, ciudad, dia_salida, color, escudo_url")
    .eq("id", hermandadId)
    .single();
  if (e1 || !h) throw new Error("Hermandad no encontrada");
  const { data: its, error: e2 } = await db
    .from("itinerarios")
    .select("id, nombre, orden, version, datos, actualizado")
    .eq("hermandad_id", hermandadId)
    .eq("publicado", true)
    .order("orden");
  if (e2) throw e2;
  return { hermandad: h, itinerarios: its ?? [] };
}
