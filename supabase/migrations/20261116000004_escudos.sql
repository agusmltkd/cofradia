-- Escudo de cada hermandad.
-- Se guarda en un bucket público de Storage ('escudos'), en una carpeta por
-- hermandad: escudos/<hermandad_id>/escudo-<marca de tiempo>.png
-- Solo el administrador de esa hermandad (o un superadmin) puede subirlo o borrarlo.

alter table public.hermandades add column if not exists escudo_url text;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('escudos', 'escudos', true, 1048576, array['image/png', 'image/jpeg', 'image/webp'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

create or replace function public.carpeta_hermandad(nombre text) returns uuid
language sql stable set search_path = public as $$
  select case
    when (storage.foldername(nombre))[1] ~ '^[0-9a-f-]{36}$' then ((storage.foldername(nombre))[1])::uuid
  end
$$;

create policy "escudos subir" on storage.objects for insert to authenticated
  with check (bucket_id = 'escudos' and public.es_admin(public.carpeta_hermandad(name)));
create policy "escudos cambiar" on storage.objects for update to authenticated
  using (bucket_id = 'escudos' and public.es_admin(public.carpeta_hermandad(name)))
  with check (bucket_id = 'escudos' and public.es_admin(public.carpeta_hermandad(name)));
create policy "escudos borrar" on storage.objects for delete to authenticated
  using (bucket_id = 'escudos' and public.es_admin(public.carpeta_hermandad(name)));
