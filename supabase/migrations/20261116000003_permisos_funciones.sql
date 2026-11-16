-- Las funciones de comprobación de permisos solo las necesitan los usuarios
-- autenticados (las usan las políticas RLS). Quitar el permiso por defecto a PUBLIC/anon.
revoke execute on function public.es_superadmin() from public, anon;
revoke execute on function public.es_admin(uuid) from public, anon;
grant execute on function public.es_superadmin() to authenticated, service_role;
grant execute on function public.es_admin(uuid) to authenticated, service_role;
