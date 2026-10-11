package installer;

import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Анимированный GIF в роли {@link Icon}: кадры заранее масштабируются и
 * переключаются {@link Timer}. Рисовать через {@code ImageIcon.drawImage} нельзя —
 * тогда анимация не воспроизводится.
 */
final class GifSpinner implements Icon {

    private static final int MIN_DELAY_MS = 40;
    private static final int DEFAULT_DELAY_MS = 100;

    private final List<BufferedImage> frames = new ArrayList<>();
    private final List<Integer> delays = new ArrayList<>();
    private final int width;
    private final int height;
    private final JComponent view;

    private Timer timer;
    private int index;
    private boolean running;

    GifSpinner(InputStream in, int width, int height, JComponent view) throws IOException {
        this.width = width;
        this.height = height;
        this.view = view;
        readFrames(in);
    }

    boolean isReady() {
        return !frames.isEmpty();
    }

    BufferedImage firstFrame() {
        return frames.isEmpty() ? null : frames.get(0);
    }

    private void readFrames(InputStream in) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false);
                int count = reader.getNumImages(true);
                for (int i = 0; i < count; i++) {
                    frames.add(scale(reader.read(i)));
                    delays.add(readDelay(reader, i));
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage scale(BufferedImage source) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return target;
    }

    private static int readDelay(ImageReader reader, int imageIndex) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(imageIndex);
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            NodeList extensions = root.getElementsByTagName("GraphicControlExtension");
            if (extensions.getLength() > 0) {
                String value = ((IIOMetadataNode) extensions.item(0)).getAttribute("delayTime");
                return Math.max(Integer.parseInt(value) * 10, MIN_DELAY_MS);
            }
        } catch (Exception ignored) {
            // метаданных нет — используем задержку по умолчанию
        }
        return DEFAULT_DELAY_MS;
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        if (frames.isEmpty()) {
            return;
        }
        g.drawImage(frames.get(index), x, y, null);
    }

    void setRunning(boolean run) {
        if (frames.isEmpty()) {
            return;
        }
        if (run) {
            start();
        } else {
            stop();
        }
    }

    private void start() {
        if (running) {
            return;
        }
        running = true;
        if (timer == null) {
            timer = new Timer(delays.get(index), e -> nextFrame());
            timer.setRepeats(true);
        }
        timer.setInitialDelay(delays.get(index));
        timer.setDelay(delays.get(index));
        timer.start();
    }

    private void nextFrame() {
        index = (index + 1) % frames.size();
        if (timer != null) {
            timer.setInitialDelay(delays.get(index));
            timer.setDelay(delays.get(index));
        }
        if (view != null) {
            view.repaint();
        }
    }

    private void stop() {
        running = false;
        if (timer != null) {
            timer.stop();
        }
        index = 0;
        if (view != null) {
            view.repaint();
        }
    }
}
