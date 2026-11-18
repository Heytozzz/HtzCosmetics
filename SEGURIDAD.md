# HtzCosmetics: checklist de seguridad

Revisado el 2026-11-18, sobre el código de la Fase 9. Cada punto indica **dónde** está la protección y **qué
prueba** lo comprueba. Para revisar de nuevo: `gradlew build :tools:test` (pruebas unitarias, incluida la
política de código) y `cd backend && npm test`.

## 1. Los cosméticos son solo datos

| ✔ | Requisito | Dónde | Prueba |
|---|---|---|---|
| ✔ | Nada de lo descargado se ejecuta: solo PNG y nuestro JSON (modelo, animación, manifiesto) | `core/manifest`, `core/model`, `core/png` | `ManifestParserTest`, `ModelParserTest` |
| ✔ | Prohibidos en el código del mod: `ObjectInputStream`, `ScriptEngine`, `Class.forName`, `ClassLoader`, reflexión, `MethodHandles`, `Runtime.exec`/`ProcessBuilder`, `System.load` (librerías nativas), `java.awt.Desktop` (abrir archivos con el sistema), `Unsafe` | todo `src/main/java` | `SourcePolicyTest` (falla la compilación si aparece alguno) |
| ✔ | El núcleo (`core/`) no depende de Minecraft ni de los loaders | `core/` | `SourcePolicyTest` |
| ✔ | Animaciones declarativas: solo keyframes numéricos, sin expresiones; el conversor rechaza Molang | `core/model/AnimationParser`, `tools/BbAnimationConverter` | `AnimationParserTest`, pruebas de tools |

## 2. Firma e integridad

| ✔ | Requisito | Dónde | Prueba |
|---|---|---|---|
| ✔ | Manifiesto firmado con Ed25519; solo las claves **públicas** están en el mod (principal + respaldo) | `core/crypto/TrustedKeys`, `SignatureVerifier` | `SignatureVerifierTest` |
| ✔ | SHA-256 de cada archivo; si no coincide, ese cosmético se descarta | `core/source/CachingRemoteSource`, `AssetCache` | `CachingRemoteSourceTest`, `CacheTest` |
| ✔ | Si la firma falla, no se usa nada | `ManifestParser` | `ManifestParserTest` |
| ✔ | Anti-rollback: nunca se acepta un manifiesto más antiguo que el último aceptado (también tras reiniciar) | `ManifestParser`, `ManifestStore` | `ManifestParserTest`, `CacheTest` |
| ✔ | Caducidad del manifiesto (180 días por defecto) | `tools/BuildSite`, `ManifestParser` | `ManifestParserTest` |
| ✔ | La caché en disco se vuelve a verificar en cada uso; los nombres de archivo salen del hash, no del servidor | `AssetCache`, `ManifestStore` | `CacheTest` |
| ✔ | La carpeta local de desarrollo (`-Dhtzcosmetics.devSite`) pasa la misma verificación | `LocalDirectorySource` | `CachingRemoteSourceTest` |

## 3. Validación estricta

| ✔ | Requisito | Dónde | Prueba |
|---|---|---|---|
| ✔ | PNG: magic bytes, 8 bits RGBA sin entrelazar, máx. 256×256 px, 256 KB y 1024 chunks; decodificador propio en Java con límite de descompresión (sin bombas zip); no se usa el decodificador nativo de Minecraft | `core/png/SafePngDecoder`, `CosmeticLibrary.toNativeImage` | `SafePngDecoderTest` (archivos maliciosos) |
| ✔ | JSON: parser propio, sin deserialización polimórfica; profundidad 32, textos de 256, 4096 entradas por objeto, 100 000 valores | `core/json/StrictJsonParser`, `Limits` | `StrictJsonParserTest` |
| ✔ | Modelos: máx. 32 huesos, 256 cubos, coordenadas y tamaños acotados | `ModelParser`, `Limits` | `ModelParserTest` |
| ✔ | Animaciones: máx. 8 clips, 64 keyframes por canal, 4096 en total, duración 0,05–60 s, valores acotados | `AnimationParser`, `Limits` | `AnimationParserTest` |
| ✔ | Tamaños: manifiesto 512 KB, modelo 128 KB, textura 256 KB, firma 256 B; máx. 2000 cosméticos | `Limits` | varias |

## 4. Red

