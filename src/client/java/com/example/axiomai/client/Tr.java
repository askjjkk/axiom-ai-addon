package com.example.axiomai.client;

import java.util.Locale;
import java.util.Map;

/** Window texts: English in the code, Russian here. The language follows the computer unless config.json says otherwise. */
final class Tr {
    private static boolean russian = Locale.getDefault().getLanguage().equals("ru");

    private static final Map<String, String> RU = Map.ofEntries(
            Map.entry("New chat", "Новый чат"),
            Map.entry("Search chats...", "Поиск по чатам..."),
            Map.entry("Today", "Сегодня"),
            Map.entry("Yesterday", "Вчера"),
            Map.entry("Earlier", "Ранее"),
            Map.entry("now", "сейчас"),
            Map.entry("min", "мин"),
            Map.entry("h", "ч"),
            Map.entry("d", "д"),
            Map.entry("Chats with no activity for %d days are deleted automatically.",
                    "Чаты без активности больше %d дн. удаляются автоматически."),
            Map.entry("Delete this chat?", "Удалить этот чат?"),
            Map.entry("The chat history will be removed.", "История чата будет удалена."),
            Map.entry("Brushes already saved in Axiom stay where they are.", "Сохранённые в Axiom кисти останутся."),
            Map.entry("Delete", "Удалить"),
            Map.entry("Cancel", "Отмена"),
            Map.entry("Rename", "Переименовать"),
            Map.entry("Settings", "Настройки"),
            Map.entry("Text size", "Размер текста"),
            Map.entry("Modern look", "Современный вид"),
            Map.entry("Copy config folder path", "Скопировать путь к конфигу"),
            Map.entry("Model", "Модель"),
            Map.entry("spent this session: about %.2f RUB", "за сессию потрачено: около %.2f руб."),
            Map.entry("What brush do you want to create?", "Какую кисть создадим?"),
            Map.entry("Describe it in your own words. Then keep chatting to adjust it.",
                    "Опишите её своими словами, а потом уточняйте в этом же чате."),
            Map.entry("Describe the brush...", "Опишите кисть..."),
            Map.entry("Enter - send, Shift+Enter - new line", "Enter - отправить, Shift+Enter - новая строка"),
            Map.entry("Send", "Отправить"),
            Map.entry("Stop", "Остановить"),
            Map.entry("Copy", "Копировать"),
            Map.entry("Copied", "Скопировано"),
            Map.entry("Regenerate", "Сгенерировать заново"),
            Map.entry("Regenerate with %s", "Заново: %s"),
            Map.entry("same request, new answer (costs again)", "тот же запрос, новый ответ (снова платно)"),
            Map.entry("Retry", "Повторить"),
            Map.entry("Error", "Ошибка"),
            Map.entry("You", "Вы"),
            Map.entry("AI Create", "AI Create"),
            Map.entry("saved as preset: %s", "сохранено как пресет: %s"),
            Map.entry("Saved as preset", "Сохранено как пресет"),
            Map.entry("Show all %d lines", "Показать все %d строк"),
            Map.entry("Collapse", "Свернуть"),
            Map.entry("lines", "строк"),
            Map.entry("Stopped.", "Остановлено."),
            Map.entry("Asking %s", "Запрос к %s"),
            Map.entry("Asking the AI", "Запрос к ИИ"),
            Map.entry("Testing the brush in a simulated world", "Проверка кисти в тестовом мире"),
            Map.entry("Fixing the brush (attempt %d)", "Исправление кисти (попытка %d)"),
            Map.entry("Asking the stronger model %s", "Сильная модель %s переписывает кисть"),
            Map.entry("Saving the preset", "Сохранение пресета"),
            Map.entry("Natural grass", "Естественная трава"),
            Map.entry("Short grass, tall grass and ferns in clumps", "Трава, высокая трава и папоротники кучками"),
            Map.entry("Thin snow", "Тонкий снег"),
            Map.entry("Snow layers of different heights on a block", "Слои снега разной высоты на выбранном блоке"),
            Map.entry("Weathered stone", "Состаренный камень"),
            Map.entry("Cracked and mossy bricks on the visible surface", "Потрескавшиеся и замшелые кирпичи на поверхности"),
            Map.entry("Sphere", "Сфера"),
            Map.entry("A sphere with adjustable radius, solid or hollow", "Сфера с настраиваемым радиусом, сплошная или пустая"),
            Map.entry("Natural grass: short grass, tall grass and a few ferns, planted in clumps with a density slider",
                    "Естественная трава: низкая и высокая трава и немного папоротников, кучками, с ползунком плотности"),
            Map.entry("Thin natural snow layers of different heights on top of oak planks, like a real snow cover",
                    "Тонкие слои снега разной высоты поверх дубовых досок, как настоящий снежный покров"),
            Map.entry("Weather stone bricks: some become cracked or mossy, only on the visible surface",
                    "Состарить каменные кирпичи: часть становится потрескавшейся или замшелой, только на видимой поверхности"),
            Map.entry("Build a sphere with adjustable radius, optionally hollow",
                    "Построить сферу с настраиваемым радиусом, при желании пустую"),
            Map.entry("Could not connect to the AI server. Check your internet connection.",
                    "Нет соединения с сервером ИИ. Проверьте интернет."),
            Map.entry("The AI server rejected the key or the access. Check openaiApiKey in config.json and that the key has access to the service",
                    "Сервер ИИ не принял ключ или доступ. Проверьте openaiApiKey в config.json и права ключа на сервис"),
            Map.entry("The AI account has no money for this request. Top up the balance",
                    "На счёте ИИ нет денег для этого запроса. Пополните баланс"),
            Map.entry("Address or model not found. Check openaiBaseUrl and openaiModel in config.json",
                    "Адрес или модель не найдены. Проверьте openaiBaseUrl и openaiModel в config.json"),
            Map.entry("Too many requests or a limit was reached. Wait a minute and try again",
                    "Слишком много запросов или достигнут лимит. Подождите минуту и повторите"),
            Map.entry("The AI server has a temporary problem (it was already retried automatically). Press Retry in a minute, or choose another model in config.json",
                    "У сервера ИИ временная проблема (запрос уже повторялся автоматически). Нажмите «Повторить» через минуту или выберите другую модель в config.json"),
            Map.entry("The AI server returned an error", "Сервер ИИ вернул ошибку"),
            Map.entry("The AI server took too long to answer. Try again.", "Сервер ИИ слишком долго отвечал. Попробуйте ещё раз."),
            Map.entry("Auto-check passed.", "Автопроверка пройдена."),
            Map.entry("Other ideas", "Другие идеи"),
            Map.entry("Name ideas:", "Название пресета:"),
            Map.entry("Own name...", "Своё название..."),
            Map.entry("Apply", "Применить"),
            Map.entry("updated preset: %s", "пресет обновлён: %s"),
            Map.entry("This request has no answer yet (it may have been stopped).",
                    "На этот запрос пока нет ответа (возможно, вы его остановили)."),
            Map.entry("Resend", "Отправить снова"),
            Map.entry("Copy request", "Копировать запрос"),
            Map.entry("Change the same preset when refining", "При уточнении менять тот же пресет"),
            Map.entry("The preset file is gone (it was deleted). Send the request again to create it.",
                    "Файл пресета удалён. Отправьте запрос снова, чтобы создать его."),
            Map.entry("tokens", "токенов"),
            Map.entry("RUB", "руб."));

