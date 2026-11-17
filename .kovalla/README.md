# Kovalla: fork personal de NuvioTV

Esta rama (`kovalla`) no contiene código de la app. Solo lleva:

- `.kovalla/patches/*.patch`: los parches que se aplican sobre un tag estable de
  [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV).
- `.github/workflows/kovalla-release.yml`: compila, firma y publica los APK en las releases de este fork.
- Este README.

La rama `kovalla` es la rama por defecto del fork porque los workflows programados solo se ejecutan
desde la rama por defecto.

## Qué cambia el fork

1. **Instalación lado a lado**: el flavor `full` lleva `applicationIdSuffix = ".kovalla"`
   (`com.nuvio.tv.kovalla`) y la etiqueta visible es «Nuvio Kovalla». `namespace` no se toca.
   Las autoridades de providers (`${applicationId}.fileprovider`) y el instalador del actualizador
   usan `applicationId`, así que siguen apuntando al paquete correcto.
2. **Actualizador propio**: `GITHUB_OWNER=sergioponguta`, `GITHUB_REPO=NuvioTV`.
3. **Hover en carpetas de colección**: si TMDB no devuelve tráiler y la pantalla no pasa un
   `fallbackYtId`, se traduce el id `tmdb:<n>` a IMDb (`tt…`) y se pide el meta a los addons
   (por ejemplo «Trailers ES Latino»), usando `meta.trailerYtIds.firstOrNull()`.
   Se mantienen el debounce, el control de versión de petición, un solo lookup en vuelo y la
   caché negativa.

## Esquema de versiones (importante para el actualizador)

El actualizador compara el **tag** de la release con `BuildConfig.VERSION_NAME` usando semver, y
descarta prereleases cuando el canal es STABLE. Por eso los tags del fork son versiones normales,
sin sufijos `-k2`:

- Primera release de una estable nueva de upstream: tag = versión de upstream (p. ej. `1.1.0`).
- Si se recompila el **mismo** upstream (parche corregido), se sube el patch: `1.1.1`, `1.1.2`…
- `versionName` = tag. `versionCode` = `major*1000000 + minor*10000 + patch*100`
  (`1.1.0` → `1010000`), siempre creciente dentro de este fork y muy por encima del `1077` de upstream.

El workflow calcula ambos y se los pasa a Gradle con `-PkovallaVersionName` y `-PkovallaVersionCode`.
Si alguna vez hay que recompilar a mano, hay que pasar esos dos parámetros con los mismos valores
del tag que se va a publicar.

## Rehacer los parches cuando upstream cambie

Cuando una estable nueva no aplique limpia (el workflow abre un issue con el error), en local:

```bash
git clone --filter=blob:none https://github.com/NuvioMedia/NuvioTV.git /tmp/nuvio-upstream
cd /tmp/nuvio-upstream
git checkout <tag-nuevo>
git am /ruta/al/fork/.kovalla/patches/*.patch    # si falla, resolver a mano
# comprobar que compila:
#   cp local.properties.example? -> usar local.properties con DOVI_NATIVE_ENABLED=false
./gradlew :app:assembleFullRelease -PkovallaVersionName=<tag-nuevo> -PkovallaVersionCode=<code>
# regenerar los parches:
rm -rf /ruta/al/fork/.kovalla/patches/*
git format-patch <tag-nuevo>..HEAD -o /ruta/al/fork/.kovalla/patches
```

Luego commit en la rama `kovalla` del fork y `gh workflow run kovalla-release.yml`.

Mantener los parches pequeños es lo que hace que esto siga siendo barato: no reformatear archivos
ni tocar código que no haga falta.

## Secretos del workflow

| Secreto | Contenido |
| --- | --- |
| `LOCAL_PROPERTIES_BASE64` | `local.properties` en base64 (Supabase URL/anon key, `TMDB_API_KEY`, `DOVI_NATIVE_ENABLED=false`) |
| `KOVALLA_KEYSTORE_BASE64` | keystore PKCS12 en base64 |
| `KOVALLA_KEY_ALIAS` | alias de la clave (`kovalla`) |
| `KOVALLA_KEY_PASSWORD` | contraseña de la clave |
| `KOVALLA_STORE_PASSWORD` | contraseña del keystore |

Ninguno de esos valores debe acabar en el repositorio (es público). El keystore local vive en
`~/.nuvio-kovalla/`.

## Limitaciones conocidas de este build

- `DOVI_NATIVE_ENABLED=false`: no se compila el puente nativo de Dolby Vision (perfil 7 → 8.1)
  porque no hay NDK en el entorno ni en el workflow. Los archivos DoVi se reproducen con lo que
  soporte el dispositivo (capa base HDR10), sin conversión nativa.
- Sin claves de Trakt, Simkl ni MDBList: esas integraciones no funcionan. Supabase y TMDB sí
  están configurados, así que login, colecciones TMDB y metadatos TMDB funcionan.
- Sentry queda sin DSN: no se envían informes de fallos.
