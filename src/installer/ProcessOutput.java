package installer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Разбор потока вывода winget.
 * <p>
 * winget обновляет строку прогресса через {@code \r} (символ возврата каретки), а не {@code \n}.
 * Если читать такой поток построчно, каждый тик «крутящейся палки» становится отдельной строкой
 * и журнал превращается в мусор. Здесь {@code \r} означает «перезапись текущей строки»,
 * поэтому такие фрагменты идут отдельным потоком (progress) и в журнал не пишутся.
 */
final class ProcessOutput {

    /** Получатель разобранного вывода. */
    interface Sink {
        /** Обычная строка, завершённая {@code \n}. */
        void line(String text);

        /** Строка, которую winget перезаписывает через {@code \r} (прогресс). */
        void progress(String text);
    }

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B[@-Z\\\\-_]");

    /** Символы, из которых состоит «крутилка» и полоса прогресса. */
    private static final String SPINNER_CHARS =
            "\\|/-=#></][()%.,:;0123456789"
                    + "█▓▒░▏▎▍▌▋▊▉■□▪▫→←·• \t\u00A0";
    private static final int MIN_PROGRESS_LENGTH = 3;

    private ProcessOutput() {
    }

    static void pump(InputStream in, Charset fallbackCharset, Sink sink) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int pending = -1;
        while (true) {
            int b = pending >= 0 ? pending : in.read();
            pending = -1;
            if (b == -1) {
                break;
            }
            if (b == '\n') {
                emitLine(buffer, fallbackCharset, sink);
            } else if (b == '\r') {
                int next = in.read();
                if (next == '\n') {
                    emitLine(buffer, fallbackCharset, sink);
                } else {
                    emitProgress(buffer, fallbackCharset, sink);
                    pending = next;
                }
            } else {
                buffer.write(b);
            }
        }
        emitLine(buffer, fallbackCharset, sink);
    }

    private static void emitLine(ByteArrayOutputStream buffer, Charset charset, Sink sink) {
        String text = clean(decode(buffer.toByteArray(), charset));
        buffer.reset();
        if (!text.isEmpty()) {
            sink.line(text);
        }
    }

    private static void emitProgress(ByteArrayOutputStream buffer, Charset charset, Sink sink) {
        String text = clean(decode(buffer.toByteArray(), charset));
        buffer.reset();
        if (text.length() < MIN_PROGRESS_LENGTH || isSpinner(text)) {
            return;
        }
        sink.progress(text);
    }

    /** Убирает ANSI-последовательности и управляющие символы. */
    static String clean(String raw) {
        String text = ANSI.matcher(raw).replaceAll("");
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\t' || c >= 0x20) {
                sb.append(c);
            }
        }
        return sb.toString().strip();
    }

    /** true, если строка состоит только из символов прогресса (например, "-", "\\", "|"). */
    private static boolean isSpinner(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (SPINNER_CHARS.indexOf(text.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Декодирует байты: сначала как UTF-8, при ошибке — в кодировке консоли.
     * winget может печатать и в UTF-8, и в кодовой странице OEM.
     */
    static String decode(byte[] bytes, Charset fallback) {
        if (bytes.length == 0) {
            return "";
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, fallback);
        }
    }
}
