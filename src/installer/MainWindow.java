package installer;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

/** Главное окно приложения. */
public final class MainWindow extends JFrame {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int SPINNER_SIZE = 44;
    private static final String ALL_CATEGORIES = "Все категории";
    private static final String APP_NAME = "i. установщик по";
    private static final String APP_VERSION = "1.0.0";
    private static final String PREF_DARK_THEME = "darkTheme";

    private final AppStore store;
    private final List<AppEntry> apps;
    private final List<Boolean> selected = new ArrayList<>();
    private final List<String> statuses = new ArrayList<>();

    private final AppsTableModel model;
    private final JTable table;
    private final TableRowSorter<AppsTableModel> sorter;
    private final JTextField searchField = new JTextField();
    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final JTextArea logArea = new JTextArea();
    private final JProgressBar progress = new JProgressBar();
    private final JLabel statusLabel = new JLabel("Готово к работе");
    private final JLabel progressDetail = new JLabel(" ");
    private final JButton installButton = new JButton("Установить выбранное");
    private final JButton cancelButton = new JButton("Отмена");
    private final JCheckBox silentCheck = new JCheckBox("Тихая установка", true);
    private final JButton settingsButton = new JButton();
    private final JLabel spinnerLabel = new JLabel();

    private GifSpinner spinner;
    private String wingetVersion;

    private final AppIcons icons;

    private final Path logFile = Path.of("installer.log").toAbsolutePath();

    private volatile AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Thread worker;
    private boolean installing;
    private boolean darkTheme;
    private long lastProgressNanos;
    private String lastProgressText = "";

    public MainWindow(AppStore store, List<AppEntry> apps) {
        super("i. установщик по");
        this.store = store;
        this.apps = apps;
        for (int i = 0; i < apps.size(); i++) {
            selected.add(Boolean.FALSE);
            statuses.add("—");
        }
        this.model = new AppsTableModel();
        this.table = new JTable(model);
        this.sorter = new TableRowSorter<>(model);
        Path dataDir = store.getFile().getParent();
        this.icons = new AppIcons(dataDir == null ? Path.of("icons") : dataDir.resolve("icons"));

        fillCategories();
        loadSpinner();
        buildUi();
        applyTheme(preferredDarkTheme());

        log("Список программ: " + store.getFile());
        log("Загружено программ: " + apps.size());
        checkWinget();
    }

