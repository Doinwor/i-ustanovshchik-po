package installer;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Иконки программ для списка.
 * <p>
 * Порядок получения: файл в папке {@code icons} → локальный файл/ссылка из apps.json →
 * favicon по домену (несколько провайдеров) → буквенный аватар (работает всегда, без сети).
 * Скачанные иконки кэшируются на диск, поэтому повторный запуск работает офлайн.
 */
final class AppIcons {

    private static final int SIZE = 20;
    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int READ_TIMEOUT_MS = 10000;

    private static final Color[] PALETTE = {
            new Color(0x2D7FF9), new Color(0x34A853), new Color(0xE8710A),
            new Color(0xD93025), new Color(0x8E24AA), new Color(0x00A0B0),
            new Color(0xF09300), new Color(0x5F6DE8)
    };

    private final Path cacheDir;
    private final Map<String, Icon> cache = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool;

    AppIcons(Path cacheDir) {
        this.cacheDir = cacheDir;
        AtomicInteger counter = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "icon-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException ignored) {
            // кэш недоступен — обойдёмся аватарами
        }
    }

    /** Возвращает иконку сразу (аватар как заглушка), а загрузку выполняет в фоне. */
    Icon icon(AppEntry app, Runnable onLoaded) {
        Icon cached = cache.get(app.getId());
        if (cached != null) {
            return cached;
        }
        Icon avatar = avatar(app);
        cache.put(app.getId(), avatar);
        request(app, onLoaded);
        return avatar;
    }

    private void request(AppEntry app, Runnable onLoaded) {
        if (app.getIcon().isEmpty() && app.getDomain().isEmpty()) {
            return;
        }
        if (!pending.add(app.getId())) {
            return;
        }
        pool.submit(() -> {
            try {
                Image image = loadImage(app);
                if (image != null) {
                    cache.put(app.getId(), new ImageIcon(scale(image)));
                    if (onLoaded != null) {
                        SwingUtilities.invokeLater(onLoaded);
                    }
                }
            } catch (Exception ignored) {
                // остаёмся на аватаре
            } finally {
                pending.remove(app.getId());
            }
        });
    }

    private Image loadImage(AppEntry app) {
        Path cachedFile = cacheDir.resolve(fileName(app.getId()));
        if (Files.isRegularFile(cachedFile)) {
            try (InputStream in = Files.newInputStream(cachedFile)) {
                BufferedImage image = ImageIO.read(in);
                if (image != null) {
                    return image;
                }
            } catch (IOException ignored) {
                // битый кэш — попробуем скачать заново
            }
        }

        List<String> sources = new ArrayList<>();
        if (!app.getIcon().isEmpty()) {
            sources.add(app.getIcon());
        }
        if (!app.getDomain().isEmpty()) {
            sources.add("https://www.google.com/s2/favicons?sz=128&domain=" + app.getDomain());
            sources.add("https://favicon.yandex.net/favicon/" + app.getDomain());
        }

        for (String source : sources) {
            try {
                byte[] bytes = read(source);
                if (bytes == null) {
                    continue;
                }
                BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (image == null) {
                    continue;
                }
                save(cachedFile, image);
                return image;
            } catch (Exception ignored) {
                // пробуем следующий источник
            }
        }
        return null;
    }

    /** Читает локальный файл или скачивает по HTTP. */
    private byte[] read(String source) throws IOException {
        String lower = source.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return download(source);
        }
        Path path = Path.of(source);
        if (Files.isRegularFile(path)) {
            return Files.readAllBytes(path);
        }
        return null;
    }

    private byte[] download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (installer-app)");
        connection.setRequestProperty("Accept", "image/*");
        try {
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                byte[] data = in.readAllBytes();
                return data.length == 0 ? null : data;
            }
        } finally {
            connection.disconnect();
        }
    }

    private void save(Path file, BufferedImage image) {
        try {
            BufferedImage target = new BufferedImage(SIZE * 2, SIZE * 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = target.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, SIZE * 2, SIZE * 2, null);
            g.dispose();
            ImageIO.write(target, "png", file.toFile());
        } catch (Exception ignored) {
            // кэш не критичен
        }
    }

    private static Image scale(Image source) {
        BufferedImage target = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = source.getWidth(null);
        int height = source.getHeight(null);
        if (width <= 0 || height <= 0) {
            g.drawImage(source, 0, 0, SIZE, SIZE, null);
        } else {
            double k = Math.min(SIZE / (double) width, SIZE / (double) height);
            int w = Math.max(1, (int) Math.round(width * k));
            int h = Math.max(1, (int) Math.round(height * k));
            g.drawImage(source, (SIZE - w) / 2, (SIZE - h) / 2, w, h, null);
        }
        g.dispose();
        return target;
    }

    private static String fileName(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
    }

    /** Буквенный аватар: цветной квадрат с первой буквой названия. */
    private static Icon avatar(AppEntry app) {
        Color color = PALETTE[Math.floorMod(app.getId().hashCode(), PALETTE.length)];
        String letter = firstLetter(app.getName());
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(color);
        g.fill(new RoundRectangle2D.Float(0, 0, SIZE, SIZE, SIZE * 0.42f, SIZE * 0.42f));
        g.setColor(Color.WHITE);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(SIZE * 0.62f));
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int x = (SIZE - metrics.stringWidth(letter)) / 2;
        int y = (SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(letter, x, y);
        g.dispose();
        return new ImageIcon(image);
    }

    private static String firstLetter(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                return String.valueOf(Character.toUpperCase(c));
            }
        }
        return "?";
    }
}