    private Tr() {
    }

    static void setLanguage(String code) {
        if ("ru".equalsIgnoreCase(code)) russian = true;
        else if ("en".equalsIgnoreCase(code)) russian = false;
        else russian = Locale.getDefault().getLanguage().equals("ru");
    }

    static boolean isRussian() {
        return russian;
    }

    static String t(String english) {
        if (!russian) return english;
        String translated = RU.get(english);
        return translated == null ? english : translated;
    }

    /** Translates the long texts that the core produces (server errors); unknown text is kept as it is. */
    static String translateKnown(String message) {
        if (!russian || message == null) return message;
        String out = message;
        for (Map.Entry<String, String> e : RU.entrySet()) {
            if (e.getKey().length() >= 20 && out.contains(e.getKey())) out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    /** The auto-check remark under an answer. */
    static String note(String note) {
        if (!russian || note == null) return note;
        if (note.equals("Auto-check passed.")) return "Автопроверка пройдена.";
        java.util.regex.Matcher fixed = java.util.regex.Pattern
                .compile("Auto-check: the AI fixed (\\d+) problem\\(s\\) in this brush before saving\\.").matcher(note);
        if (fixed.matches()) return "Автопроверка: ИИ исправил проблем до сохранения: " + fixed.group(1) + ".";
        java.util.regex.Matcher stronger = java.util.regex.Pattern
                .compile("Auto-check: the first model's brush had problems, so the stronger model(?: \\((.*)\\))? rewrote it\\.").matcher(note);
        if (stronger.matches()) {
            return "Автопроверка: у первой модели были проблемы, поэтому кисть переписала более сильная модель"
                    + (stronger.group(1) == null ? "" : " (" + stronger.group(1) + ")") + ".";
        }
        String prefix = "Auto-check could not fully verify this brush: ";
        if (note.startsWith(prefix)) return "Автопроверка не смогла полностью проверить кисть: " + note.substring(prefix.length());
        return translateKnown(note);
    }

    /** The "model | tokens | cost" line under an answer. */
    static String meta(String meta) {
        if (!russian || meta == null) return meta;
        String out = meta.replaceAll("(\\d+) in / (\\d+) out tokens", "$1 вх. / $2 вых. токенов");
        return out.replaceAll("about ([0-9.]+) RUB", "около $1 руб.");
    }

    /** Translates a pattern and fills in its values, for example t("Show all %d lines", 30). */
    static String t(String english, Object... args) {
        return String.format(Locale.ROOT, t(english), args);
    }
}
