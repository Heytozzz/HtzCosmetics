package com.heytozzz.htzcosmetics.studio;

import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.Manifest;
import com.heytozzz.htzcosmetics.core.manifest.Rarity;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Main window: categories on the left, cards in the middle, details on the right (like the in-game wardrobe),
 * everyday actions in the top bar, the log at the bottom, and the advanced section behind its own sidebar button.
 */
final class MainWindow extends JFrame {
    static final String DEV_CLIENT = ":1.21.8-fabric:runClient";

    final Project project;
    final LogPanel log = new LogPanel();
    final TaskRunner runner;

    private List<CosmeticStore.Entry> entries = List.of();
    private final Map<String, BufferedImage> thumbs = new LinkedHashMap<>();
    private final Map<String, List<String>> problems = new LinkedHashMap<>();
    private boolean checkedOnce;
    private String selectedId;
    private Category categoryFilter;
    private Rarity rarityFilter;
    private String query = "";

    private final CardGrid grid = new CardGrid();
    private final JPanel detail = new JPanel(new BorderLayout(0, 10));
    private final ModelView detailView = new ModelView();
    private final Map<Category, JComponent> categoryRows = new EnumMap<>(Category.class);
    private JComponent allRow;
    private final Map<Rarity, Widgets.Chip> rarityChips = new EnumMap<>(Rarity.class);
    private Widgets.Chip allRarities;
    private final CardLayout centerCards = new CardLayout();
    private final JPanel center = new JPanel(centerCards);
    private final List<JButton> actionButtons = new ArrayList<>();
    private final JLabel signatureMini = Theme.label("Firma: comprobando…", Theme.FONT_SMALL, Theme.TEXT_DIM);
    private SidebarRow maintenanceRow;
    private SidebarRow cosmeticsRow;
    private MaintenancePanel maintenance;
    private boolean inMaintenance;

    Manifest published;
    Manifest local;
    boolean publishedChecked;

