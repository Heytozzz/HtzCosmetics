# Cómo añadir y publicar cosméticos

## Usar HtzCosmetics Studio (lo más fácil)

**HtzCosmetics Studio** es una aplicación de escritorio que hace todo lo de esta guía con botones, sin escribir comandos.

**Abrirla:** haz doble clic en `HtzCosmetics Studio.bat`, en la carpeta del proyecto. La primera vez tarda un poco porque se prepara; después se abre en unos segundos. (También vale `.\gradlew.bat htzStudio`.)

Pantalla principal (el día a día):

- **+ Añadir cosmético**: arrastra el `.bbmodel` (y la textura `.png` si no va dentro), escribe el id y los nombres y elige categoría, rareza y anclaje. Todo se comprueba mientras escribes, con las mismas reglas que el mod, y verás el modelo en 3D. «Guardar» solo se activa cuando todo está bien.
- **Haz clic en una tarjeta** para ver sus detalles; doble clic o **Editar** para cambiarla; **Eliminar** la manda a la papelera de reciclaje (pide confirmación).
- **Comprobar**: revisa todos los cosméticos y te dice en español qué cosmético falla y por qué.
- **Probar en el juego**: construye `site/` y abre Minecraft 1.21.8 de prueba con tus cosméticos. Ciérralo o pulsa **Detener** en el registro.
- **Publicar…**: construye y firma `site/`, te enseña qué cambia (nuevos, cambiados, eliminados) y solo sube a Internet si confirmas.

En el editor también tienes:

- **Categorías**: Cabeza, Collar, Hombro, Espalda, Camisa, Abrigo, Cintura, Pantalón, Zapatos y Aura. Bajo los botones se explica a qué partes del cuerpo puede ir cada una.
- **Ropa que sigue a varias partes** (camisa, abrigo, pantalón, zapatos…): en Blockbench, pon a cada grupo principal el nombre de la parte del cuerpo (`left_arm`, `right_arm`, `left_leg`, `right_leg`, `body`, `head`; también vale en español, como `brazo_izquierdo` o `pierna_derecha`). Al cargar el modelo, el editor muestra **«Grupos principales y la parte que siguen»**: así compruebas que las mangas van con los brazos. La vista previa la enseña puesta sobre un jugador gris.
- **7. Brillo** (opcional, cualquier categoría): pulsa **Con brillo** y elige de 1 a 8 colores (clic en un color para cambiarlo, × para quitarlo, **+ Color** para añadir otro). Con varios colores, se mezclan en bucle según el **Ciclo de color**. **Brillo propio** hace que se vea a plena luz también de noche (con shaders, además brilla). **Intensidad** y **Tamaño del halo** controlan el resplandor de color alrededor. **Pulso → Respirar** lo hace latir. La vista previa enseña un brillo aproximado.
- **Aura**: un efecto de color alrededor de todo el jugador, **sin modelo ni textura**. Al elegir Aura se ocultan los archivos y el brillo se activa solo (sus colores son los del aura). En **8. Aura** eliges el **Relleno** (luz suave sobre el cuerpo) y el **Contorno** (borde de color).
- Si algo no vale (por ejemplo, un pulso de menos de 0,25 s escrito a mano en `cosmetic.json`), **Comprobar** te lo explica en español.

Abajo está el **registro**, donde ves lo que hace cada acción. Abajo a la izquierda se ve cuántos días faltan para que caduque la firma.

**Mantenimiento / Avanzado** (abajo a la izquierda, en rojo) está aparte a propósito y cada acción pide confirmación:

- **Firma**: fecha de caducidad de lo publicado, con aviso cuando quedan menos de 30 días, y **Renovar firma**.
- **Claves**: comprueba que existen `primary.key` y `backup.key` (la app nunca las abre ni las copia) y **Rotar claves**, que pide escribir ROTAR.
- **Backend**: tests, desplegar y actualizar las claves de Mojang.

El resto de esta guía explica lo que hace la app por dentro, por si algún día quieres usar los comandos a mano.

Todos los comandos se ejecutan en PowerShell, desde la carpeta del proyecto.

## 1. Crear un cosmético

Haz una carpeta dentro de `cosmetics/`. **El nombre de la carpeta es el id del cosmético**: solo puede llevar minúsculas, números y `_`, con un máximo de 32 caracteres. Por ejemplo, `cosmetics/top_hat/`. Si el nombre empieza por `_` (como `_borradores`), la carpeta se ignora.

Dentro de la carpeta van estos archivos:

| Archivo | Qué es |
|---|---|
| `cosmetic.json` | Nombre, categoría y rareza (ver el ejemplo de abajo) |
| `model.bbmodel` | El modelo de Blockbench |
| `texture.png` | La textura. Es opcional si ya va incluida dentro del `.bbmodel` |

