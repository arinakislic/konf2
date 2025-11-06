import java.io.*;

public class ConfigParser {
    public static void main(String[] args) {
        System.out.println("=== Чтение конфигурации ===");

        try {
            // Создаем файл config.toml если его нет
            File configFile = new File("config.toml");
            if (!configFile.exists()) {
                System.out.println("Создаю файл config.toml...");
                createConfigFile();
            }

            // Читаем и выводим конфигурацию
            readConfigFile();

        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    // Создает файл config.toml с настройками
    public static void createConfigFile() {
        try {
            FileWriter writer = new FileWriter("config.toml");
            writer.write("package_name = example-package\n");
            writer.write("repository_url = https://example.com/repo\n");
            writer.write("test_mode = false\n");
            writer.write("package_version = 1.0.0\n");
            writer.write("output_file = graph.png\n");
            writer.write("ascii_tree = false\n");
            writer.close();
            System.out.println("Файл config.toml создан!");
        } catch (IOException e) {
            System.out.println("Не удалось создать файл: " + e.getMessage());
        }
    }

    // Читает и выводит настройки из config.toml
    public static void readConfigFile() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("config.toml"));
            String line;

            System.out.println("\nПараметры конфигурации:");
            System.out.println("-----------------------");

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    System.out.println(key + ": " + value);
                }
            }

            reader.close();

        } catch (FileNotFoundException e) {
            System.out.println("Файл config.toml не найден!");
        } catch (IOException e) {
            System.out.println("Ошибка чтения файла: " + e.getMessage());
        }
    }
}
