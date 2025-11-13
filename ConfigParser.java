import java.io.*;
import java.net.URL;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        System.out.println("=== Этап 1: Загрузка конфигурационного файла ===");
        System.out.print("Введите команду (например: config config.toml): ");

        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine().trim();

        if (!line.startsWith("config")) {
            System.out.println("Ошибка: команда должна начинаться с 'config'.");
            return;
        }

        // Разбираем команду: "config" или "config путь"
        String[] parts = line.split("\\s+", 2);
        String configPath;
        if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
            configPath = parts[1].trim();
        } else {
            // путь по умолчанию, если аргумент не передан
            configPath = "src/main/resources/config.toml";
            System.out.println("Аргумент не указан — используется путь по умолчанию: " + configPath);
        }

        Map<String, String> config;
        try {
            config = readToml(configPath);
            if (config.isEmpty()) {
                System.out.println("Ошибка: конфигурационный файл пуст или не содержит параметров.");
                return;
            }
            System.out.println("Конфигурация успешно загружена.\n");
            System.out.println("Параметры:");
            for (String key : config.keySet()) {
                System.out.println(" " + key + " = " + config.get(key));
            }
        } catch (FileNotFoundException e) {
            System.out.println("Ошибка при чтении конфигурации: файл не найден — " + configPath);
            return;
        } catch (Exception e) {
            System.out.println("Ошибка при чтении конфигурации: " + e.getMessage());
            return;
        }

        System.out.println("\n=== Этап 2: Сбор данных о зависимостях ===");

        String repoPath = config.getOrDefault("repository_path", "").trim();
        String repoUrl = config.getOrDefault("repository_url", "").trim();
        String packageName = config.getOrDefault("package_name", "—");
        String packageVersion = config.getOrDefault("package_version", "—");

        // ДЕБАГ: посмотрим что получили из конфига
        System.out.println("DEBUG: repository_path = '" + repoPath + "'");
        System.out.println("DEBUG: repository_url = '" + repoUrl + "'");
        System.out.println("DEBUG: package_name = '" + packageName + "'");
        System.out.println("DEBUG: package_version = '" + packageVersion + "'");

        // Приоритет — локальный путь, затем URL
        if (!repoPath.isEmpty()) {
            File cargo = new File(repoPath, "Cargo.toml");
            if (!cargo.exists()) {
                System.out.println("Ошибка: Cargo.toml не найден по локальному пути: " + cargo.getAbsolutePath());
            } else {
                System.out.println("Найден локальный Cargo.toml: " + cargo.getAbsolutePath());
                try {
                    String content = readFile(cargo.getAbsolutePath());
                    Map<String, String> deps = extractDependencies(content);
                    printDependencies(packageName, packageVersion, deps);
                } catch (Exception e) {
                    System.out.println("Ошибка чтения локального Cargo.toml: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } else if (!repoUrl.isEmpty()) {
            // Убедимся, что URL корректен
            String cargoUrl;
            if (repoUrl.endsWith("Cargo.toml")) {
                cargoUrl = repoUrl;
            } else {
                if (!repoUrl.endsWith("/")) repoUrl += "/";
                cargoUrl = repoUrl + "Cargo.toml";
            }
            System.out.println("Попытка загрузить Cargo.toml по URL:\n" + cargoUrl);
            try {
                String content = readUrl(cargoUrl);
                System.out.println("Cargo.toml успешно загружен.");
                System.out.println("Содержимое (первые 500 символов): " +
                        (content.length() > 500 ? content.substring(0, 500) + "..." : content));

                Map<String, String> deps = extractDependencies(content);
                System.out.println("Найдено зависимостей: " + deps.size());
                printDependencies(packageName, packageVersion, deps);
            } catch (FileNotFoundException e) {
                System.out.println("Ошибка: Cargo.toml не найден по URL: " + cargoUrl);
            } catch (Exception e) {
                System.out.println("Ошибка при загрузке/анализе Cargo.toml: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Ошибка: ни 'repository_path', ни 'repository_url' не указаны в конфиге.");
        }

        System.out.println("\nПроцесс завершён.");
    }

    // ----- Вспомогательные методы -----

    // Чтение TOML-конфига (очень простой парсер key = value)
    public static Map<String, String> readToml(String filePath) throws Exception {
        Map<String, String> config = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue;
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    // удаляем кавычки вокруг значения, если есть
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    config.put(key, value);
                }
            }
        }
        return config;
    }

    // Чтение локального файла в строку
    public static String readFile(String path) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String l;
            while ((l = r.readLine()) != null) {
                sb.append(l).append("\n");
            }
        }
        return sb.toString();
    }

    // Загрузка содержимого по URL
    public static String readUrl(String urlStr) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL(urlStr).openStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine).append("\n");
            }
        }
        return content.toString();
    }

    // Извлечение зависимостей из секции [dependencies] в Cargo.toml
    public static Map<String, String> extractDependencies(String cargoToml) {
        Map<String, String> dependencies = new LinkedHashMap<>();
        boolean inDeps = false;
        for (String rawLine : cargoToml.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("[dependencies]")) {
                inDeps = true;
                continue;
            }
            if (inDeps && line.startsWith("[")) {
                // Началась новая секция: выходим из раздела зависимостей
                break;
            }
            if (inDeps) {
                // Пропускаем комментарии и пустые строки
                if (line.isEmpty() || line.startsWith("#")) continue;
                // Форматы зависимостей могут быть разные:
                // serde = "1.0"
                // mycrate = { version = "1.2", features = [...] }
                // mycrate = { git = "...", branch = "..." }
                // Для простоты: если есть "=", берем левую часть как имя,
                // а пытаемся извлечь version="..." из правой части или взять правую как простую версию.
                if (line.contains("=")) {
                    String[] kv = line.split("=", 2);
                    String name = kv[0].trim();
                    String rhs = kv[1].trim();
                    String version = "";

                    // Если rhs — простая строка версии: "1.0"
                    if (rhs.startsWith("\"") && rhs.endsWith("\"")) {
                        version = rhs.substring(1, rhs.length() - 1);
                    } else if (rhs.startsWith("{") && rhs.endsWith("}")) {
                        // Внутри фигурных скобок ищем version = "..."
                        String inside = rhs.substring(1, rhs.length() - 1);
                        // простая попытка найти version = "..."
                        int idx = inside.indexOf("version");
                        if (idx >= 0) {
                            String after = inside.substring(idx);
                            String[] parts = after.split("=", 2);
                            if (parts.length == 2) {
                                String val = parts[1].trim();
                                // val может содержать дополнительные поля, берем до запятой
                                if (val.endsWith(",")) val = val.substring(0, val.length() - 1).trim();
                                if (val.startsWith("\"") && val.endsWith("\"")) {
                                    version = val.substring(1, val.length() - 1);
                                } else {
                                    // если версия указана иначе
                                    version = val.replaceAll("[,\\}].*$", "").trim().replace("\"", "");
                                }
                            }
                        } else {
                            version = "(нет явной версии)";
                        }
                    } else {
                        // другой формат — просто записываем rhs как есть (без кавычек)
                        version = rhs.replaceAll("\"", "").trim();
                    }

                    dependencies.put(name, version);
                }
            }
        }
        return dependencies;
    }

    // Печать зависимостей в красивом формате
    public static void printDependencies(String packageName, String packageVersion, Map<String, String> deps) {
        System.out.println();
        System.out.println("Прямые зависимости пакета \"" + packageName + "\" (версия " + packageVersion + "):");
        if (deps.isEmpty()) {
            System.out.println(" (Нет прямых зависимостей)");
            return;
        }
        for (Map.Entry<String, String> e : deps.entrySet()) {
            System.out.println(" - " + e.getKey() + " => " + e.getValue());
        }
    }
}