Ejemplo de `cosmetic.json`:

```json
{
  "name": { "en_us": "Top hat", "es_es": "Chistera", "es_mx": "Sombrero de copa" },
  "category": "HEAD",
  "rarity": "RARE"
}
```

- `category`, una de estas:

  | Categoría | Qué es | Partes del cuerpo que puede seguir |
  |---|---|---|
  | `HEAD` | Cabeza (sombreros, coronas) | cabeza |
  | `NECKLACE` | Collar | cuerpo, cabeza |
  | `SHOULDER` | Hombro | cuerpo, brazos |
  | `BACK` | Espalda (alas, mochilas) | cuerpo |
  | `SHIRT` | Camisa | cuerpo, brazos |
  | `COAT` | Abrigo | cuerpo, brazos, piernas |
  | `WAIST` | Cintura (cinturones, faldas) | cuerpo, piernas |
  | `PANTS` | Pantalón | cuerpo, piernas |
  | `SHOES` | Zapatos | piernas |
  | `AURA` | Aura: brillo, contorno y bloom alrededor de todo el cuerpo (sin modelo) | todo el cuerpo |

- `rarity`: `COMMON`, `UNCOMMON`, `RARE`, `EPIC` o `LEGENDARY`.
- `anchor` (opcional): la parte del cuerpo principal del cosmético; se mueve con ella. Si no lo pones, es la cabeza para `HEAD`, la pierna derecha para `SHOES` y el cuerpo para las demás.
- `glow` (opcional, cualquier categoría): brillo con bloom de colores. Ejemplo:

  ```json
  "glow": { "colors": ["#FFD24A", "#FF5AD2"], "cycle": 3, "emissive": true, "bloom": 0.6, "radius": 1, "pulse": 0 }
  ```

  - `colors`: de 1 a 8 colores `#RRGGBB`; si pones varios, va pasando de uno a otro en bucle.
  - `cycle`: segundos que tarda en recorrer todos los colores (0,25 a 60; por defecto 4).
  - `emissive`: `true` hace que el cosmético se vea a plena luz también de noche (por defecto `true`). Con shaders, el shader le añade su propio bloom.
  - `bloom`: intensidad del halo de color alrededor (0 a 1; 0 = sin halo; por defecto 0,6).
  - `radius`: tamaño del halo en píxeles del modelo (0,1 a 8; por defecto 1).
  - `pulse`: segundos de cada "respiración" del brillo (0 = fijo, o de 0,25 a 60).
- `aura` (solo para la categoría `AURA`, que además **necesita** `glow` para sus colores). El aura es un brillo suave alrededor de la silueta del jugador, parecido al efecto *glowing* de Minecraft, pero que nunca se ve a través de las paredes:

  ```json
  "aura": { "outline": 1, "outlineStrength": 0.9, "fill": 0 }
  ```

  - `outline`: ancho del borde brillante pegado al cuerpo, en píxeles (0 a 3; 0 = sin borde; por defecto 1).
  - `outlineStrength`: opacidad de ese borde (0 a 1; por defecto 0,9).
  - `fill`: luz de color sobre el propio cuerpo (0 a 0,6; por defecto 0, apagada).
  - El halo ancho y difuso usa `bloom` (intensidad) y `radius` (amplitud, hasta 8 píxeles) del bloque `glow`.

  Un aura no necesita `model.bbmodel` ni `texture.png`: basta con `cosmetic.json`.
- `name`: el nombre en inglés (`en_us`) es obligatorio. Cada nombre puede tener hasta 48 caracteres.

### Reglas al modelar en Blockbench

- **Ropa (camisas, abrigos, pantalones, zapatos...):** cada grupo principal (de primer nivel) cuyo nombre sea una parte del cuerpo sigue a esa parte: `head`, `body`, `left_arm`, `right_arm`, `left_leg`, `right_leg` (también valen en español, como `cabeza`, `cuerpo`, `brazo_izquierdo`, `brazo_derecho`, `pierna_izquierda`, `pierna_derecha`). Así las mangas se mueven con los brazos y las perneras con las piernas. Modela la ropa sobre el jugador de referencia, un poco más grande que la piel (usa *Inflate* de 0,3 o más para que no se meta dentro de la segunda capa de la skin). Solo se usan las partes que permite la categoría (tabla de arriba); los demás grupos siguen la parte principal.