    MainWindow(Project project) {
        super("HtzCosmetics Studio");
        this.project = project;
        this.runner = new TaskRunner(log, this::updateBusy);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (runner.busy() && !Dialogs.confirm(MainWindow.this, "Hay una tarea en marcha",
                    "Hay una tarea en marcha (por ejemplo, el cliente de prueba de Minecraft).<br>"
                        + "Si sales, se detendrá.", Dialogs.Kind.WARNING, "Detener y salir")) {
                    return;
                }
                runner.stop();
                dispose();
                System.exit(0);
            }
        });
        try {
            List<Image> icons = new ArrayList<>();
            BufferedImage icon = ImageIO.read(project.asset("icon.png").toFile());
            icons.add(icon);
            icons.add(icon.getScaledInstance(32, 32, Image.SCALE_SMOOTH));
            setIconImages(icons);
        } catch (Exception ignored) {
            // the default Java icon is fine
        }

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        root.add(topBar(), BorderLayout.NORTH);
        root.add(sidebar(), BorderLayout.WEST);

        JPanel list = Widgets.clear(new BorderLayout(6, 0));
        JPanel gridPanel = new JPanel(new BorderLayout());
        gridPanel.setBackground(Theme.PANEL);
        gridPanel.setBorder(BorderFactory.createLineBorder(Theme.PANEL_EDGE));
        gridPanel.add(Theme.scroll(grid), BorderLayout.CENTER);
        list.add(gridPanel, BorderLayout.CENTER);
        detail.setBackground(Theme.PANEL);
        detail.setBorder(Theme.panelBorder());
        detail.setPreferredSize(new Dimension(320, 100));
        list.add(detail, BorderLayout.EAST);

        maintenance = new MaintenancePanel(this);
        center.setOpaque(false);
        center.add(list, "list");
        center.add(maintenance, "maintenance");
        root.add(center, BorderLayout.CENTER);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Theme.BG);
        outer.add(root, BorderLayout.CENTER);
        JPanel logWrap = Widgets.clear(new BorderLayout());
        logWrap.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        logWrap.add(log, BorderLayout.CENTER);
        outer.add(logWrap, BorderLayout.SOUTH);
        setContentPane(outer);

        setSize(1360, 880);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        reload(null);
        refreshSignature();
        if (project.dryRun) {
            log.info("MODO PRUEBA activo: «Publicar» solo verifica site/ y no sube nada; el despliegue y la rotación de "
                + "claves se simulan.");
        }
    }

    // ------------------------------------------------------------------ top bar

    private JComponent topBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.PANEL_EDGE),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JPanel left = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 10, 0));
        try {
            BufferedImage icon = ImageIO.read(project.asset("icon.png").toFile());
            left.add(new JLabel(new ImageIcon(icon.getScaledInstance(36, 36, Image.SCALE_SMOOTH))));
        } catch (Exception ignored) {
            // no icon
        }
        JPanel titles = Widgets.clear(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Theme.label("HtzCosmetics Studio", Theme.FONT_BIG, Theme.TEXT));
        titles.add(Theme.label("Gestiona, prueba y publica tus cosméticos", Theme.FONT_SMALL, Theme.TEXT_DIM));
        left.add(titles);
        if (project.dryRun) {
            JLabel dry = Theme.label("  MODO PRUEBA: no publica nada  ", Theme.FONT_SMALL.deriveFont(java.awt.Font.BOLD),
                new Color(0x1A1A1A));
            dry.setOpaque(true);
            dry.setBackground(Theme.WARN);
            left.add(dry);
        }
        bar.add(left, BorderLayout.WEST);

        JPanel right = Widgets.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(action("+ Añadir cosmético", Widgets.Style.PRIMARY, "Crea un cosmético nuevo a partir de un .bbmodel",
            this::addCosmetic));
        right.add(action("Comprobar", Widgets.Style.NORMAL,
            "Revisa todos los cosméticos con las mismas reglas que htzBuildSite", () -> checkAll(true, null)));
        right.add(action("Probar en el juego", Widgets.Style.NORMAL,
            "Construye site/ y abre Minecraft 1.21.8 de prueba con tus cosméticos locales", this::testLocally));
        right.add(action("Publicar…", Widgets.Style.SUCCESS,
            "Sube los cosméticos a Internet (GitHub Pages). Antes te enseña qué cambia.", () -> publish(false)));
        bar.add(right, BorderLayout.EAST);

        // Second row: search + rarity filter (same controls as the wardrobe).
        JPanel filters = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 4, 0));
        filters.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JTextField search = Widgets.textField(18);
        search.setToolTipText("Buscar por nombre o id");
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                query = search.getText().toLowerCase(Locale.ROOT).strip();
                rebuildGrid();
            }

            public void insertUpdate(DocumentEvent e) {
                update();
            }

            public void removeUpdate(DocumentEvent e) {
                update();
            }

            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });
        filters.add(Theme.label("Buscar:", Theme.FONT, Theme.TEXT_DIM));
        filters.add(search);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(Theme.label("Rareza:", Theme.FONT, Theme.TEXT_DIM));
        allRarities = new Widgets.Chip("Todas", Theme.TEXT);
        allRarities.addActionListener(e -> setRarity(null));
        filters.add(allRarities);
        for (Rarity r : Rarity.values()) {
            Widgets.Chip chip = new Widgets.Chip(Theme.rarityName(r), Theme.rarityColor(r));
            chip.addActionListener(e -> setRarity(r));
            rarityChips.put(r, chip);
            filters.add(chip);
        }
        bar.add(filters, BorderLayout.SOUTH);
        return bar;
    }

    private JButton action(String text, Widgets.Style style, String tip, Runnable r) {
        Widgets.FlatButton b = new Widgets.FlatButton(text, style);
        b.setToolTipText(tip);
        b.addActionListener(e -> r.run());
        actionButtons.add(b);
        return b;
    }

    JButton maintenanceAction(String text, Widgets.Style style, String tip, Runnable r) {
        return action(text, style, tip, r);
    }

    private void setRarity(Rarity r) {
        rarityFilter = r;
        rebuildGrid();
    }

    // ------------------------------------------------------------------ sidebar

    /** A row of the left panel, drawn like the wardrobe's category list (accent bar when selected). */
    private static final class SidebarRow extends JComponent {
        private final String text;
        private String count = "";
        private boolean on;
        private boolean hover;
        private final Color color;

        SidebarRow(String text, Color color, Runnable onClick) {
            this.text = text;
            this.color = color;
            setPreferredSize(new Dimension(190, 30));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void set(boolean on, String count) {
            this.on = on;
            this.count = count;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            java.awt.Graphics2D g2 = Theme.smooth(g);
            if (on) {
                g2.setColor(Theme.CHIP_ON);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(color == Theme.ERROR ? Theme.ERROR : Theme.ACCENT);
                g2.fillRect(0, 0, 3, getHeight());
            } else if (hover) {
                g2.setColor(Theme.CARD_HOVER);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setFont(on ? Theme.FONT_BOLD : Theme.FONT);
            g2.setColor(on ? (color == Theme.ERROR ? Theme.ERROR : Theme.TEXT) : color);
            g2.drawString(text, 12, 20);
            g2.setFont(Theme.FONT_SMALL);
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString(count, getWidth() - 10 - g2.getFontMetrics().stringWidth(count), 20);
            g2.dispose();
        }
    }

    private JComponent sidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.PANEL);
        side.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.PANEL_EDGE),
            BorderFactory.createEmptyBorder(6, 0, 8, 0)));
        side.setPreferredSize(new Dimension(200, 100));

        JPanel top = Widgets.clear(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel head = Theme.header("  Cosméticos");
        try {
            BufferedImage sprite = ImageIO.read(project.asset("textures/gui/sprites/wardrobe.png").toFile());
            head.setIcon(new ImageIcon(sprite.getScaledInstance(16, 16, Image.SCALE_FAST)));
        } catch (Exception ignored) {
            // no sprite
        }
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 4));
        top.add(head);
        cosmeticsRow = null;
        allRow = new SidebarRow("Todos", Theme.TEXT_DIM, () -> setCategory(null));
        top.add(allRow);
        for (Category c : Category.values()) {
            SidebarRow row = new SidebarRow(Theme.categoryName(c), Theme.TEXT_DIM, () -> setCategory(c));
            categoryRows.put(c, row);
            top.add(row);
        }
        side.add(top, BorderLayout.NORTH);

        // Bottom: signature status + the advanced section, clearly separated from the everyday actions.
        JPanel bottom = Widgets.clear(null);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        signatureMini.setAlignmentX(Component.LEFT_ALIGNMENT);
        signatureMini.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 8));
        signatureMini.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signatureMini.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMaintenance(true);
            }
        });
        bottom.add(signatureMini);
        JPanel danger = new JPanel();
        danger.setLayout(new BoxLayout(danger, BoxLayout.Y_AXIS));
        danger.setBackground(Theme.DANGER_BG);
        danger.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.DANGER_EDGE),
            BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        danger.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel dh = Theme.header("  Zona avanzada");
        dh.setForeground(Theme.ERROR);
        dh.setAlignmentX(Component.LEFT_ALIGNMENT);
        danger.add(dh);
        maintenanceRow = new SidebarRow("Mantenimiento / Avanzado", Theme.ERROR, () -> showMaintenance(true));
        danger.add(maintenanceRow);
        bottom.add(danger);
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private void setCategory(Category c) {
        categoryFilter = c;
        showMaintenance(false);
        rebuildGrid();
    }

    void showMaintenance(boolean on) {
        inMaintenance = on;
        centerCards.show(center, on ? "maintenance" : "list");
        if (on) maintenance.refresh();
        updateSidebar();
    }

    private void updateSidebar() {
        ((SidebarRow) allRow).set(!inMaintenance && categoryFilter == null, Integer.toString(entries.size()));
        for (Category c : Category.values()) {
            long n = entries.stream().filter(e -> e.info() != null && e.info().category() == c).count();
            ((SidebarRow) categoryRows.get(c)).set(!inMaintenance && categoryFilter == c, Long.toString(n));
        }
        maintenanceRow.set(inMaintenance, "");
        allRarities.setOn(rarityFilter == null);
        rarityChips.forEach((r, chip) -> chip.setOn(r == rarityFilter));
    }

    // ------------------------------------------------------------------ grid + detail

    /** Re-reads cosmetics/ (thumbnails are rendered in the background). */
    void reload(String select) {
        if (select != null) selectedId = select;
        new SwingWorker<Map<String, BufferedImage>, Void>() {
            List<CosmeticStore.Entry> loaded;

            @Override
            protected Map<String, BufferedImage> doInBackground() {
                loaded = CosmeticStore.loadAll(project.cosmeticsDir);
                Map<String, BufferedImage> t = new LinkedHashMap<>();
                for (CosmeticStore.Entry e : loaded) {
                    if (e.loaded() != null) {
                        try {
                            t.put(e.id(), ModelView.thumbnail(e.loaded(), 300));
                        } catch (RuntimeException ignored) {
                            // drawn without a thumbnail
                        }
                    }
                }
                return t;
            }

            @Override
            protected void done() {
                try {
                    Map<String, BufferedImage> t = get();
                    entries = loaded;
                    thumbs.clear();
                    thumbs.putAll(t);
                } catch (Exception e) {
                    log.error("No se pudieron leer los cosméticos: " + e.getMessage());
                }
                Set<String> ids = new HashSet<>();
                for (CosmeticStore.Entry e : entries) ids.add(e.id());
                problems.keySet().retainAll(ids);
                if (selectedId != null && !ids.contains(selectedId)) selectedId = null;
                if (selectedId == null && !entries.isEmpty()) selectedId = entries.get(0).id();
                rebuildGrid();
            }
        }.execute();
    }

    private void rebuildGrid() {
        grid.removeAll();
        List<CardGrid.Card> cards = new ArrayList<>();
        for (CosmeticStore.Entry e : entries) {
            if (categoryFilter != null && (e.info() == null || e.info().category() != categoryFilter)) continue;
            if (rarityFilter != null && (e.info() == null || e.info().rarity() != rarityFilter)) continue;
            if (!query.isEmpty() && !e.id().contains(query) && !e.displayName().toLowerCase(Locale.ROOT).contains(query)
                && (e.info() == null || e.info().names().values().stream()
                .noneMatch(n -> n.toLowerCase(Locale.ROOT).contains(query)))) continue;
            Consumer<CardGrid.Card> select = c -> select(c.entry.id());
            Consumer<CardGrid.Card> edit = c -> editCosmetic(c.entry);
            CardGrid.Card card = new CardGrid.Card(e, thumbs.get(e.id()), select, edit);
            card.setSelected(e.id().equals(selectedId));
            card.setProblem(problems.containsKey(e.id()) && !problems.get(e.id()).isEmpty());
            cards.add(card);
            grid.add(card);
        }
        grid.setEmptyText(entries.isEmpty()
            ? "Todavía no hay cosméticos.\nPulsa «+ Añadir cosmético» para crear el primero."
            : cards.isEmpty() ? "Ningún cosmético coincide con el filtro." : "");
        grid.revalidate();
        grid.repaint();
        updateSidebar();
        showDetail();
    }

    void select(String id) {
        selectedId = id;
        for (Component c : grid.getComponents()) {
            if (c instanceof CardGrid.Card card) card.setSelected(card.entry.id().equals(id));
        }
        showDetail();
    }

    CosmeticStore.Entry entry(String id) {
        for (CosmeticStore.Entry e : entries) if (e.id().equals(id)) return e;
        return null;
    }

    private CosmeticStore.Entry selected() {
        for (CosmeticStore.Entry e : entries) if (e.id().equals(selectedId)) return e;
        return null;
    }

    private void showDetail() {
        detail.removeAll();
        CosmeticStore.Entry e = selected();
        if (e == null) {
            detail.add(Theme.label("<html><center>Selecciona un cosmético<br>para ver sus detalles.</center></html>",
                Theme.FONT, Theme.TEXT_DIM), BorderLayout.CENTER);
            detail.revalidate();
            detail.repaint();
            return;
        }
        detailView.setPreferredSize(new Dimension(296, 280));
        detailView.show(e.loaded(), e.problem() != null ? "No se puede mostrar:\nmira los errores abajo" : "Sin vista previa");
        detail.add(detailView, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setBackground(Theme.PANEL);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(left(Theme.label(e.displayName(), Theme.FONT_BIG, Theme.TEXT)));
        info.add(left(Theme.label("id: " + e.id(), Theme.FONT_SMALL, Theme.TEXT_FAINT)));
        info.add(Box.createVerticalStrut(8));
        if (e.info() != null) {
            JPanel chips = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 4, 0));
            chips.add(tag(Theme.categoryName(e.info().category()), Theme.TEXT));
            chips.add(tag(Theme.rarityName(e.info().rarity()), Theme.rarityColor(e.info().rarity())));
            chips.add(tag("Anclaje: " + Theme.anchorName(e.info().effectiveAnchor()), Theme.TEXT_DIM));
            if (e.animated()) chips.add(tag("▶ Animado", Theme.ACCENT));
            info.add(left(chips));
            info.add(Box.createVerticalStrut(8));
            info.add(left(Theme.header("Nombres")));
            for (Map.Entry<String, String> n : e.info().names().entrySet()) {
                info.add(left(Theme.label(n.getKey() + ":  " + n.getValue(), Theme.FONT, Theme.TEXT_DIM)));
            }
        }
        info.add(Box.createVerticalStrut(8));
        info.add(left(Theme.header("Estado")));
        List<String> errs = new ArrayList<>();
        if (e.problem() != null) errs.add(e.problem());
        if (problems.containsKey(e.id())) {
            for (String p : problems.get(e.id())) if (!errs.contains(p)) errs.add(p);
        }
        if (!errs.isEmpty()) {
            for (String p : errs) {
                info.add(left(Theme.label("<html><div style='width:250px'>✖ " + Dialogs.esc(p) + "</div></html>",
                    Theme.FONT, Theme.ERROR)));
            }
        } else if (checkedOnce && problems.containsKey(e.id())) {
            info.add(left(Theme.label("✔ Cumple todas las reglas", Theme.FONT, Theme.OK)));
        } else {
            info.add(left(Theme.label("Sin comprobar (pulsa «Comprobar»)", Theme.FONT, Theme.TEXT_DIM)));
        }
        detail.add(Theme.scroll(info), BorderLayout.CENTER);

        JPanel buttons = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
        Widgets.FlatButton edit = new Widgets.FlatButton("Editar", Widgets.Style.NORMAL);
        edit.addActionListener(ev -> editCosmetic(e));
        Widgets.FlatButton open = new Widgets.FlatButton("Abrir carpeta", Widgets.Style.GHOST);
        open.addActionListener(ev -> {
            try {
                Desktop.getDesktop().open(e.folder().toFile());
            } catch (Exception ex) {
                log.error("No se pudo abrir la carpeta: " + ex.getMessage());
            }
        });
        Widgets.FlatButton delete = new Widgets.FlatButton("Eliminar", Widgets.Style.DANGER);
        delete.addActionListener(ev -> deleteCosmetic(e));
        edit.setEnabled(!runner.busy());
        delete.setEnabled(!runner.busy());
        buttons.add(edit);
        buttons.add(open);
        buttons.add(delete);
        detail.add(buttons, BorderLayout.SOUTH);
        detail.revalidate();
        detail.repaint();
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JLabel tag(String text, Color color) {
        JLabel l = Theme.label(" " + text + " ", Theme.FONT_SMALL, color);
        l.setOpaque(true);
        l.setBackground(Theme.CHIP);
        l.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        return l;
    }

    private void updateBusy() {
        boolean busy = runner.busy();
        for (JButton b : actionButtons) b.setEnabled(!busy);
        showDetail();
    }

    private Set<String> ids() {
        Set<String> s = new HashSet<>();
        for (CosmeticStore.Entry e : entries) s.add(e.id());
        return s;
    }

    // ------------------------------------------------------------------ add / edit / delete

    void addCosmetic() {
        if (refuseWhileBusy()) return;
        String id = EditorDialog.open(this, project, null, ids());
        if (id != null) {
            log.ok("Cosmético «" + id + "» guardado en cosmetics/" + id + "/");
            problems.remove(id);
            reload(id);
        }
    }

    private void editCosmetic(CosmeticStore.Entry e) {
        if (refuseWhileBusy()) return;
        String id = EditorDialog.open(this, project, e, ids());
        if (id != null) {
            log.ok("Cosmético «" + id + "» actualizado.");
            problems.remove(e.id());
            problems.remove(id);
            reload(id);
        }
    }

    void deleteCosmetic(CosmeticStore.Entry e) {
        if (refuseWhileBusy()) return;
        boolean online = published != null && published.byId().containsKey(e.id());
        if (!Dialogs.confirm(this, "Eliminar cosmético",
            "¿Seguro que quieres eliminar <b>" + Dialogs.esc(e.displayName()) + "</b> (" + e.id() + ")?<br><br>"
                + "La carpeta <b>cosmetics/" + e.id() + "/</b> se moverá a la <b>papelera de reciclaje</b> de Windows "
                + "(podrás recuperarla desde allí).<br><br>"
                + (online ? "Este cosmético está publicado: los jugadores lo seguirán viendo hasta que vuelvas a "
                + "<b>Publicar</b>." : "Este cosmético no está publicado todavía."),
            Dialogs.Kind.DANGER, "Eliminar")) {
            return;
        }
        try {
            boolean bin = CosmeticStore.delete(e.folder());
            log.ok("Cosmético «" + e.id() + "» eliminado" + (bin ? " (está en la papelera de reciclaje)." : "."));
        } catch (Exception ex) {
            Dialogs.message(this, "No se pudo eliminar", Dialogs.esc(String.valueOf(ex.getMessage())), Dialogs.Kind.DANGER);
        }
        selectedId = null;
        reload(null);
    }

    boolean refuseWhileBusy() {
        if (!runner.busy()) return false;
        Dialogs.message(this, "Espera un momento", "Hay una tarea en marcha. Espera a que termine o pulsa "
            + "<b>Detener</b> en el registro de abajo.", Dialogs.Kind.WARNING);
        return true;
    }

    // ------------------------------------------------------------------ check / test / publish

    /**
     * Checks every cosmetic with htzBuildSite's own rules (no signing, nothing written).
     *
     * @param showResult show a dialog even when everything is fine
     * @param then       runs on the Swing thread with true when everything is OK (may be null)
     */
    void checkAll(boolean showResult, Consumer<Boolean> then) {
        if (refuseWhileBusy()) return;
        log.info("Comprobando todos los cosméticos con las reglas de htzBuildSite…");
        new SwingWorker<Map<String, List<String>>, Void>() {
            @Override
            protected Map<String, List<String>> doInBackground() {
                Map<String, List<String>> out = new LinkedHashMap<>();
                for (CosmeticStore.Entry e : CosmeticStore.loadAll(project.cosmeticsDir)) {
                    out.put(e.id(), CosmeticStore.check(e.id(), e.folder()));
                }
                return out;
            }

            @Override
            protected void done() {
                Map<String, List<String>> result;
                try {
                    result = get();
                } catch (Exception e) {
                    log.error("La comprobación falló: " + e.getMessage());
                    if (then != null) then.accept(false);
                    return;
                }
                problems.clear();
                problems.putAll(result);
                checkedOnce = true;
                int bad = 0;
                StringBuilder html = new StringBuilder();
                for (Map.Entry<String, List<String>> r : result.entrySet()) {
                    if (r.getValue().isEmpty()) continue;
                    bad++;
                    html.append("<p><b>").append(Dialogs.esc(r.getKey())).append("</b> (cosmetics/")
                        .append(r.getKey()).append("/)</p>").append(Dialogs.list(r.getValue()));
                    for (String p : r.getValue()) log.error(r.getKey() + ": " + p);
                }
                boolean ok = bad == 0 && !result.isEmpty();
                if (result.isEmpty()) {
                    log.error("No hay ningún cosmético en cosmetics/.");
                    if (showResult || then != null) {
                        Dialogs.message(MainWindow.this, "No hay cosméticos", "La carpeta cosmetics/ está vacía. "
                            + "Añade al menos uno.", Dialogs.Kind.WARNING);
                    }
                } else if (ok) {
                    log.ok("Los " + result.size() + " cosméticos cumplen todas las reglas.");
                    if (showResult) {
                        Dialogs.message(MainWindow.this, "Todo correcto", "Los <b>" + result.size()
                            + "</b> cosméticos cumplen todas las reglas del mod.", Dialogs.Kind.OK);
                    }
                } else {
                    log.error(bad + " cosmético(s) con errores.");
                    Dialogs.message(MainWindow.this, bad == 1 ? "Hay 1 cosmético con errores"
                            : "Hay " + bad + " cosméticos con errores",
                        "Corrígelos antes de probar o publicar (no se ha publicado nada):" + html,
                        Dialogs.Kind.DANGER);
                }
                rebuildGrid();
                if (then != null) then.accept(ok);
            }
        }.execute();
    }

    void testLocally() {
        checkAll(false, ok -> {
            if (!ok) return;
            runner.run("Probar en el juego", List.of(
                TaskRunner.gradle(project, "Construyendo y firmando site/ (htzBuildSite)", "htzBuildSite"),
                TaskRunner.gradle(project, "Abriendo Minecraft 1.21.8 de prueba (carga los cosméticos de site/)",
                    DEV_CLIENT)), r -> {
                if (!r.ok() && !r.cancelled()) explainFailure("No se pudo probar en el juego", r);
                refreshSignature();
            });
        });
    }

    /** Publish (or renew the signature): check → htzBuildSite → show the changes → htzPublishSite. */
    void publish(boolean renew) {
        checkAll(false, ok -> {
            if (!ok) return;
            runner.run(renew ? "Renovar firma: preparar" : "Preparar publicación", List.of(
                TaskRunner.gradle(project, "Construyendo, comprobando y firmando site/ (htzBuildSite)", "htzBuildSite")),
                r -> {
                    if (!r.ok()) {
                        if (!r.cancelled()) explainFailure("No se pudo preparar la publicación", r);
                        return;
                    }
                    log.info("Comparando con lo que está publicado ahora…");
                    new SwingWorker<Manifest[], Void>() {
                        @Override
                        protected Manifest[] doInBackground() {
                            return new Manifest[]{ManifestStatus.fetchPublished(), ManifestStatus.readLocal(project.siteDir)};
                        }

                        @Override
                        protected void done() {
                            try {
                                Manifest[] m = get();
                                confirmAndPublish(renew, m[0], m[1]);
                            } catch (Exception e) {
                                log.error("No se pudo comparar: " + e.getMessage());
                            }
                        }
                    }.execute();
                });
        });
    }

    private void confirmAndPublish(boolean renew, Manifest online, Manifest built) {
        if (built == null) {
            Dialogs.message(this, "No hay nada que publicar", "site/manifest.json no existe.", Dialogs.Kind.DANGER);
            return;
        }
        ManifestStatus.Diff d = ManifestStatus.diff(online, built);
        StringBuilder html = new StringBuilder();
        html.append(renew ? "<p>Se volverá a firmar el manifiesto y se subirá a Internet.</p>"
            : "<p>Esto es lo que cambiará para <b>todos los jugadores</b>:</p>");
        if (online == null) {
            html.append("<p style='color:#F2A84C'>No he podido descargar lo publicado ahora mismo (¿sin Internet?), "
                + "así que no puedo comparar. Se publicarán estos " + built.cosmetics().size() + " cosméticos.</p>");
        } else {
            if (!d.added().isEmpty()) html.append("<p style='color:#7FE07F'><b>Nuevos (").append(d.added().size())
                .append(")</b></p>").append(Dialogs.list(d.added()));
            if (!d.changed().isEmpty()) html.append("<p style='color:#F2C94C'><b>Cambiados (").append(d.changed().size())
                .append(")</b></p>").append(Dialogs.list(d.changed()));
            if (!d.removed().isEmpty()) html.append("<p style='color:#F08A8A'><b>Eliminados (").append(d.removed().size())
                .append(")</b> — los jugadores dejarán de verlos</p>").append(Dialogs.list(d.removed()));
            if (!d.contentChanged()) {
                html.append("<p>Los cosméticos son <b>los mismos</b> que ya están publicados (")
                    .append(d.unchanged()).append("). Solo se renueva la firma.</p>");
            } else if (d.unchanged() > 0) {
                html.append("<p style='color:#9AA0AD'>Sin cambios: ").append(d.unchanged()).append(" cosmético(s).</p>");
            }
            if (renew && d.contentChanged()) {
                html.append("<p style='color:#F2A84C'><b>Atención:</b> tienes cambios locales sin publicar; "
                    + "renovar la firma también los publicará.</p>");
            }
        }
        html.append("<p>Firma válida hasta el <b>").append(ManifestStatus.date(built.expiresAt())).append("</b> (")
            .append(ManifestStatus.daysLeft(built.expiresAt())).append(" días). Versión del manifiesto: ")
            .append(built.manifestVersion()).append(".</p>");
        html.append("<p style='color:#9AA0AD'>Se sube a GitHub Pages (rama gh-pages). Los jugadores lo reciben en "
            + "unos minutos (el mod mira cada 30 minutos o con /htzcosmetics reload).</p>");
        if (project.dryRun) {
            html.append("<p style='color:#F2A84C'><b>MODO PRUEBA:</b> no se subirá nada; solo se verificará site/.</p>");
        }
        if (!Dialogs.confirm(this, renew ? "¿Renovar la firma y publicar?" : "¿Publicar en Internet?", html.toString(),
            Dialogs.Kind.WARNING, project.dryRun ? "Simular publicación" : "Publicar ahora")) {
            log.info("Publicación cancelada. No se ha subido nada (site/ queda construido en tu PC).");
            return;
        }
        TaskRunner.Step step = project.dryRun
            ? TaskRunner.gradle(project, "MODO PRUEBA: verificando site/ en lugar de publicar (htzVerifySite)", "htzVerifySite")
            : TaskRunner.gradle(project, "Publicando en GitHub Pages (htzPublishSite)", "htzPublishSite");
        runner.run(renew ? "Renovar firma" : "Publicar", List.of(step), r -> {
            if (r.ok()) {
                Dialogs.message(this, project.dryRun ? "Simulación terminada" : "¡Publicado!",
                    project.dryRun ? "En modo prueba no se ha subido nada. site/ es correcto."
                        : "Publicado correctamente. GitHub Pages suele tardar 1 o 2 minutos en actualizarse.<br>"
                        + "Firma válida hasta el <b>" + ManifestStatus.date(built.expiresAt()) + "</b>.",
                    Dialogs.Kind.OK);
            } else if (!r.cancelled()) {
                explainFailure("No se pudo publicar", r);
            }
            refreshSignature();
        });
    }

    /** Shows the tool errors of a failed task in Spanish. */
    void explainFailure(String title, TaskRunner.Result r) {
        List<String> errs = TaskRunner.toolErrors(r.output());
        StringBuilder html = new StringBuilder();
        if (errs.isEmpty()) {
            html.append("El comando ha fallado. Mira el <b>registro</b> de abajo para ver el detalle.");
            if (r.output().contains("Could not resolve") || r.output().contains("Could not GET")) {
                html.append("<br><br>Parece un problema de conexión a Internet.");
            }
        } else {
            for (String e : errs) {
                String id = ErrorText.cosmeticOf(e);
                for (String part : e.split("\n")) {
                    if (part.isBlank()) continue;
                    String pid = ErrorText.cosmeticOf(part);
                    String who = pid != null ? pid : id;
                    html.append("<p>").append(who != null ? "<b>" + Dialogs.esc(who) + ":</b> " : "")
                        .append(Dialogs.esc(ErrorText.translate(part))).append("</p>");
                    if (part.endsWith(":")) break; // a heading line like "Verification failed:"; details follow
                }
            }
            html.append("<p style='color:#9AA0AD'>No se ha publicado nada.</p>");
        }
        Dialogs.message(this, title, html.toString(), Dialogs.Kind.DANGER);
    }

    // ------------------------------------------------------------------ signature status

    void refreshSignature() {
        new SwingWorker<Manifest[], Void>() {
            @Override
            protected Manifest[] doInBackground() {
                return new Manifest[]{ManifestStatus.fetchPublished(), ManifestStatus.readLocal(project.siteDir)};
            }

            @Override
            protected void done() {
                try {
                    Manifest[] m = get();
                    published = m[0];
                    local = m[1];
                    publishedChecked = true;
                } catch (Exception ignored) {
                    // keeps the previous status
                }
                if (published == null) {
                    signatureMini.setText("Firma: sin conexión");
                    signatureMini.setForeground(Theme.TEXT_DIM);
                } else {
                    long days = ManifestStatus.daysLeft(published.expiresAt());
                    signatureMini.setText(days < 0 ? "⚠ Firma CADUCADA" : (days < ManifestStatus.WARN_DAYS ? "⚠ " : "")
                        + "Firma: caduca en " + days + " días");
                    signatureMini.setForeground(days < 0 ? Theme.ERROR : days < ManifestStatus.WARN_DAYS ? Theme.WARN : Theme.OK);
                    signatureMini.setToolTipText("Publicado: válido hasta el " + ManifestStatus.date(published.expiresAt()));
                    if (days < ManifestStatus.WARN_DAYS) {
                        log.error("La firma publicada caduca en " + days + " días. Ve a Mantenimiento > Renovar firma.");
                    }
                }
                if (inMaintenance) maintenance.refresh();
            }
        }.execute();
    }

    static void start(Project project) {
        SwingUtilities.invokeLater(() -> new MainWindow(project).setVisible(true));
    }
}
