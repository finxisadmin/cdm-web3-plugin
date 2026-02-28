package cdm.plugin.api;

import java.util.Map;

/**
 * Standard result wrapper for all CDM plugin operations.
 * Carries success/failure state, data, error message, and metadata.
 */
public class PluginResult<T> {

    private final boolean success;
    private final T data;
    private final String error;
    private final Map<String, String> metadata;

    private PluginResult(boolean success, T data, String error, Map<String, String> metadata) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.metadata = metadata;
    }

    public static <T> PluginResult<T> ok(T data) {
        return new PluginResult<>(true, data, null, Map.of());
    }

    public static <T> PluginResult<T> ok(T data, Map<String, String> metadata) {
        return new PluginResult<>(true, data, null, metadata);
    }

    public static <T> PluginResult<T> fail(String error) {
        return new PluginResult<>(false, null, error, Map.of());
    }

    public boolean success()              { return success; }
    public T data()                       { return data; }
    public String error()                 { return error; }
    public Map<String, String> metadata() { return metadata; }

    @Override
    public String toString() {
        if (success) {
            return "PluginResult[OK, data=" + data + ", metadata=" + metadata + "]";
        } else {
            return "PluginResult[FAIL, error=" + error + "]";
        }
    }
}
