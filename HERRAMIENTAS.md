# Cómo añadir y publicar cosméticos

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

- `category`: `HEAD`, `BACK`, `SHOULDER` o `WAIST`.
- `rarity`: `COMMON`, `UNCOMMON`, `RARE`, `EPIC` o `LEGENDARY`.
- `anchor` (opcional): la parte del cuerpo a la que va pegado el cosmético; se mueve con ella. Si no lo pones, es `HEAD` para la categoría `HEAD` y `BODY` para las demás. `SHOULDER` también admite `LEFT_ARM` y `RIGHT_ARM`.
- `name`: el nombre en inglés (`en_us`) es obligatorio. Cada nombre puede tener hasta 48 caracteres.

### Reglas al modelar en Blockbench

- Usa un proyecto **Modded Entity** con **Box UV**.
- Modela el cosmético encima de un jugador normal, con los pies en Y=0 y la cabeza entre Y=24 y Y=32. Si añades un jugador de referencia, ocúltalo (icono del ojo); las piezas ocultas no se exportan.
- Solo se admiten **cubos**: nada de *meshes*. Puedes rotar grupos y cubos.
- Límites: 32 grupos, 256 cubos, texturas de hasta 256×256 y cubos a menos de 32 píxeles del pivote de su grupo.
- Las animaciones todavía no se exportan.

## 2. Publicar

```powershell
.\gradlew.bat htzBuildSite
```

Este comando:
- convierte los modelos y normaliza las texturas;
- comprueba todo con las mismas reglas que usa el mod;
- firma el manifiesto;
- deja el resultado en la carpeta `site/`.

Si algo falla, te dice qué cosmético y qué regla, y no publica nada. El contenido de `site/` es lo que se sube a GitHub Pages (en la Fase 4 lo dejaremos automatizado).

**La firma caduca a los 180 días.** Vuelve a ejecutar el comando antes de esa fecha aunque no hayas cambiado nada. Para elegir otra duración: `.\gradlew.bat htzBuildSite --args="--days 365"`.

Para volver a comprobar `site/` sin reconstruirlo:

```powershell
.\gradlew.bat htzVerifySite
```

## 3. Tus claves (importante)

Las claves ya están creadas en `C:\Users\caro3\.htzcosmetics\keys\`, fuera del proyecto para que nunca acaben en git:

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
