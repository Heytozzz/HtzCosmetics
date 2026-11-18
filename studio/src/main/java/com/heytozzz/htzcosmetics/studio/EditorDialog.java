package com.heytozzz.htzcosmetics.studio;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.ManifestParser;
import com.heytozzz.htzcosmetics.core.manifest.Rarity;
import com.heytozzz.htzcosmetics.tools.JsonWriter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Add / edit form. Everything is validated while typing; the model and texture are checked in the background with
 * the exact rules of {@code htzBuildSite} ({@code SiteBuilder.checkCosmetic}) before "Guardar" is enabled.
 */
final class EditorDialog extends JDialog {
    private static final Map<String, String> LANG_LABEL = Map.of(
        "en_us", "Inglés (en_us) *", "es_es", "Español de España (es_es)", "es_mx", "Español de México (es_mx)");

    private final Project project;
    private final CosmeticStore.Entry existing; // null when adding
    private final Set<String> takenIds;
    private boolean saved;
    private String savedId;

    private Path newModel;
    private Path newTexture;
    private boolean removeTexture;
    private boolean idEditedByHand;

    private final JTextField idField = Widgets.textField(22);
    private final JLabel idNote = note();
    private final Map<String, JTextField> nameFields = new LinkedHashMap<>();
    private final Map<String, JLabel> nameNotes = new LinkedHashMap<>();
    private final Map<Category, Widgets.Chip> categoryChips = new EnumMap<>(Category.class);
    private final Map<Rarity, Widgets.Chip> rarityChips = new EnumMap<>(Rarity.class);
    private final JPanel anchorRow = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private DropZone modelZone;
    private DropZone textureZone;
    private final Widgets.FlatButton removeTextureButton = new Widgets.FlatButton("Quitar textura", Widgets.Style.GHOST);
    private final ModelView preview = new ModelView();
    private final JLabel modelStatus = Theme.label(" ", Theme.FONT_BOLD, Theme.TEXT_DIM);
    private final JLabel modelInfo = Theme.label(" ", Theme.FONT_SMALL, Theme.TEXT_DIM);
    private final JLabel problems = Theme.label(" ", Theme.FONT, Theme.ERROR);
    private final Widgets.FlatButton save = new Widgets.FlatButton("Guardar", Widgets.Style.PRIMARY);
    private final Timer checkTimer;

    private Category category = Category.HEAD;
    private Rarity rarity = Rarity.COMMON;
    private Anchor anchor; // null = automatic
    private List<String> modelErrors = List.of("Elige un modelo .bbmodel.");
    private boolean checking;
    private int checkGeneration;

    /** @return the id of the saved cosmetic, or null if cancelled */
    static String open(Window owner, Project project, CosmeticStore.Entry existing, Set<String> takenIds) {
        EditorDialog d = new EditorDialog(owner, project, existing, takenIds);
        d.setVisible(true);
        return d.saved ? d.savedId : null;
    }

    private EditorDialog(Window owner, Project project, CosmeticStore.Entry existing, Set<String> takenIds) {
        super(owner, existing == null ? "Añadir cosmético" : "Editar cosmético", ModalityType.APPLICATION_MODAL);
        this.project = project;
        this.existing = existing;
        this.takenIds = takenIds;
        modelZone = new DropZone("el modelo", ".bbmodel", p -> {
            newModel = p;
            modelZone.setCurrent(p.getFileName().toString());
            if (idField.getText().isBlank()) suggestId(p.getFileName().toString().replaceFirst("\\.bbmodel$", ""));
            changed(true);
        });
        textureZone = new DropZone("la textura", ".png", p -> {
            newTexture = p;
            removeTexture = false;
            textureZone.setCurrent(p.getFileName().toString());
            changed(true);
        });
        checkTimer = new Timer(350, e -> runModelCheck());
        checkTimer.setRepeats(false);

        JPanel root = new JPanel(new BorderLayout(14, 12));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        String heading = existing == null ? "Añadir cosmético" : "Editar «" + existing.displayName() + "»";
        root.add(Theme.label(heading, Theme.FONT_BIG, Theme.TEXT), BorderLayout.NORTH);
        root.add(Theme.scroll(form()), BorderLayout.CENTER);
        root.add(previewPanel(), BorderLayout.EAST);
        root.add(footer(), BorderLayout.SOUTH);
        setContentPane(root);

        fillFromExisting();
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(1040, 720);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(owner);
        changed(true);
    }

    // ------------------------------------------------------------------ layout

