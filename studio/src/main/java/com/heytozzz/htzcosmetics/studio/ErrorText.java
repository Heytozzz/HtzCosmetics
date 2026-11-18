package com.heytozzz.htzcosmetics.studio;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the (English) messages of the tools and of the mod's validators into plain Spanish. Only the wording
 * changes: the rules themselves always come from {@code tools/} and {@code core/}. Unknown messages are shown
 * with the original text so nothing is ever hidden.
 */
final class ErrorText {
    private record Rule(Pattern pattern, Function<Matcher, String> text) {
    }

    private static final Pattern PREFIX = Pattern.compile(
        "^(?:\\[htz\\w+] ERROR:\\s*)?(?:(?:cosmetics/[^\\s:]+(?: texture)?|model(?: texture)?)(?:/cosmetic\\.json)?:\\s*)+");
    private static final Pattern COSMETIC = Pattern.compile("cosmetics/([a-z0-9_A-Z.\\-]+)");

    private static final List<Rule> RULES = List.of(
        rule("folder name must be 1-32 characters", m ->
            "El id (nombre de la carpeta) solo puede tener de 1 a 32 caracteres: minúsculas, números o _."),
        rule("missing cosmetic\\.json", m -> "Falta el archivo cosmetic.json."),
        rule("unknown category '(.*?)'", m -> "La categoría «" + m.group(1) + "» no existe. Usa HEAD, BACK, SHOULDER o WAIST."),
        rule("unknown rarity '(.*?)'", m -> "La rareza «" + m.group(1) + "» no existe. Usa COMMON, UNCOMMON, RARE, EPIC o LEGENDARY."),
        rule("unknown anchor '(.*?)'", m -> "El anclaje «" + m.group(1) + "» no existe."),
        rule("No enum constant .*\\.(\\w+)\\.(\\w+)", m -> "Valor desconocido en cosmetic.json: «" + m.group(2) + "»."),
        rule("anchor (\\w+) is not allowed for category (\\w+)", m ->
            "El anclaje " + m.group(1) + " no está permitido en la categoría " + m.group(2)
                + ". HEAD solo admite HEAD; BACK y WAIST, BODY; SHOULDER, BODY, LEFT_ARM o RIGHT_ARM."),
        rule("\"name\" needs at least an \"en_us\" entry|English name \\(en_us\\) is required", m ->
            "Falta el nombre en inglés (en_us), que es obligatorio."),
        rule("needs exactly one of model\\.bbmodel or model\\.json", m ->
            "Tiene que haber exactamente un modelo: model.bbmodel (o model.json), ni ninguno ni los dos."),
        rule("model rejected by the mod rules: ([^\\n]*)", m ->
            "El modelo no cumple las reglas del mod: " + inner(m.group(1))
                + ". Límites: 32 grupos, 256 cubos y cubos a menos de 32 píxeles del pivote de su grupo."),
        rule("no texture\\.png and no texture embedded", m ->
            "No hay texture.png y el .bbmodel no lleva la textura dentro."),
        rule("texture is (\\d+)x(\\d+) but the model expects (\\d+)x(\\d+)", m ->
            "La textura mide " + m.group(1) + "×" + m.group(2) + " pero el modelo espera " + m.group(3) + "×"
                + m.group(4) + " (o un múltiplo exacto). Revisa el tamaño de textura del proyecto en Blockbench."),
        rule("texture is (\\d+)x(\\d+), maximum is", m ->
            "La textura mide " + m.group(1) + "×" + m.group(2) + "; el máximo es 256×256."),
        rule("still (\\d+) KB after compression", m ->
            "La textura ocupa demasiado (" + m.group(1) + " KB) incluso comprimida; el máximo es 256 KB."),
        rule("texture rejected: (.*)", m -> "La textura no es válida: " + inner(m.group(1)) + "."),
        rule("cannot read image|not a readable PNG", m -> "La textura no es una imagen PNG que se pueda leer."),
        rule("rejected by the mod rules\\. Check the names", m ->
            "Algún nombre no es válido: máximo 48 caracteres, sin § ni caracteres invisibles."),
        rule("bad name text|bad language code|too many name languages", m ->
            "Algún nombre no es válido: máximo 48 caracteres, sin § ni caracteres invisibles."),
        rule("the model has no visible cubes", m -> "El modelo no tiene cubos visibles (¿están todos ocultos?)."),
        rule("element '(.*?)' is a (\\w+)", m ->
            "«" + m.group(1) + "» es un " + m.group(2) + ". Solo se admiten cubos (nada de meshes)."),
        rule("cube '(.*?)' uses per-face UV", m ->
            "El cubo «" + m.group(1) + "» usa UV por cara. Activa Box UV (Archivo > Proyecto > Box UV) en todos los cubos."),
        rule("cube '(.*?)' has a non-integer UV offset", m ->
            "El cubo «" + m.group(1) + "» tiene una UV con decimales; usa números enteros."),
        rule("embedded texture is not valid Base64", m -> "La textura que va dentro del .bbmodel está dañada."),
        rule("two animations are both '(.*?)'", m ->
            "Hay dos animaciones «" + m.group(1) + "». Solo puede haber una de cada tipo (idle, walk, sneak)."),
        rule("animation '(.*?)' is longer than", m -> "La animación «" + m.group(1) + "» dura más de 60 segundos."),
        rule("animation '(.*?)' has more than", m ->
            "La animación «" + m.group(1) + "» tiene demasiados fotogramas (máximo 64 por canal)."),
        rule("animation '(.*?)' \\((.*?)\\) uses the expression", m ->
            "La animación «" + m.group(1) + "» (" + m.group(2) + ") usa una fórmula (Molang). Solo valen números: "
                + "conviértela en fotogramas clave en Blockbench."),
        rule("Too many cosmetics", m -> "Hay demasiados cosméticos (máximo 2000)."),
        rule("signing key is not in TrustedKeys|TrustedKeys\\.java has no keys yet or changed", m ->
            "Las claves de firma no coinciden con las que lleva el mod. Revisa la sección Mantenimiento > Claves."),
        rule("manifest has expired", m -> "La firma del manifiesto ha caducado. Usa «Renovar firma»."),
        rule("manifest signature is invalid", m -> "La firma del manifiesto no es válida."),
        rule("site/manifest\\.json or site/manifest\\.json\\.sig is missing", m ->
            "Todavía no hay nada construido en site/. Usa «Probar en el juego» o «Publicar»."),
        rule("Nothing new to publish", m -> "No había nada nuevo que publicar."),
        rule("too many cubes", m -> "El modelo tiene demasiados cubos (máximo 256)."),
        rule("cube exceeds the allowed volume", m ->
            "Un cubo se sale del espacio permitido (a más de 32 píxeles del pivote de su grupo o demasiado grande)."),
        rule("cube UV is outside the texture", m -> "La UV de un cubo se sale de la textura."),
        rule("model has no cubes", m -> "El modelo no tiene cubos."),
        rule("Unexpected|unexpected|JSON|json", m -> "El archivo no es un JSON válido (" + m.group() + ")."));

    private ErrorText() {
    }

    private static Rule rule(String regex, Function<Matcher, String> text) {
        return new Rule(Pattern.compile(regex), text);
    }

    /** Spanish explanation of a tool/validator message. */
    static String translate(String message) {
        if (message == null || message.isBlank()) return "Error desconocido.";
        String msg = PREFIX.matcher(message.strip()).replaceFirst("");
        for (Rule r : RULES) {
            Matcher m = r.pattern().matcher(msg);
            if (m.find()) return r.text().apply(m);
        }
        return "Detalle técnico (en inglés): " + msg;
    }

    private static String inner(String msg) {
        String t = translate(msg);
        if (t.startsWith("Detalle técnico (en inglés): ")) return msg;
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }

    /** The cosmetic id mentioned in a message, or null. */
    static String cosmeticOf(String message) {
        if (message == null) return null;
        Matcher m = COSMETIC.matcher(message);
        return m.find() ? m.group(1) : null;
    }
}
