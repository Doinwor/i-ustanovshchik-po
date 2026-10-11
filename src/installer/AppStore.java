package installer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Загрузка и сохранение списка программ из файла apps.json. */
public final class AppStore {

    private final Path file;

    public AppStore(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    /** Ищет apps.json рядом с приложением, в рабочей папке и рядом с jar-файлом. */
    public static AppStore locate() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("apps.json").toAbsolutePath());
        candidates.add(Paths.get("..", "apps.json").toAbsolutePath());
        Path jarDir = jarDirectory();
        if (jarDir != null) {
            candidates.add(jarDir.resolve("apps.json"));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return new AppStore(candidate.normalize());
            }
        }
        return new AppStore(candidates.get(0).normalize());
    }

    private static Path jarDirectory() {
        try {
            Path self = Paths.get(AppStore.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isDirectory(self) ? self : self.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    public List<AppEntry> load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Object root = Json.parse(text);
        List<AppEntry> apps = new ArrayList<>();
        if (!(root instanceof List)) {
            throw new IOException("apps.json должен содержать массив программ.");
        }
        for (Object item : (List<?>) root) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> obj = (Map<?, ?>) item;
            String id = str(obj.get("id"));
            if (id.isEmpty()) {
                continue;
            }
            String name = str(obj.get("name"));
            apps.add(new AppEntry(
                    id,
                    name.isEmpty() ? id : name,
                    str(obj.get("category")),
                    str(obj.get("description")),
                    str(obj.get("domain")),
                    str(obj.get("icon"))));
        }
        return apps;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
