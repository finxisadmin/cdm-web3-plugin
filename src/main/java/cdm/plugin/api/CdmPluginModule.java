package cdm.plugin.api;

import com.google.inject.Module;

/**
 * SPI (Service Provider Interface) for CDM plugin Guice modules.
 *
 * Every CDM plugin that needs Guice bindings implements this interface
 * and registers it via META-INF/services. The CdmPluginRegistry
 * discovers implementations using Java's ServiceLoader at runtime.
 *
 * This interface exists so that plugins can wire themselves into the
 * CDM Guice injector without any changes to CDM source code, the
 * Rune code generator, or the application's main class.
 *
 * <h3>Plugin author usage:</h3>
 * <pre>
 * public class HttpsPluginModuleSpi implements CdmPluginModule {
 *
 *     public String getId()      { return "cdm-plugin-https"; }
 *     public String getName()    { return "CDM HTTPS Plugin"; }
 *     public String getVersion() { return "1.0.0"; }
 *
 *     public Module getModule() {
 *         return new HttpsPluginModule();
 *     }
 * }
 * </pre>
 *
 * Registered in META-INF/services/cdm.plugin.api.CdmPluginModule:
 * <pre>
 * cdm.plugin.https.module.HttpsPluginModuleSpi
 * </pre>
 *
 * @see CdmPluginRegistry
 */
public interface CdmPluginModule {

    /** Unique identifier, e.g. "cdm-plugin-https" */
    String getId();

    /** Human-readable name, e.g. "CDM HTTPS Plugin" */
    String getName();

    /** Semantic version, e.g. "1.0.0" */
    String getVersion();

    /**
     * Return the Guice module that binds this plugin's Rune-generated
     * abstract function classes to their bridge implementations.
     *
     * This module will be installed alongside CdmRuntimeModule when
     * the CdmPluginRegistry builds the injector.
     *
     * @return a Guice Module (never null)
     */
    Module getModule();
}