    private JPanel form() {
        JPanel f = new JPanel();
        f.setLayout(new BoxLayout(f, BoxLayout.Y_AXIS));
        f.setBackground(Theme.PANEL);
        f.setBorder(Theme.panelBorder());

        section(f, "1. Archivos");
        add(f, label("Modelo de Blockbench (.bbmodel) *"));
        add(f, modelZone);
        f.add(Box.createVerticalStrut(8));
        add(f, label("Textura (.png) — opcional si ya va dentro del .bbmodel"));
        add(f, textureZone);
        JPanel texRow = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 0, 2));
        removeTextureButton.addActionListener(e -> {
            newTexture = null;
            removeTexture = true;
            textureZone.setCurrent(null);
            changed(true);
        });
        texRow.add(removeTextureButton);
        add(f, texRow);

        section(f, "2. Identificador");
        add(f, label("Id (es el nombre de la carpeta) *"));
        add(f, idField);
        add(f, idNote);
        idField.getDocument().addDocumentListener(onChange(() -> {
            if (idField.isFocusOwner()) idEditedByHand = true;
            changed(false);
        }));

        section(f, "3. Nombres (máx. " + Limits.MAX_NAME_LENGTH + " caracteres)");
        for (String lang : CosmeticStore.LANGS) {
            JTextField field = Widgets.textField(30);
            JLabel n = note();
            nameFields.put(lang, field);
            nameNotes.put(lang, n);
            add(f, label(LANG_LABEL.get(lang)));
            add(f, field);
            add(f, n);
            field.getDocument().addDocumentListener(onChange(() -> {
                if (lang.equals("en_us") && existing == null && !idEditedByHand) suggestId(field.getText());
                changed(false);
            }));
        }

        section(f, "4. Categoría");
        JPanel cats = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (Category c : Category.values()) {
            Widgets.Chip chip = new Widgets.Chip(Theme.categoryName(c), Theme.TEXT);
            chip.addActionListener(e -> {
                category = c;
                if (anchor != null && !c.allows(anchor)) anchor = null;
                changed(true);
            });
            categoryChips.put(c, chip);
            cats.add(chip);
        }
        add(f, cats);

        section(f, "5. Rareza");
        JPanel rars = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (Rarity r : Rarity.values()) {
            Widgets.Chip chip = new Widgets.Chip(Theme.rarityName(r), Theme.rarityColor(r));
            chip.addActionListener(e -> {
                rarity = r;
                changed(false);
            });
            rarityChips.put(r, chip);
            rars.add(chip);
        }
        add(f, rars);

        section(f, "6. Anclaje (opcional)");
        add(f, Theme.label("<html>La parte del cuerpo a la que va pegado; se mueve con ella.</html>",
            Theme.FONT_SMALL, Theme.TEXT_FAINT));
        f.add(Box.createVerticalStrut(4));
        add(f, anchorRow);
        f.add(Box.createVerticalGlue());
        return f;
    }

    private JPanel previewPanel() {
        JPanel p = Widgets.panel(new BorderLayout(0, 8));
        p.setPreferredSize(new Dimension(380, 100));
        p.add(Theme.header("Vista previa"), BorderLayout.NORTH);
        preview.setPreferredSize(new Dimension(360, 360));
        p.add(preview, BorderLayout.CENTER);
        JPanel info = Widgets.clear(new BorderLayout(0, 4));
        info.add(modelStatus, BorderLayout.NORTH);
        info.add(modelInfo, BorderLayout.CENTER);
        p.add(info, BorderLayout.SOUTH);
        return p;
    }

    private JPanel footer() {
        JPanel bar = Widgets.clear(new BorderLayout(10, 0));
        bar.add(problems, BorderLayout.CENTER);
        JPanel buttons = Widgets.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        Widgets.FlatButton cancel = new Widgets.FlatButton("Cancelar", Widgets.Style.NORMAL);
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> save());
        buttons.add(cancel);
        buttons.add(save);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    private static void section(JPanel f, String title) {
        JLabel l = Theme.label(title, Theme.FONT_TITLE, Theme.ACCENT);
        l.setBorder(BorderFactory.createEmptyBorder(f.getComponentCount() == 0 ? 0 : 14, 0, 6, 0));
        add(f, l);
    }

    private static void add(JPanel f, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (c instanceof JTextField || c instanceof DropZone) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        } else {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 2));
        }
        f.add(c);
    }

    private static JLabel label(String text) {
        JLabel l = Theme.label(text, Theme.FONT, Theme.TEXT_DIM);
        l.setBorder(BorderFactory.createEmptyBorder(2, 0, 3, 0));
        return l;
    }

    private static JLabel note() {
        JLabel l = Theme.label(" ", Theme.FONT_SMALL, Theme.TEXT_FAINT);
        l.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return l;
    }

    private static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    private void fillFromExisting() {
        if (existing == null || existing.info() == null) {
            if (existing != null) idField.setText(existing.id());
            return;
        }
        CosmeticStore.Info info = existing.info();
        idField.setText(existing.id());
        idEditedByHand = true;
        for (String lang : CosmeticStore.LANGS) nameFields.get(lang).setText(info.names().getOrDefault(lang, ""));
        category = info.category();
        rarity = info.rarity();
        anchor = info.anchor() != null && info.anchor() != defaultAnchor(info.category()) ? info.anchor() : null;
        Path folder = existing.folder();
        if (Files.exists(folder.resolve("model.bbmodel"))) modelZone.setCurrent("model.bbmodel (actual)");
        else if (Files.exists(folder.resolve("model.json"))) modelZone.setCurrent("model.json (actual)");
        if (Files.exists(folder.resolve("texture.png"))) textureZone.setCurrent("texture.png (actual)");
    }

    private void suggestId(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.length() > 32) s = s.substring(0, 32).replaceAll("_+$", "");
        idField.setText(s);
    }

    private static Anchor defaultAnchor(Category c) {
        return c == Category.HEAD ? Anchor.HEAD : Anchor.BODY;
    }

    // ------------------------------------------------------------------ validation

    /** Called after any change. {@code modelRelevant}: also re-check the model (files, category, anchor). */
    private void changed(boolean modelRelevant) {
        categoryChips.forEach((c, chip) -> chip.setOn(c == category));
        rarityChips.forEach((r, chip) -> chip.setOn(r == rarity));
        rebuildAnchors();
        removeTextureButton.setVisible(newTexture != null || (existing != null && !removeTexture
            && Files.exists(existing.folder().resolve("texture.png"))));
        if (modelRelevant) {
            checking = true;
            modelStatus.setText("Comprobando el modelo…");
            modelStatus.setForeground(Theme.TEXT_DIM);
            checkTimer.restart();
        }
        refreshProblems();
    }

    private void rebuildAnchors() {
        anchorRow.removeAll();
        Widgets.Chip auto = new Widgets.Chip("Automático (" + Theme.anchorName(defaultAnchor(category)) + ")", Theme.TEXT);
        auto.setOn(anchor == null);
        auto.addActionListener(e -> {
            anchor = null;
            changed(true);
        });
        anchorRow.add(auto);
        for (Anchor a : Anchor.values()) {
            if (!category.allows(a) || a == defaultAnchor(category)) continue;
            Widgets.Chip chip = new Widgets.Chip(Theme.anchorName(a), Theme.TEXT);
            chip.setOn(anchor == a);
            chip.addActionListener(e -> {
                anchor = a;
                changed(true);
            });
            anchorRow.add(chip);
        }
        anchorRow.revalidate();
        anchorRow.repaint();
    }

    /** Form problems in Spanish (everything except the model check). */
    private List<String> formProblems() {
        List<String> out = new ArrayList<>();
        String id = idField.getText();
        String idMsg = null;
        if (id.isEmpty()) idMsg = "Escribe un id.";
        else if (id.length() > 32) idMsg = "Máximo 32 caracteres (tiene " + id.length() + ").";
        else if (!CosmeticStore.ID.matcher(id).matches()) idMsg = "Solo minúsculas (a-z), números y _ (sin espacios ni tildes).";
        else if (takenIds.contains(id) && (existing == null || !existing.id().equals(id))) idMsg = "Ya existe un cosmético con este id.";
        idNote.setText(idMsg != null ? idMsg : "✔ Se guardará en cosmetics/" + id + "/");
        idNote.setForeground(idMsg != null ? Theme.ERROR : Theme.OK);
        Widgets.markField(idField, idMsg == null);
        if (idMsg != null) out.add("Id: " + idMsg);

        for (String lang : CosmeticStore.LANGS) {
            String name = nameFields.get(lang).getText();
            String msg = nameProblem(lang, name);
            JLabel n = nameNotes.get(lang);
            n.setText(msg != null ? msg : name.isBlank() ? "Opcional (si falta, se usa el inglés)."
                : name.strip().length() + "/" + Limits.MAX_NAME_LENGTH);
            n.setForeground(msg != null ? Theme.ERROR : Theme.TEXT_FAINT);
            Widgets.markField(nameFields.get(lang), msg == null);
            if (msg != null) out.add("Nombre " + lang + ": " + msg);
        }
        if (anchor != null && !category.allows(anchor)) out.add("El anclaje no vale para esta categoría.");
        return out;
    }

    /** Checks one name with the mod's own manifest rules (so the studio can never disagree with the mod). */
    static String nameProblem(String lang, String name) {
        String n = name.strip();
        if (n.isEmpty()) return lang.equals("en_us") ? "El nombre en inglés es obligatorio." : null;
        if (n.length() > Limits.MAX_NAME_LENGTH) {
            return "Demasiado largo: " + n.length() + "/" + Limits.MAX_NAME_LENGTH + " caracteres.";
        }
        Map<String, Object> names = new LinkedHashMap<>();
        names.put("en_us", lang.equals("en_us") ? n : "x");
        if (!lang.equals("en_us")) names.put(lang, n);
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("sha256", "0".repeat(64));
        ref.put("size", 1);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", "x");
        entry.put("name", names);
        entry.put("category", "HEAD");
        entry.put("rarity", "COMMON");
        entry.put("anchor", "HEAD");
        entry.put("minFormat", 1);
        entry.put("model", ref);
        entry.put("texture", ref);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", 1);
        manifest.put("manifestVersion", 1);
        manifest.put("expiresAt", 32_000_000_000L);
        manifest.put("cosmetics", List.of(entry));
        try {
            if (ManifestParser.parseUnverified(JsonWriter.toBytes(manifest)).skippedEntries() == 0) return null;
        } catch (Exception ignored) {
            // falls through to the message below
        }
        return "No se permiten § ni caracteres invisibles o de control.";
    }

    private void refreshProblems() {
        List<String> all = new ArrayList<>(formProblems());
        if (!checking) all.addAll(modelErrors);
        boolean ok = all.isEmpty() && !checking;
        save.setEnabled(ok);
        problems.setText(checking && all.isEmpty() ? "Comprobando el modelo…"
            : all.isEmpty() ? "✔ Todo correcto. Puedes guardar." : "✖ " + all.get(0)
            + (all.size() > 1 ? "  (y " + (all.size() - 1) + " más)" : ""));
        problems.setForeground(all.isEmpty() ? Theme.OK : Theme.ERROR);
    }

    /** Copies the current files + a cosmetic.json into a scratch folder and runs the site builder's own check. */
    private void runModelCheck() {
        int gen = ++checkGeneration;
        Path model = newModel;
        Path texture = newTexture;
        boolean dropTexture = removeTexture;
        Path oldFolder = existing == null ? null : existing.folder();
        Category cat = category;
        Anchor anc = anchor;
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() {
                Path tmp = null;
                try {
                    tmp = Files.createTempDirectory("htzstudio-check");
                    boolean hasModel = stage(tmp, model, oldFolder, dropTexture, texture);
                    if (!hasModel) return new Object[]{List.of("Elige un modelo .bbmodel."), null};
                    Map<String, String> names = new LinkedHashMap<>();
                    names.put("en_us", "Preview");
                    CosmeticStore.writeInfo(tmp, new CosmeticStore.Info(names, cat, Rarity.COMMON, anc));
                    List<String> errs = CosmeticStore.check("preview", tmp);
                    CosmeticStore.Loaded loaded = null;
                    try {
                        loaded = CosmeticStore.loadFolderModel(tmp, anc != null ? anc : defaultAnchor(cat));
                    } catch (Exception e) {
                        if (errs.isEmpty()) errs = List.of(ErrorText.translate(e.getMessage()));
                    }
                    return new Object[]{errs, loaded};
                } catch (Exception e) {
                    return new Object[]{List.of(ErrorText.translate(e.getMessage())), null};
                } finally {
                    deleteQuietly(tmp);
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                if (gen != checkGeneration) return; // a newer check is on its way
                try {
                    Object[] r = get();
                    modelErrors = (List<String>) r[0];
                    CosmeticStore.Loaded loaded = (CosmeticStore.Loaded) r[1];
                    preview.show(loaded, modelErrors.isEmpty() ? "Sin vista previa" : "El modelo tiene errores");
                    if (modelErrors.isEmpty()) {
                        modelStatus.setText("✔ El modelo cumple todas las reglas del mod");
                        modelStatus.setForeground(Theme.OK);
                    } else {
                        modelStatus.setText("<html>✖ " + Dialogs.esc(modelErrors.get(0)) + "</html>");
                        modelStatus.setForeground(Theme.ERROR);
                    }
                    if (loaded != null) {
                        int cubes = loaded.model().cubeCount();
                        modelInfo.setText(cubes + " cubos · " + loaded.model().bones().size() + " grupos · textura "
                            + loaded.texture().getWidth() + "×" + loaded.texture().getHeight()
                            + (loaded.model().animations().isEmpty() ? "" : " · ▶ animado ("
                            + loaded.model().animations().size() + ")"));
                    } else {
                        modelInfo.setText(" ");
                    }
                } catch (Exception e) {
                    modelErrors = List.of(ErrorText.translate(e.getMessage()));
                }
                checking = false;
                refreshProblems();
            }
        }.execute();
    }

    /** Puts model + texture (new or existing) into {@code dir}. @return false if there is no model at all */
    private static boolean stage(Path dir, Path newModel, Path oldFolder, boolean dropTexture, Path newTexture)
        throws Exception {
        boolean hasModel = false;
        if (newModel != null) {
            Files.copy(newModel, dir.resolve("model.bbmodel"), StandardCopyOption.REPLACE_EXISTING);
            hasModel = true;
        } else if (oldFolder != null) {
            for (String f : List.of("model.bbmodel", "model.json")) {
                if (Files.exists(oldFolder.resolve(f))) {
                    Files.copy(oldFolder.resolve(f), dir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
                    hasModel = true;
                }
            }
        }
        if (newTexture != null) {
            Files.copy(newTexture, dir.resolve("texture.png"), StandardCopyOption.REPLACE_EXISTING);
        } else if (!dropTexture && oldFolder != null && Files.exists(oldFolder.resolve("texture.png"))) {
            Files.copy(oldFolder.resolve("texture.png"), dir.resolve("texture.png"), StandardCopyOption.REPLACE_EXISTING);
        }
        return hasModel;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (Exception ignored) {
            // temp folder; Windows cleans it eventually
        }
    }

    // ------------------------------------------------------------------ save

    private void save() {
        if (!formProblems().isEmpty() || checking || !modelErrors.isEmpty()) return;
        String id = idField.getText();
        Path target = project.cosmeticsDir.resolve(id);
        Map<String, String> names = new LinkedHashMap<>();
        for (String lang : CosmeticStore.LANGS) {
            String n = nameFields.get(lang).getText().strip();
            if (!n.isEmpty()) names.put(lang, n);
        }
        if (existing != null && existing.info() != null) {
            existing.info().names().forEach((k, v) -> {
                if (!CosmeticStore.LANGS.contains(k)) names.put(k, v); // keep extra languages added by hand
            });
        }
        try {
            Path staging = Files.createTempDirectory("htzstudio-save");
            try {
                stage(staging, newModel, existing == null ? null : existing.folder(), removeTexture, newTexture);
                CosmeticStore.writeInfo(staging, new CosmeticStore.Info(names, category, rarity, anchor));
                List<String> errs = CosmeticStore.check(id, staging);
                if (!errs.isEmpty()) {
                    Dialogs.message(this, "No se puede guardar", Dialogs.list(errs), Dialogs.Kind.DANGER);
                    return;
                }
                if (existing != null && !existing.id().equals(id)) {
                    if (Files.exists(target)) throw new IllegalStateException("cosmetics/" + id + " ya existe");
                    Files.move(existing.folder(), target);
                } else if (existing == null) {
                    if (Files.exists(target)) throw new IllegalStateException("cosmetics/" + id + " ya existe");
                }
                Files.createDirectories(target);
                // Replace the folder contents with the checked files (only the files the builder uses).
                for (String f : List.of("cosmetic.json", "model.bbmodel", "model.json", "texture.png")) {
                    Path src = staging.resolve(f);
                    if (Files.exists(src)) Files.copy(src, target.resolve(f), StandardCopyOption.REPLACE_EXISTING);
                    else Files.deleteIfExists(target.resolve(f));
                }
            } finally {
                deleteQuietly(staging);
            }
            saved = true;
            savedId = id;
            dispose();
        } catch (Exception e) {
            Dialogs.message(this, "No se pudo guardar", Dialogs.esc(String.valueOf(e.getMessage())), Dialogs.Kind.DANGER);
        }
    }

    // ------------------------------------------------------------------ hooks for the automated self-test

    void testFill(Path model, Path texture, String id, String en, String es, Category c, Rarity r) {
        if (model != null) {
            newModel = model;
            modelZone.setCurrent(model.getFileName().toString());
        }
        if (texture != null) {
            newTexture = texture;
            textureZone.setCurrent(texture.getFileName().toString());
        }
        if (en != null) nameFields.get("en_us").setText(en);
        if (es != null) nameFields.get("es_es").setText(es);
        if (id != null) {
            idEditedByHand = true;
            idField.setText(id);
        }
        if (c != null) category = c;
        if (r != null) rarity = r;
        changed(true);
    }

    boolean testReady() {
        return save.isEnabled();
    }

    void testSave() {
        save();
    }
}
