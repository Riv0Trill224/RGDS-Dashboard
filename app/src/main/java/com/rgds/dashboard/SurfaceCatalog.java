package com.rgds.dashboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catálogo de superficies de SurfaceFlinger.
 *
 * v0.5:
 * - Compatible con "dumpsys SurfaceFlinger --list".
 * - Compatible con ROMs donde --list devuelve vacío.
 * - Puede extraer superficies desde el dump completo.
 * - Da prioridad a SurfaceView/BLAST del paquete objetivo.
 * - Excluye las superficies propias de RGDS Dashboard.
 */
final class SurfaceCatalog {

    final List<String> layers =
            new ArrayList<>();

    int matches;
    int rejected;
    int own;

    String error;

    private String source =
            "lista";

    /*
     * Android 14 / New Frontend usa entradas como:
     *
     * Layer [1373] SurfaceView[com.mojang.minecraftpe/...](BLAST)#1373
     */
    private static final Pattern FULL_DUMP_LAYER =
            Pattern.compile(
                    "^\\s*Layer\\s+\\[\\d+\\]\\s+(.+?)\\s*$"
            );

    SurfaceCatalog(
            CommandResult result,
            String target,
            String dashboard
    ) {

        if (!result.succeeded()) {

            error =
                    "Error consultando SurfaceFlinger: exit="
                            + result.exitCode
                            + (
                            result.timedOut
                                    ? " (timeout)"
                                    : ""
                    );

            return;
        }

        if (result.truncated) {

            error =
                    "Información de superficies incompleta (truncada)";

            return;
        }

        String raw =
                result.stdout == null
                        ? ""
                        : result.stdout;

        if (
                raw.contains("Permission Denial")
                        || raw.contains("Permission denied")
        ) {

            error =
                    "SurfaceFlinger denegó la consulta";

            return;
        }

        Pattern game =
                packagePattern(target);

        Pattern self =
                packagePattern(dashboard);

        Set<String> preferred =
                new LinkedHashSet<>();

        Set<String> others =
                new LinkedHashSet<>();

        /*
         * Detectar si recibimos el dump completo
         * en lugar de --list.
         */
        boolean fullDump =
                looksLikeFullDump(raw);

        if (fullDump) {

            source =
                    "dump completo";

            parseFullDump(
                    raw,
                    game,
                    self,
                    preferred,
                    others
            );

        } else {

            source =
                    "lista";

            parseList(
                    raw,
                    game,
                    self,
                    preferred,
                    others
            );
        }

        /*
         * En el dump completo pueden aparecer varias
         * superficies pertenecientes al mismo paquete:
         *
         * Background for SurfaceView...
         * SurfaceView[...] (BLAST)...
         * com.paquete/.MainActivity...
         *
         * Queremos que la SurfaceView real del juego
         * aparezca primero.
         */
        List<String> orderedPreferred =
                new ArrayList<>(
                        preferred
                );

        orderedPreferred.sort(
                Comparator
                        .comparingInt(
                                SurfaceCatalog::priority
                        )
                        .reversed()
        );

        /*
         * También ordenamos las no verificadas,
         * aunque tienen menor importancia.
         */
        List<String> orderedOthers =
                new ArrayList<>(
                        others
                );

        orderedOthers.sort(
                Comparator
                        .comparingInt(
                                SurfaceCatalog::priority
                        )
                        .reversed()
        );

        matches =
                orderedPreferred.size();

        layers.addAll(
                orderedPreferred
        );

        layers.addAll(
                orderedOthers
        );
    }

    /**
     * Parser tradicional para:
     *
     * dumpsys SurfaceFlinger --list
     */
    private void parseList(
            String raw,
            Pattern game,
            Pattern self,
            Set<String> preferred,
            Set<String> others
    ) {

        for (
                String line :
                raw.split("\\n")
        ) {

            String layer =
                    line.trim();

            if (layer.isEmpty()) {
                continue;
            }

            addCandidate(
                    layer,
                    false,
                    game,
                    self,
                    preferred,
                    others
            );
        }
    }

    /**
     * Parser alternativo para Android / ROMs donde
     * --list no devuelve ninguna superficie.
     *
     * Extraemos únicamente las líneas "Layer [ID] ..."
     * del Composition list del dump completo.
     */
    private void parseFullDump(
            String raw,
            Pattern game,
            Pattern self,
            Set<String> preferred,
            Set<String> others
    ) {

        for (
                String line :
                raw.split("\\n")
        ) {

            Matcher matcher =
                    FULL_DUMP_LAYER.matcher(
                            line
                    );

            if (!matcher.matches()) {
                continue;
            }

            String layer =
                    matcher.group(1)
                            .trim();

            if (layer.isEmpty()) {
                continue;
            }

            addCandidate(
                    layer,
                    true,
                    game,
                    self,
                    preferred,
                    others
            );
        }
    }

