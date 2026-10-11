package installer;

/** Одна программа из списка apps.json. */
public final class AppEntry {

    private final String id;
    private final String name;
    private final String category;
    private final String description;
    private final String domain;
    private final String icon;

    public AppEntry(String id, String name, String category, String description,
                    String domain, String icon) {
        this.id = id;
        this.name = name;
        this.category = category == null ? "" : category;
        this.description = description == null ? "" : description;
        this.domain = domain == null ? "" : domain;
        this.icon = icon == null ? "" : icon;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    /** Домен для поиска favicon (необязательно). */
    public String getDomain() {
        return domain;
    }

    /** Прямая ссылка или путь к иконке (необязательно, имеет приоритет над доменом). */
    public String getIcon() {
        return icon;
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
