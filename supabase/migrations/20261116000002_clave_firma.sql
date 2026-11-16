-- Clave privada con la que las Edge Functions firman los tokens de licencia.
--
-- Vive en un esquema que la API no expone y solo se puede leer con la
-- service role (que únicamente tienen las Edge Functions). La clave en sí NO
-- está en el repositorio: se insertó directamente en la base de datos.
-- Para rotarla, ver docs/GUIA.md → "Rotar la clave de licencias".

create schema if not exists privado;
revoke all on schema privado from public, anon, authenticated;

create table privado.claves (
  id     text primary key,          -- 'licencias-v1'
  jwk    jsonb not null,             -- clave privada EC P-256 en formato JWK
  creada timestamptz not null default now()
);
revoke all on privado.claves from public, anon, authenticated;

create or replace function public.clave_firma(clave text default 'licencias-v1')
returns jsonb
language sql stable security definer set search_path = privado, public as $$
  select jwk from privado.claves where id = clave
$$;
revoke execute on function public.clave_firma(text) from public, anon, authenticated;
grant execute on function public.clave_firma(text) to service_role;