- Usa un proyecto **Modded Entity** con **Box UV**.
- Modela el cosmético encima de un jugador normal, con los pies en Y=0 y la cabeza entre Y=24 y Y=32. Si añades un jugador de referencia, ocúltalo (icono del ojo); las piezas ocultas no se exportan.
- Solo se admiten **cubos**: nada de *meshes*. Puedes rotar grupos y cubos.
- Límites: 32 grupos, 256 cubos, texturas de hasta 256×256 y cubos a menos de 32 píxeles del pivote de su grupo.
- **Animaciones:** crea en Blockbench animaciones con estos nombres (vale un prefijo como `animation.modelo.idle`):
  - `idle`: se reproduce siempre;
  - `walk`: se reproduce al andar;
  - `sneak` (o `crouch`): se reproduce agachado.
  Si falta `walk` o `sneak`, se usa `idle`. Se pueden animar rotación, posición y escala de los **grupos**, con interpolación lineal, escalonada o suave (catmullrom). Las animaciones siempre se repiten en bucle.
- **Nada de fórmulas (Molang):** en los fotogramas solo valen números. Si escribes algo como `math.sin(query.anim_time*90)`, el comando se para y te dice en qué animación está. Convierte esos movimientos en fotogramas clave en Blockbench.
- Límites de animación: 8 por cosmético, 64 fotogramas por canal, 60 segundos de duración como máximo.

## 2. Publicar

```powershell
.\gradlew.bat htzBuildSite
```

Este comando:
- convierte los modelos y normaliza las texturas;
- comprueba todo con las mismas reglas que usa el mod;
- firma el manifiesto;
- deja el resultado en la carpeta `site/`.

Si algo falla, te dice qué cosmético y qué regla, y no publica nada. El contenido de `site/` es lo que se sube a GitHub Pages con `htzPublishSite` (punto 4).

Para verlo en el juego antes de publicarlo, ejecuta `.\gradlew.bat :1.21.8-fabric:runClient`: ese cliente de prueba carga los cosméticos desde `site/` en vez de desde Internet.

Los jugadores **no necesitan actualizar el mod** para ver cosméticos nuevos: el mod revisa la lista al arrancar y cada 30 minutos (o al momento con `/htzcosmetics reload`).

**La firma caduca a los 180 días.** Vuelve a ejecutar el comando antes de esa fecha aunque no hayas cambiado nada. Para elegir otra duración: `.\gradlew.bat htzBuildSite --args="--days 365"`.

Para volver a comprobar `site/` sin reconstruirlo:

```powershell
.\gradlew.bat htzVerifySite
```

## 3. Tus claves (importante)

Las claves ya están creadas en `%USERPROFILE%\.htzcosmetics\keys\` (la carpeta `.htzcosmetics\keys` dentro de tu carpeta de usuario de Windows), fuera del proyecto para que nunca acaben en git:

- `primary.key` se usa para firmar.
- `backup.key` es la de reserva.

El mod lleva incrustadas solo las claves **públicas**, que no son secretas.

- **Copia ahora esa carpeta** a un USB o a un gestor de contraseñas. Si pierdes las dos claves, tendrás que publicar una versión nueva del mod con claves nuevas.
- **No compartas nunca** los archivos `.key`.
- Si crees que alguien ha conseguido `primary.key`, ejecuta:

  ```powershell
  .\gradlew.bat htzKeygen --args="--rotate"
  ```

  La clave de reserva pasa a ser la principal y se crea una nueva de reserva. Después publica una versión nueva del mod. Las versiones antiguas del mod seguirán confiando en la clave filtrada hasta que los jugadores actualicen.

## 4. Publicar en Internet

Después de `htzBuildSite`, sube los cosméticos firmados a GitHub Pages:

```powershell
.\gradlew.bat htzPublishSite
```

Este comando vuelve a comprobar las firmas antes de subir nada y lo publica en la rama `gh-pages`, aparte de tu código. En 1 o 2 minutos aparece en https://heytozzz.github.io/HtzCosmetics/manifest.json.

## 5. Backend (Cloudflare)

Ya está desplegado en https://htzcosmetics.htzcosmetics-backend.workers.dev. Solo guarda el UUID de cada jugador y la lista de cosméticos que lleva equipados.

Cómo se comprueba la identidad: cada cuenta de Minecraft tiene un certificado firmado por Mojang, el mismo que se usa para firmar el chat. El mod firma un reto con la clave privada de ese certificado, que nunca sale del PC, y el backend comprueba esa firma y la de Mojang. No hace falta llamar a Mojang, que bloquea las peticiones que llegan desde Cloudflare.

Comandos útiles (dentro de la carpeta `backend`):

```powershell
npm test
```

```powershell
npm run deploy
```

```powershell
npm run update-mojang-keys
```

Usa el último solo si los inicios de sesión empiezan a fallar con `invalid_certificate`. Después de ejecutarlo, vuelve a lanzar `npm test` y `npm run deploy`.

La clave secreta de los tokens (`TOKEN_SECRET`) solo está guardada en Cloudflare. Si alguna vez crees que se ha filtrado, ejecuta `npx wrangler secret put TOKEN_SECRET` y pega un valor aleatorio largo: todos los jugadores tendrán que volver a iniciar sesión, nada más.
