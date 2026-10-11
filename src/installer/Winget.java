package installer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Обёртка над консольной утилитой winget. */
public final class Winget {

    /**
     * Источник пакетов. Явно указываем winget: иначе winget опрашивает и msstore,
     * который часто падает с ошибкой сертификата (0x8A15005E), и установка срывается
     * из-за неоднозначности ("Please specify one of them using the --source option").
     */
    private static final String SOURCE = "winget";

    private static final Charset FALLBACK_CHARSET = consoleCharset();

    private Winget() {
    }

    private static Charset consoleCharset() {
        String name = System.getProperty("sun.stdout.encoding");
        if (name != null) {
            try {
                if (Charset.isSupported(name)) {
                    return Charset.forName(name);
                }
            } catch (Exception ignored) {
                // некорректное имя кодировки — идём дальше
            }
        }
        try {
            return Charset.forName("CP866");
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }

    /** Проверяет, доступен ли winget. Возвращает версию или null. */
    public static String version() {
        try {
            List<String> lines = new ArrayList<>();
            ProcessResult result = run(List.of("winget", "--version"),
                    line -> lines.add(line), null);
            if (result.exitCode == 0 && !lines.isEmpty()) {
                return lines.get(0).trim();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Устанавливает пакет.
     *
     * @param onLine     обычные строки вывода (пишутся в журнал)
     * @param onProgress строки прогресса, обновляемые winget через {@code \r}
     * @return код возврата процесса (0 — успех, -1 — отменено пользователем)
     */
    public static int install(AppEntry app, boolean silent,
                              Consumer<String> onLine, Consumer<String> onProgress,
                              AtomicBoolean cancelled) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("winget");
        cmd.add("install");
        cmd.add("--id");
        cmd.add(app.getId());
        cmd.add("-e");
        cmd.add("--source");
        cmd.add(SOURCE);
        cmd.add("--accept-package-agreements");
        cmd.add("--accept-source-agreements");
        cmd.add("--disable-interactivity");
        if (silent) {
            cmd.add("--silent");
        }

        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        ProcessOutput.Sink sink = new ProcessOutput.Sink() {
            @Override
            public void line(String text) {
                onLine.accept(text);
            }

            @Override
            public void progress(String text) {
                if (onProgress != null) {
                    onProgress.accept(text);
                }
            }
        };

        Thread reader = new Thread(() -> {
            try {
                ProcessOutput.pump(process.getInputStream(), FALLBACK_CHARSET, sink);
            } catch (IOException ignored) {
                // процесс завершился — чтение прервано
            }
        }, "winget-output");
        reader.setDaemon(true);
        reader.start();

        while (process.isAlive()) {
            if (cancelled != null && cancelled.get()) {
                process.destroyForcibly();
                process.waitFor();
                reader.join(2000);
                return -1;
            }
            process.waitFor(200, TimeUnit.MILLISECONDS);
        }
        reader.join(2000);
        return process.exitValue();
    }

    private static ProcessResult run(List<String> cmd, Consumer<String> onLine,
                                     ProcessOutput.Sink extra) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        ProcessOutput.Sink sink = new ProcessOutput.Sink() {
            @Override
            public void line(String text) {
                onLine.accept(text);
            }

            @Override
            public void progress(String text) {
                if (extra != null) {
                    extra.progress(text);
                }
            }
        };

        Thread reader = new Thread(() -> {
            try {
                ProcessOutput.pump(process.getInputStream(), FALLBACK_CHARSET, sink);
            } catch (IOException ignored) {
                // чтение прервано
            }
        });
        reader.setDaemon(true);
        reader.start();

        int code = process.waitFor();
        reader.join(2000);
        return new ProcessResult(code);
    }

    private static final class ProcessResult {
        final int exitCode;

        ProcessResult(int exitCode) {
            this.exitCode = exitCode;
        }
    }
}