| ✔ | Requisito | Dónde | Prueba |
|---|---|---|---|
| ✔ | Solo HTTPS y solo a dos dominios: `heytozzz.github.io` y `htzcosmetics.htzcosmetics-backend.workers.dev` | `core/net/Endpoints`, `SecureHttp` | `SecureHttpTest` |
| ✔ | No se siguen redirecciones; URLs con usuario, fragmento u otros puertos se bloquean | `SecureHttp` | `SecureHttpTest` |
| ✔ | Timeouts de conexión y de petición; la respuesta se corta al pasar el límite (no se carga entera) | `SecureHttp.readCapped` | `SecureHttpTest` |
| ✔ | Caché en disco limitada a 50 MB | `AssetCache` | `CacheTest` |
| ✔ | El token de acceso de Minecraft y la clave privada del jugador nunca salen del PC; el backend solo recibe una firma del certificado de Mojang | `AuthClient` | `AuthClientTest`, `backend/test` |
| ✔ | Backend: valida UUID e ids con expresiones estrictas, límites por IP (AUTH, READ, FRESH, WRITE), solo guarda UUID → lista de ids | `backend/src/index.js` | `backend/test/worker.test.js` (18) |
| ✔ | Señal de cambio con cooldown: envío cada 5 s; recepción 10 s por jugador y 12 por minuto en total; backend 30/min por IP | `ChangeSignalPolicy`, backend | `ChangeSignalPolicyTest`, gametest |

## 5. Ante cualquier fallo

| ✔ | Requisito | Dónde |
|---|---|---|
| ✔ | Se ignora en silencio, se registra en el log y el juego sigue: los errores de red, firma o validación nunca cierran Minecraft | `CosmeticLibrary`, `RemoteSync`, `OwnCosmetics` |
| ✔ | Las descargas y peticiones van en hilos de fondo; el render nunca espera a la red | `CosmeticLibrary`, `RemoteSync` |

## Riesgos residuales

Ordenados de más a menos importante. Ninguno permite ejecutar código en el PC de un jugador.

1. **Tu clave privada (`~/.htzcosmetics/keys`).** Está protegida con permisos de archivo, pero no cifrada con
   contraseña. Un virus en tu PC podría robarla y firmar cosméticos falsos. Aun así seguirían siendo solo
   datos (modelos y PNG validados), pero podrían ser imágenes ofensivas. **Qué hacer:** guarda
   `backup.key` fuera del PC (USB o gestor de contraseñas). Si la principal se filtra, publica una versión
   del mod que solo confíe en la de respaldo.
2. **Tus cuentas (GitHub, Cloudflare, Modrinth/CurseForge).** Quien entre en ellas no puede firmar
   cosméticos, pero **sí podría subir un jar del mod modificado**, que es código. **Qué hacer:** activa la
   verificación en dos pasos (2FA) en las tres.
3. **Caducidad del manifiesto.** El sitio publicado caduca a los 180 días (aprox. **mayo de 2027**). Si no
   vuelves a ejecutar `gradlew htzPublishSite` antes, los cosméticos dejan de verse (no es inseguro, pero
   sí una avería).
4. **Cuota gratuita de Cloudflare.** Alguien podría gastar las 100 000 peticiones diarias del plan
   gratuito. El mod lo aguanta (no ve cosméticos ajenos hasta el día siguiente), pero los jugadores
   notarían la caída.
5. **Privacidad.** El backend ve tu IP y los UUID de los jugadores que tienes cerca cuando pide sus
   cosméticos, así que podría deducir quién juega con quién. Cloudflare también ve las IPs. No guardamos
   nada de eso (solo UUID → ids), pero conviene decirlo en una nota de privacidad.
6. **El bit de la señal de cambio (0x80 de las capas de skin)** es visible para el servidor. Un servidor
   podría saber que usas el mod. Los servidores vanilla y Paper lo ignoran; un anticheat muy estricto
   podría quejarse, aunque no lo hemos visto.
7. **Certificados de Mojang robados.** Si alguien roba el certificado de un jugador de su PC (dura unas
   48 h), podría cambiar los cosméticos equipados de ese jugador hasta que caduque. El daño máximo es
   ponerle o quitarle cosméticos.
8. **Rotación de claves de Mojang.** Si Mojang cambia sus claves, el inicio de sesión del backend falla
   hasta que se ejecute `backend/scripts/update-mojang-keys.mjs` y se redespliegue. Mientras tanto, cada
   jugador sigue viendo sus cosméticos en local.
9. **IPs compartidas.** Los límites son por IP, así que muchos jugadores tras la misma IP (un colegio, una
   red móvil) pueden llegar al límite antes. El efecto es que los cambios tardan algo más en verse.
10. **Dependencias de compilación** (Gradle, Loom, Stonecutter). Se descargan de repositorios oficiales;
    un ataque a esos repositorios afectaría a cualquier mod. Ninguna va dentro del jar salvo nuestro código.
