-- =====================================================================
-- Statio · esquema inicial
--
-- Quién hace qué:
--   · superadmins   → tú. Das de alta hermandades, licencias y administradores.
--   · admins        → el Diputado Mayor de Gobierno (o quien designe la
--                     hermandad). Gestiona itinerarios, códigos y dispositivos
--                     de SU hermandad desde el panel web.
--   · dispositivos  → los móviles de los fiscales/diputados. NO tienen cuenta:
--                     se activan con un código de invitación y reciben un
--                     token de licencia firmado que vale 30 días sin red.
--
-- Los dispositivos nunca hablan con las tablas directamente: lo hacen a
-- través de las Edge Functions `activar` y `sincronizar`, que usan la
-- service role. Por eso RLS solo contempla a superadmins y admins.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- tablas

create table public.hermandades (
  id            uuid primary key default gen_random_uuid(),
  slug          text not null unique check (slug ~ '^[a-z0-9-]{3,40}$'),
  nombre        text not null,
  nombre_corto  text,
  ciudad        text not null default 'Sevilla',
  dia_salida    text,                          -- "Martes Santo"
  color         text not null default '#B8862F' check (color ~ '^#[0-9A-Fa-f]{6}$'),
  interna       boolean not null default false, -- cuentas de prueba/internas
  creada        timestamptz not null default now()
);

create table public.licencias (
  id               uuid primary key default gen_random_uuid(),
  hermandad_id     uuid not null references public.hermandades(id) on delete cascade,
  inicio           date not null default current_date,
  fin              date not null,
  max_dispositivos int  not null default 10 check (max_dispositivos between 1 and 500),
  estado           text not null default 'activa' check (estado in ('activa','suspendida')),
  notas            text,
  creada           timestamptz not null default now(),
  check (fin > inicio)
);
create index on public.licencias (hermandad_id, fin desc);

-- Acceso al panel por email (login con enlace mágico). Guardar el email y no
-- el user_id permite invitar a alguien antes de que haya entrado nunca.
create table public.superadmins (
  email text primary key check (email = lower(email))
);

create table public.admins (
  hermandad_id uuid not null references public.hermandades(id) on delete cascade,
  email        text not null check (email = lower(email)),
  nombre       text,
  creado       timestamptz not null default now(),
  primary key (hermandad_id, email)
);

create table public.itinerarios (
  id           uuid primary key default gen_random_uuid(),
  hermandad_id uuid not null references public.hermandades(id) on delete cascade,
  nombre       text not null,                   -- "Plan Sol", "Itinerario de lluvia"
  orden        int  not null default 0,
  datos        jsonb not null,                  -- {puntos:[], secciones:[]} ver docs/modelo.md
  publicado    boolean not null default false,  -- solo lo publicado llega a los móviles
  version      int not null default 1,
  actualizado  timestamptz not null default now(),
  actualizado_por text
);
create index on public.itinerarios (hermandad_id, orden);

create table public.codigos (
  id           uuid primary key default gen_random_uuid(),
  hermandad_id uuid not null references public.hermandades(id) on delete cascade,
  codigo       text not null unique check (codigo ~ '^[A-Z0-9]{4}-[A-Z0-9]{4}$'),
  etiqueta     text not null,                   -- "Fiscal de cruz de guía"
  rol          text not null default 'fiscal' check (rol in ('diputado_mayor','fiscal','consulta')),
  seccion      text,                            -- id de sección preseleccionada ("cruz", "cristo"...)
  usos_max     int  not null default 1 check (usos_max between 1 and 200),
  usos         int  not null default 0,
  caduca       timestamptz,
  activo       boolean not null default true,
  creado       timestamptz not null default now()
);

create table public.dispositivos (
  id              uuid primary key default gen_random_uuid(),
  hermandad_id    uuid not null references public.hermandades(id) on delete cascade,
  codigo_id       uuid references public.codigos(id) on delete set null,
  nombre          text not null default 'Móvil',
  modelo          text,
  rol             text not null,
  seccion         text,
  secreto_hash    text not null,                -- sha256 hex del secreto del dispositivo
  version_app     text,
  creado          timestamptz not null default now(),
  ultimo_contacto timestamptz not null default now(),
  revocado        boolean not null default false
);
create index on public.dispositivos (hermandad_id) where not revocado;