    /**
     * Añade una superficie candidata.
     */
    private void addCandidate(
            String layer,
            boolean fullDump,
            Pattern game,
            Pattern self,
            Set<String> preferred,
            Set<String> others
    ) {

        /*
         * Nunca ofrecer superficies del propio Dashboard.
         */
        if (
                self.matcher(layer)
                        .find()
        ) {

            own++;
            return;
        }

        /*
         * SurfaceFps.command también valida que el
         * nombre pueda utilizarse con seguridad como
         * argumento de shell.
         */
        if (
                SurfaceFps.command(layer)
                        == null
        ) {

            rejected++;
            return;
        }

        /*
         * Coincidencia exacta con el package objetivo.
         */
        if (
                game.matcher(layer)
                        .find()
        ) {

            preferred.add(
                    layer
            );

            return;
        }

        /*
         * Con --list mantenemos el comportamiento anterior:
         * permitir también selección manual.
         */
        if (!fullDump) {

            others.add(
                    layer
            );

            return;
        }

        /*
         * El dump completo puede contener cientos
         * de capas internas de Android.
         *
         * Solo conservamos superficies razonablemente
         * seleccionables para evitar una lista enorme.
         */
        if (
                looksLikeSurface(layer)
                        && others.size() < 40
        ) {

            others.add(
                    layer
            );
        }
    }

    /**
     * Determina si la respuesta parece ser el
     * informe completo de SurfaceFlinger.
     */
    private static boolean looksLikeFullDump(
            String raw
    ) {

        return
                raw.contains(
                        "SurfaceFlinger New Frontend"
                )

                        || raw.contains(
                        "\nComposition list\n"
                )

                        || raw.contains(
                        "\nh/w composer state:"
                )

                        || raw.contains(
                        "Active Layers - layers with client handles"
                );
    }

    /**
     * Filtra capas internas poco útiles cuando
     * estamos analizando el dump completo.
     */
    private static boolean looksLikeSurface(
            String layer
    ) {

        String lower =
                layer.toLowerCase();

        return
                lower.contains(
                        "surfaceview"
                )

                        || lower.contains(
                        "blast"
                )

                        || lower.contains(
                        "surface("
                )

                        || lower.contains(
                        "native"
                );
    }

    /**
     * Prioridad para presentar las superficies.
     *
     * Una SurfaceView BLAST normal obtiene la
     * mayor puntuación.
     */
    private static int priority(
            String layer
    ) {

        int score =
                0;

        String lower =
                layer.toLowerCase();

        if (
                lower.contains(
                        "surfaceview["
                )
        ) {

            score +=
                    100;
        }

        if (
                lower.contains(
                        "(blast)"
                )
        ) {

            score +=
                    60;

        } else if (
                lower.contains(
                        "blast"
                )
        ) {

            score +=
                    30;
        }

        /*
         * Background for SurfaceView no es
         * la superficie de frames que buscamos.
         */
        if (
                lower.startsWith(
                        "background for "
                )
        ) {

            score -=
                    150;
        }

        if (
                lower.contains(
                        "activityrecordinputsink"
                )
        ) {

            score -=
                    150;
        }

        if (
                lower.contains(
                        "inputmethod"
                )
        ) {

            score -=
                    100;
        }

        return score;
    }

    /**
     * Coincidencia de package evitando:
     *
     * com.game.fake
     * prefixcom.game
     *
     * como falsos positivos.
     */
    private static Pattern packagePattern(
            String value
    ) {

        if (
                value == null
                        || value.isEmpty()
        ) {

            /*
             * Patrón que nunca coincide.
             */
            return Pattern.compile(
                    "(?!)"
            );
        }

        return Pattern.compile(
                "(?<![\\w.])"
                        + Pattern.quote(value)
                        + "(?![\\w.])"
        );
    }

    String summary() {

        if (
                error != null
        ) {

            return error;
        }

        return
                "Fuente: "
                        + source

                        + "\nSuperficies disponibles: "
                        + layers.size()

                        + "; del paquete: "
                        + matches

                        + "; Dashboard: "
                        + own

                        + "; nombres descartados: "
                        + rejected

                        + (
                        layers.isEmpty()
                                ? "\nNo se obtuvo una superficie seleccionable."
                                : ""
                );
    }
}
