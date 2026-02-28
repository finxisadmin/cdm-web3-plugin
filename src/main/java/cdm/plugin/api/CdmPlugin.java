package cdm.plugin.api;

import java.util.Map;

/**
 * Base interface for all CDM plugins.
 * Every plugin must implement this for lifecycle management.
 */
public interface CdmPlugin {

    /** Unique identifier, e.g. "cdm-https-client" */
    String getId();

    /** Human-readable name, e.g. "CDM HTTPS Client" */
    String getName();

    /** Semantic version, e.g. "1.0.0" */
    String getVersion();

    /**
     * Initialize the plugin with configuration.
     * Called once before any operations.
     *
     * @param config key-value configuration map
     */
    void initialize(Map<String, String> config);

    /**
     * Shut down the plugin and release resources.
     */
    void shutdown();
}