-- ---------------------------------------------------------------- helpers

create or replace function public.email_actual() returns text
language sql stable set search_path = public as $$
  select lower(coalesce(auth.jwt() ->> 'email', ''))
$$;

create or replace function public.es_superadmin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from superadmins where email = public.email_actual())
$$;

create or replace function public.es_admin(h uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select public.es_superadmin()
      or exists (select 1 from admins where hermandad_id = h and email = public.email_actual())
$$;

-- Licencia vigente de una hermandad (la que acaba más tarde, si está activa).
create or replace function public.licencia_vigente(h uuid)
returns table (id uuid, fin date, max_dispositivos int)
language sql stable security definer set search_path = public as $$
  select l.id, l.fin, l.max_dispositivos
  from licencias l
  where l.hermandad_id = h and l.estado = 'activa'
    and l.inicio <= current_date and l.fin >= current_date
  order by l.fin desc
  limit 1
$$;

-- Versión e instante de actualización automáticos en itinerarios.
create or replace function public.tocar_itinerario() returns trigger
language plpgsql set search_path = public as $$
begin
  if tg_op = 'UPDATE' and new.datos is distinct from old.datos then
    new.version := old.version + 1;
  end if;
  new.actualizado := now();
  new.actualizado_por := nullif(public.email_actual(), '');
  return new;
end $$;

create trigger itinerarios_tocar before insert or update on public.itinerarios
for each row execute function public.tocar_itinerario();

-- ---------------------------------------------------------------- RLS

alter table public.hermandades  enable row level security;
alter table public.licencias    enable row level security;
alter table public.superadmins  enable row level security;
alter table public.admins       enable row level security;
alter table public.itinerarios  enable row level security;
alter table public.codigos      enable row level security;
alter table public.dispositivos enable row level security;

-- hermandades: el admin ve la suya; solo superadmin crea/borra.
create policy "ver hermandad" on public.hermandades for select to authenticated
  using (public.es_admin(id));
create policy "editar hermandad" on public.hermandades for update to authenticated
  using (public.es_admin(id)) with check (public.es_admin(id));
create policy "crear hermandad" on public.hermandades for insert to authenticated
  with check (public.es_superadmin());
create policy "borrar hermandad" on public.hermandades for delete to authenticated
  using (public.es_superadmin());

-- licencias: el admin las ve; solo superadmin las gestiona.
create policy "ver licencias" on public.licencias for select to authenticated
  using (public.es_admin(hermandad_id));
create policy "gestionar licencias" on public.licencias for all to authenticated
  using (public.es_superadmin()) with check (public.es_superadmin());

-- superadmins: cada uno puede comprobar si lo es.
create policy "verse a sí mismo" on public.superadmins for select to authenticated
  using (email = public.email_actual());

-- admins: el admin ve a los de su hermandad; superadmin gestiona.
create policy "ver admins" on public.admins for select to authenticated
  using (public.es_admin(hermandad_id));
create policy "gestionar admins" on public.admins for all to authenticated
  using (public.es_superadmin()) with check (public.es_superadmin());

-- itinerarios, códigos y dispositivos: el admin de la hermandad, completo.
create policy "itinerarios admin" on public.itinerarios for all to authenticated
  using (public.es_admin(hermandad_id)) with check (public.es_admin(hermandad_id));
create policy "codigos admin" on public.codigos for all to authenticated
  using (public.es_admin(hermandad_id)) with check (public.es_admin(hermandad_id));
create policy "dispositivos ver" on public.dispositivos for select to authenticated
  using (public.es_admin(hermandad_id));
create policy "dispositivos revocar" on public.dispositivos for update to authenticated
  using (public.es_admin(hermandad_id)) with check (public.es_admin(hermandad_id));
create policy "dispositivos borrar" on public.dispositivos for delete to authenticated
  using (public.es_admin(hermandad_id));

-- Las funciones auxiliares no deben poder llamarse de forma anónima.
revoke execute on function public.licencia_vigente(uuid) from anon, public;
grant execute on function public.licencia_vigente(uuid) to authenticated, service_role;
revoke execute on function public.es_superadmin() from anon;
revoke execute on function public.es_admin(uuid) from anon;
