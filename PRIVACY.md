# Privacy / Privacidad

*Español más abajo.*

## English

HtzCosmetics talks to two addresses only:

- `heytozzz.github.io` (GitHub Pages): downloads the signed list of cosmetics and their files.
  GitHub may log your IP address like any website.
- `htzcosmetics.htzcosmetics-backend.workers.dev` (Cloudflare Workers): saves which cosmetics you wear and
  tells the mod which cosmetics nearby players wear.

**What we store:** your Minecraft UUID, the ids of the cosmetics you have equipped, and when you last changed
them. Nothing else: no name, no email, no IP address, no chat, no server address.

**What is sent but not stored:**

- When you sign in, your game proves who you are with the player certificate Mojang gives every account. Your
  Minecraft access token and your private key never leave your computer.
- To show other players' cosmetics, the mod sends their UUIDs (players near you). Together with your IP
  address, this could reveal who plays near whom; we do not record it.
- Cloudflare, which runs the server, sees IP addresses to block abuse (rate limits) and may keep short-lived
  logs under its own privacy policy.

**Changing your cosmetics** also flips one unused bit of your skin settings so nearby players with the mod
refresh at once. Servers can see that bit, which tells them you use the mod.

**Deleting your data:** unequipping everything in the wardrobe deletes your entry. You can also ask on the
[issue tracker](https://github.com/Heytozzz/HtzCosmetics/issues) and we will delete your entry.
The server also supports deletion directly (`DELETE /v1/me`).

## Español

HtzCosmetics solo se conecta a dos direcciones:

- `heytozzz.github.io` (GitHub Pages): descarga la lista firmada de cosméticos y sus archivos. GitHub puede
  registrar tu IP como cualquier web.
- `htzcosmetics.htzcosmetics-backend.workers.dev` (Cloudflare Workers): guarda qué cosméticos llevas y le dice
  al mod qué llevan los jugadores cercanos.

**Qué guardamos:** tu UUID de Minecraft, los ids de los cosméticos equipados y cuándo los cambiaste por
última vez. Nada más: ni nombre, ni email, ni IP, ni chat, ni servidor.

**Qué se envía pero no se guarda:**

- Al iniciar sesión, el juego demuestra quién eres con el certificado de jugador que Mojang da a cada cuenta.
  Tu token de acceso de Minecraft y tu clave privada nunca salen de tu PC.
- Para mostrar los cosméticos de otros, el mod envía sus UUID (jugadores cerca de ti). Junto con tu IP, eso
  podría revelar quién juega cerca de quién; no lo registramos.
- Cloudflare, que aloja el servidor, ve las IP para frenar abusos (límites de peticiones) y puede guardar
  registros temporales según su propia política de privacidad.

**Al cambiar de cosméticos** se activa un bit sin uso de tus ajustes de skin para que los jugadores cercanos
con el mod se actualicen al momento. Los servidores pueden ver ese bit y saber que usas el mod.

**Borrar tus datos:** al quitarte todo en el guardarropa se borra tu entrada. También puedes pedirlo en el
[gestor de incidencias](https://github.com/Heytozzz/HtzCosmetics/issues) y borraremos tu entrada.
El servidor también permite borrarlos directamente (`DELETE /v1/me`).
