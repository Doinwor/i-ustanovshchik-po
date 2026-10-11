package installer;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.List;

/** Точка входа приложения «i. установщик по». */
public final class Main {

    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
        } catch (Throwable ignored) {
            // FlatLaf недоступен — используем стандартное оформление
        }

        SwingUtilities.invokeLater(() -> {
            AppStore store = AppStore.locate();
            List<AppEntry> apps;
            try {
                apps = store.load();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Не удалось прочитать список программ:\n" + store.getFile() + "\n\n" + e.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (apps.isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        "Файл apps.json пуст или не найден:\n" + store.getFile(),
                        "Нет списка программ", JOptionPane.WARNING_MESSAGE);
            }
            new MainWindow(store, apps).setVisible(true);
        });
    }
}
