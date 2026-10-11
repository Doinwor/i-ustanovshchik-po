package installer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный парсер/сериализатор JSON без внешних зависимостей.
 * Поддерживает объекты, массивы, строки, числа, true/false/null.
 */
final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (p.pos < p.src.length()) {
            throw new IllegalArgumentException("Лишние данные в JSON на позиции " + p.pos);
        }
        return value;
    }

    private Object readValue() {
        char c = peek();
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            if (peek() != ':') {
                throw new IllegalArgumentException("Ожидалось ':' на позиции " + pos);
            }
            pos++;
            skipWs();
            map.put(key, readValue());
            skipWs();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Ожидалось ',' или '}' на позиции " + (pos - 1));
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(readValue());
            skipWs();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Ожидалось ',' или ']' на позиции " + (pos - 1));
            }
        }
    }

    private String readString() {
        char quote = next();
        if (quote != '"') {
            throw new IllegalArgumentException("Ожидалась '\"' на позиции " + (pos - 1));
        }
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        String hex = src.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                        break;
                    default:
                        throw new IllegalArgumentException("Неизвестная escape-последовательность \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < src.length() && "+-.eE0123456789".indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
        String num = src.substring(start, pos);
        if (num.isEmpty()) {
            throw new IllegalArgumentException("Неожиданный символ на позиции " + start);
        }
        if (num.contains(".") || num.contains("e") || num.contains("E")) {
            return Double.parseDouble(num);
        }
        return Long.parseLong(num);
    }

    private void expect(String literal) {
        if (!src.startsWith(literal, pos)) {
            throw new IllegalArgumentException("Ожидалось '" + literal + "' на позиции " + pos);
        }
        pos += literal.length();
    }

    private char peek() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("Неожиданный конец JSON");
        }
        return src.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