    // ---------- Интерфейс ----------

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(940, 660));

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildBottom(), BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(installButton);

        installButton.addActionListener(e -> startInstall());
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancel());

        setSize(1020, 760);
        setLocationRelativeTo(null);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));

        JLabel title = new JLabel(APP_NAME);
        title.putClientProperty("FlatLaf.styleClass", "h3");
        JLabel subtitle = new JLabel("Выберите программы и установите их одной кнопкой через winget");
        subtitle.putClientProperty("FlatLaf.styleClass", "small");
        subtitle.setForeground(new Color(0x808080));

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(2));
        text.add(subtitle);

        settingsButton.setText("Настройки");
        settingsButton.addActionListener(e -> openSettings());
        settingsButton.putClientProperty("JButton.buttonType", "borderless");

        JLabel credit = new JLabel("создано студией i.");
        credit.putClientProperty("FlatLaf.styleClass", "mini");
        credit.setForeground(new Color(0x9A9A9A));
        credit.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel themeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        themeWrap.add(settingsButton);
        themeWrap.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);
        rightPanel.add(themeWrap);
        rightPanel.add(Box.createVerticalStrut(4));
        rightPanel.add(credit);

        header.add(text, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenter() {
        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchField.putClientProperty("JTextField.placeholderText", "Поиск: название, категория или winget ID");
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(new JLabel("Категория:"));
        categoryBox.setPreferredSize(new Dimension(200, categoryBox.getPreferredSize().height));
        categoryBox.addActionListener(e -> applyFilter());
        filters.add(categoryBox);

        JButton selectAll = new JButton("Выбрать все");
        JButton selectNone = new JButton("Снять все");
        selectAll.addActionListener(e -> setAll(true));
        selectNone.addActionListener(e -> setAll(false));

        JPanel searchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchButtons.add(selectAll);
        searchButtons.add(selectNone);

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(filters, BorderLayout.WEST);
        searchRow.add(searchButtons, BorderLayout.EAST);

        configureTable();

        JScrollPane tableScroll = new JScrollPane(table);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setMargin(new Insets(6, 8, 6, 8));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Журнал установки"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, logScroll);
        split.setResizeWeight(0.66);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerLocation(320);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.add(searchRow, BorderLayout.NORTH);
        center.add(split, BorderLayout.CENTER);
        return center;
    }

    private void configureTable() {
        table.setRowSorter(sorter);
        table.setRowHeight(30);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.putClientProperty("JTable.intercellSpacing", new Dimension(0, 0));
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setToolTipText("");

        table.getColumnModel().getColumn(0).setMaxWidth(44);
        table.getColumnModel().getColumn(0).setMinWidth(44);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(2).setPreferredWidth(130);
        table.getColumnModel().getColumn(3).setPreferredWidth(230);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setCellRenderer(new NameRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewCol = table.columnAtPoint(e.getPoint());
                if (viewRow >= 0 && viewCol >= 0 && table.convertColumnIndexToModel(viewCol) == 4) {
                    int row = table.convertRowIndexToModel(viewRow);
                    AppEntry app = apps.get(row);
                    JOptionPane.showMessageDialog(MainWindow.this,
                            app.getDescription() + "\n\nwinget ID: " + app.getId()
                                    + "\nКатегория: " + app.getCategory() + "\nСтатус: " + statuses.get(row),
                            app.getName(), JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });
    }

    private JPanel buildBottom() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.add(installButton);
        actions.add(cancelButton);
        actions.add(silentCheck);

        JPanel progressHolder = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 12);
        progressHolder.add(progress, gbc);
        progress.setStringPainted(true);
        progress.setString("");
        progress.setPreferredSize(new Dimension(200, 20));

        spinnerLabel.setPreferredSize(new Dimension(SPINNER_SIZE, SPINNER_SIZE));
        if (spinner == null) {
            spinnerLabel.setVisible(false);
        }

        JPanel progressRow = new JPanel(new BorderLayout(0, 0));
        progressRow.add(progressHolder, BorderLayout.CENTER);
        progressRow.add(spinnerLabel, BorderLayout.EAST);

        JPanel progressBlock = new JPanel();
        progressBlock.setLayout(new BoxLayout(progressBlock, BoxLayout.Y_AXIS));
        progressRow.setAlignmentX(LEFT_ALIGNMENT);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        statusLabel.putClientProperty("FlatLaf.styleClass", "small");
        progressDetail.setForeground(new Color(0x868686));
        progressDetail.putClientProperty("FlatLaf.styleClass", "small");

        JPanel statusRow = new JPanel(new BorderLayout(14, 0));
        statusRow.add(statusLabel, BorderLayout.WEST);
        statusRow.add(progressDetail, BorderLayout.CENTER);

        progressBlock.add(progressRow);
        progressBlock.add(Box.createVerticalStrut(4));
        progressBlock.add(statusRow);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        actions.setAlignmentX(LEFT_ALIGNMENT);
        progressBlock.setAlignmentX(LEFT_ALIGNMENT);
        south.add(actions);
        south.add(Box.createVerticalStrut(10));
        south.add(progressBlock);
        return south;
    }

    /** Заполняет выпадающий список категориями из списка программ. */
    private void fillCategories() {
        categoryBox.addItem(ALL_CATEGORIES);
        java.util.TreeSet<String> categories = new java.util.TreeSet<>();
        for (AppEntry app : apps) {
            String category = app.getCategory();
            if (category != null && !category.isBlank()) {
                categories.add(category);
            }
        }
        for (String category : categories) {
            categoryBox.addItem(category);
        }
    }

    /** Фильтр по поисковой строке и по выбранной категории (работают вместе). */
    private void applyFilter() {
        String text = searchField.getText().trim().toLowerCase(Locale.ROOT);
        Object selectedItem = categoryBox.getSelectedItem();
        String selectedCategory = selectedItem == null ? null : selectedItem.toString();
        boolean allCategories = selectedCategory == null || selectedCategory.equals(ALL_CATEGORIES);

        if (text.isEmpty() && allCategories) {
            sorter.setRowFilter(null);
            return;
        }

        sorter.setRowFilter(new RowFilter<AppsTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends AppsTableModel, ? extends Integer> entry) {
                AppEntry app = apps.get(entry.getIdentifier());
                if (!allCategories && !app.getCategory().equalsIgnoreCase(selectedCategory)) {
                    return false;
                }
                if (text.isEmpty()) {
                    return true;
                }
                return app.getName().toLowerCase(Locale.ROOT).contains(text)
                        || app.getId().toLowerCase(Locale.ROOT).contains(text)
                        || app.getCategory().toLowerCase(Locale.ROOT).contains(text)
                        || app.getDescription().toLowerCase(Locale.ROOT).contains(text);
            }
        });
    }

    /**
     * Ставит/снимает галочки. Если включён фильтр — только по видимым строкам,
     * иначе по всем.
     */
    private void setAll(boolean value) {
        if (sorter.getRowFilter() == null) {
            for (int i = 0; i < selected.size(); i++) {
                selected.set(i, value);
            }
        } else {
            for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
                int modelRow = table.convertRowIndexToModel(viewRow);
                selected.set(modelRow, value);
            }
        }
        model.fireTableDataChanged();
    }

    // ---------- Тема и настройки ----------

    /** Тёмная тема — по умолчанию; выбранную тему запоминаем между запусками. */
    private static boolean preferredDarkTheme() {
        try {
            return Preferences.userNodeForPackage(MainWindow.class).getBoolean(PREF_DARK_THEME, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void rememberDarkTheme(boolean dark) {
        try {
            Preferences.userNodeForPackage(MainWindow.class).putBoolean(PREF_DARK_THEME, dark);
        } catch (Throwable ignored) {
            // хранилище настроек недоступно — не критично
        }
    }

    private void applyTheme(boolean dark) {
        this.darkTheme = dark;
        try {
            if (dark) {
                FlatDarculaLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Throwable ignored) {
            // FlatLaf недоступен — остаёмся на стандартном оформлении
        }
        UIManager.put("Table.alternateRowColor", dark ? new Color(0x2B2D30) : new Color(0xF3F5F9));
        UIManager.put("Table.showHorizontalLines", Boolean.TRUE);
        UIManager.put("Component.focusWidth", 1);
        rememberDarkTheme(dark);
        SwingUtilities.updateComponentTreeUI(this);
        revalidate();
        repaint();
    }

    /** Окно настроек: вкладки «Темы» и «О программе». */
    private void openSettings() {
        JDialog dialog = new JDialog(this, "Настройки", true);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Темы", buildThemeTab(dialog));
        tabs.addTab("О программе", buildAboutTab());
        dialog.setContentPane(tabs);
        dialog.setSize(470, 320);
        dialog.setMinimumSize(new Dimension(400, 260));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JComponent buildThemeTab(JDialog dialog) {
        JRadioButton darkOption = new JRadioButton("Тёмная", darkTheme);
        JRadioButton lightOption = new JRadioButton("Светлая", !darkTheme);
        ButtonGroup group = new ButtonGroup();
        group.add(darkOption);
        group.add(lightOption);
        darkOption.addActionListener(e -> switchTheme(dialog, true));
        lightOption.addActionListener(e -> switchTheme(dialog, false));

        JLabel header = new JLabel("Оформление");
        header.putClientProperty("FlatLaf.styleClass", "h4");

        JLabel hint = new JLabel("Изменения применяются сразу. По умолчанию — тёмная тема.");
        hint.putClientProperty("FlatLaf.styleClass", "small");
        hint.setForeground(new Color(0x9A9A9A));

        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        for (JComponent component : new JComponent[]{header, darkOption, lightOption, hint}) {
            component.setAlignmentX(LEFT_ALIGNMENT);
        }
        options.add(header);
        options.add(Box.createVerticalStrut(12));
        options.add(darkOption);
        options.add(Box.createVerticalStrut(4));
        options.add(lightOption);
        options.add(Box.createVerticalStrut(14));
        options.add(hint);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(options, BorderLayout.NORTH);
        return wrap;
    }

    private void switchTheme(JDialog dialog, boolean dark) {
        if (dark != darkTheme) {
            applyTheme(dark);
        }
        SwingUtilities.updateComponentTreeUI(dialog);
        dialog.repaint();
    }

    private JComponent buildAboutTab() {
        JLabel name = new JLabel(APP_NAME);
        name.putClientProperty("FlatLaf.styleClass", "h3");

        JLabel version = new JLabel("Версия " + APP_VERSION);
        version.putClientProperty("FlatLaf.styleClass", "small");
        version.setForeground(new Color(0x9A9A9A));

        JLabel about = new JLabel("<html>Устанавливает выбранные программы через <b>winget</b><br>"
                + "одной кнопкой, с журналом и индикатором прогресса.</html>");

        JLabel author = new JLabel("Автор: студия i.");

        JLabel stats = new JLabel("Программ в списке: " + apps.size()
                + "   ·   Категорий: " + countCategories());

        JLabel winget = new JLabel("winget: " + (wingetVersion == null ? "не найден" : wingetVersion));

        JLabel path = new JLabel("Список программ: " + store.getFile());
        path.putClientProperty("FlatLaf.styleClass", "mini");
        path.setForeground(new Color(0x9A9A9A));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        for (JComponent component : new JComponent[]{name, version, about, author, stats, winget, path}) {
            component.setAlignmentX(LEFT_ALIGNMENT);
        }
        panel.add(name);
        panel.add(version);
        panel.add(Box.createVerticalStrut(14));
        panel.add(about);
        panel.add(Box.createVerticalStrut(14));
        panel.add(author);
        panel.add(Box.createVerticalStrut(4));
        panel.add(stats);
        panel.add(Box.createVerticalStrut(4));
        panel.add(winget);
        panel.add(Box.createVerticalStrut(14));
        panel.add(path);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(panel, BorderLayout.NORTH);
        return wrap;
    }

    private long countCategories() {
        return apps.stream()
                .map(AppEntry::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .count();
    }

    // ---------- Спиннер (GIF) ----------

    private void loadSpinner() {
        try {
            InputStream in = openSpinnerStream();
            if (in == null) {
                spinnerLabel.setVisible(false);
                return;
            }
            GifSpinner loaded;
            try (InputStream stream = in) {
                loaded = new GifSpinner(stream, SPINNER_SIZE, SPINNER_SIZE, spinnerLabel);
            }
            if (!loaded.isReady()) {
                spinnerLabel.setVisible(false);
                return;
            }
            spinner = loaded;
            spinnerLabel.setIcon(spinner);
            spinnerLabel.setToolTipText("Установка выполняется...");
            BufferedImage frame = spinner.firstFrame();
            if (frame != null) {
                setIconImage(frame.getScaledInstance(64, 64, BufferedImage.SCALE_SMOOTH));
            }
        } catch (Exception e) {
            spinner = null;
            spinnerLabel.setVisible(false);
            System.err.println("Не удалось загрузить анимацию: " + e.getMessage());
        }
    }

    /** Ищет GIF: сначала ресурс внутри jar, затем файл рядом с программой. */
    private static InputStream openSpinnerStream() throws IOException {
        URL url = MainWindow.class.getResource("/boykisser-spin.gif");
        if (url != null) {
            return url.openStream();
        }
        for (String candidate : new String[]{"boykisser-spin.gif", "assets/boykisser-spin.gif"}) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return Files.newInputStream(path);
            }
        }
        return null;
    }

    private void setSpinning(boolean spinning) {
        if (spinner == null) {
            return;
        }
        spinnerLabel.setVisible(true);
        spinner.setRunning(spinning);
    }

    /** Показывает иконку программы в колонке «Программа». */
    private final class NameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            int modelRow = t.convertRowIndexToModel(row);
            AppEntry app = apps.get(modelRow);
            setIcon(icons.icon(app, () -> table.repaint()));
            setIconTextGap(9);
            setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            return this;
        }
    }

    /** Раскрашивает статус программы в зависимости от результата. */
    private final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            if (isSelected) {
                return c;
            }
            String value2 = value == null ? "" : value.toString();
            Color color = t.getForeground();
            switch (value2) {
                case "Готово":
                    color = darkTheme ? new Color(0x66D17A) : new Color(0x1B8A3A);
                    break;
                case "Ошибка":
                    color = darkTheme ? new Color(0xFF7B72) : new Color(0xD93025);
                    break;
                case "Установка...":
                    color = darkTheme ? new Color(0x6CB6FF) : new Color(0x1A73E8);
                    break;
                case "Уже установлено":
                    color = darkTheme ? new Color(0xC3A6FF) : new Color(0x7B4FD1);
                    break;
                case "Пропущено":
                    color = new Color(0x909090);
                    break;
                default:
                    break;
            }
            c.setForeground(color);
            return c;
        }
    }

    // ---------- Установка ----------

    private void checkWinget() {
        log("Проверка winget...");
        String version = Winget.version();
        if (version == null) {
            log("ОШИБКА: winget не найден. Установите «App Installer» из Microsoft Store.");
            JOptionPane.showMessageDialog(this,
                    "winget не найден.\n\nУстановите приложение «App Installer» из Microsoft Store,\n"
                            + "после чего перезапустите программу.",
                    "winget не найден", JOptionPane.ERROR_MESSAGE);
            installButton.setEnabled(false);
        } else {
            log("winget найден: " + version);
            wingetVersion = version;
        }
    }

    private void startInstall() {
        if (installing) {
            return;
        }
        List<Integer> queue = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            if (Boolean.TRUE.equals(selected.get(i)) && !"Готово".equals(statuses.get(i))) {
                queue.add(i);
            }
        }
        if (queue.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Отметьте хотя бы одну программу.",
                    "Ничего не выбрано", JOptionPane.WARNING_MESSAGE);
            return;
        }

        installing = true;
        cancelled = new AtomicBoolean(false);
        installButton.setEnabled(false);
        cancelButton.setEnabled(true);
        silentCheck.setEnabled(false);
        progress.setMaximum(queue.size());
        progress.setValue(0);
        progress.setString("0 / " + queue.size());
        progressDetail.setText(" ");
        lastProgressText = "";
        setSpinning(true);
        log("");
        log("=== Начало установки: " + queue.size() + " программ ===");
        persistLog();

        boolean silent = silentCheck.isSelected();
        worker = new Thread(() -> {
            int ok = 0;
            int failed = 0;
            int done = 0;
            for (int index : queue) {
                if (cancelled.get()) {
                    setStatus(index, "Пропущено");
                    continue;
                }
                AppEntry app = apps.get(index);
                setStatus(index, "Установка...");
                setStatusLabel("Устанавливается: " + app.getName());
                log("");
                log("--- " + app.getName() + " [" + app.getId() + "] ---");
                StringBuilder output = new StringBuilder();
                try {
                    int code = Winget.install(app, silent,
                            line -> {
                                output.append(line).append('\n');
                                logLine(line);
                            },
                            this::onProgress, cancelled);
                    if (code == 0) {
                        ok++;
                        setStatus(index, "Готово");
                        log("OK: " + app.getName() + " установлено.");
                    } else if (code == -1) {
                        setStatus(index, "Пропущено");
                        log("Отменено пользователем.");
                    } else if (isAlreadyInstalled(output.toString())) {
                        ok++;
                        setStatus(index, "Уже установлено");
                        log("Уже установлено: " + app.getName() + " — пропускаем.");
                    } else {
                        failed++;
                        setStatus(index, "Ошибка");
                        log("ОШИБКА: " + app.getName() + " — код возврата " + code + ".");
                    }
                } catch (Exception ex) {
                    failed++;
                    setStatus(index, "Ошибка");
                    log("ОШИБКА: " + app.getName() + " — " + ex.getMessage());
                }
                done++;
                final int progressValue = done;
                SwingUtilities.invokeLater(() -> {
                    progress.setValue(progressValue);
                    progress.setString(progressValue + " / " + queue.size());
                });
                persistLog();
            }
            final int okCount = ok;
            final int failCount = failed;
            final boolean wasCancelled = cancelled.get();
            SwingUtilities.invokeLater(() -> {
                installing = false;
                installButton.setEnabled(true);
                cancelButton.setEnabled(false);
                silentCheck.setEnabled(true);
                setSpinning(false);
                setStatusLabel("Завершено");
                progressDetail.setText(" ");
                log("");
                log("=== Итог: успешно " + okCount + ", с ошибкой " + failCount
                        + (wasCancelled ? ", отменено" : "") + " ===");
                persistLog();
                String message = "Установка завершена.\n\nУспешно: " + okCount
                        + "\nС ошибкой: " + failCount;
                JOptionPane.showMessageDialog(MainWindow.this, message, "Установка завершена",
                        failCount == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            });
        }, "winget-installer");
        worker.setDaemon(true);
        worker.start();
    }

    private void cancel() {
        if (installing) {
            cancelled.set(true);
            log("Запрошена отмена...");
            cancelButton.setEnabled(false);
        }
    }

    private void setStatus(int index, String value) {
        statuses.set(index, value);
        SwingUtilities.invokeLater(() -> model.fireTableRowsUpdated(index, index));
    }

    private void setStatusLabel(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    /**
     * winget возвращает ненулевой код, если пакет уже установлен (или обновлять нечего).
     * Это не ошибка — показываем «Уже установлено».
     */
    private static boolean isAlreadyInstalled(String output) {
        String text = output.toLowerCase();
        return text.contains("already installed")
                || text.contains("already exists")
                || text.contains("no newer package versions")
                || text.contains("no applicable upgrade")
                || text.contains("no applicable installer")
                || text.contains("уже установлен")
                || text.contains("нет доступных обновлений");
    }

    // ---------- Журнал ----------

    private void log(String line) {
        SwingUtilities.invokeLater(() -> appendToLog("[" + LocalDateTime.now().format(TS) + "] " + line + System.lineSeparator()));
    }

    /** Обычная строка вывода winget — попадает в журнал. */
    private void logLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        String text = line.length() > 300 ? line.substring(0, 300) + "..." : line;
        SwingUtilities.invokeLater(() -> appendToLog("    " + text + System.lineSeparator()));
    }

    /**
     * Прогресс winget (winget перезаписывает строку через \r, поэтому в журнал он не идёт —
     * иначе журнал превращается в сотни строк с «крутящейся палкой»). Показываем его
     * отдельной подписью под прогресс-баром с ограничением частоты обновлений.
     */
    private void onProgress(String text) {
        long now = System.nanoTime();
        if (text.equals(lastProgressText) || now - lastProgressNanos < 90_000_000L) {
            return;
        }
        lastProgressNanos = now;
        lastProgressText = text;
        String shown = text.length() > 120 ? text.substring(0, 120) + "..." : text;
        SwingUtilities.invokeLater(() -> progressDetail.setText(shown));
    }

    private void appendToLog(String text) {
        logArea.append(text);
        int maxLength = 300_000;
        int length = logArea.getDocument().getLength();
        if (length > maxLength) {
            try {
                logArea.getDocument().remove(0, length - maxLength / 2);
            } catch (javax.swing.text.BadLocationException ignored) {
                // не критично
            }
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void persistLog() {
        try {
            String text = logArea.getText();
            Files.write(logFile, text.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Не удалось записать журнал: " + e.getMessage());
        }
    }

    // ---------- Модель таблицы ----------

    private final class AppsTableModel extends AbstractTableModel {

        private final String[] columns = {"", "Программа", "Категория", "winget ID", "Статус"};

        @Override
        public int getRowCount() {
            return apps.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0 && !installing;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            AppEntry app = apps.get(rowIndex);
            switch (columnIndex) {
                case 0: return selected.get(rowIndex);
                case 1: return app.getName();
                case 2: return app.getCategory();
                case 3: return app.getId();
                case 4: return statuses.get(rowIndex);
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                selected.set(rowIndex, Boolean.TRUE.equals(value));
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
