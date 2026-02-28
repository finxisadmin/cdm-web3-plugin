package cdm.plugin.web3.module;

import cdm.plugin.api.CdmPluginModule;
import com.google.inject.Module;

import java.util.Map;

/**
 * SPI for CdmPluginRegistry auto-discovery.
 *
 * IMPORTANT: The private key should NEVER be set as a system
 * property. Use environment variables for production deployments.
 */
public class Web3PluginModuleSpi implements CdmPluginModule {

    @Override
    public String getId()      { return "cdm-plugin-web3"; }

    @Override
    public String getName()    { return "CDM Web3 (Ethereum)"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public Module getModule() {
        Map<String, String> config = Map.ofEntries(
            entry("ethereum.rpc.url",
                envOrProp("cdm.plugin.web3.rpc.url",
                    "http://localhost:8545")),
            entry("ethereum.private.key",
                envOnly("CDM_PLUGIN_WEB3_PRIVATE_KEY", "")),
            entry("registry.address",
                envOrProp("cdm.plugin.web3.registry.address", "")),
            entry("gas.price.gwei",
                envOrProp("cdm.plugin.web3.gas.price.gwei", "20")),
            entry("gas.limit",
                envOrProp("cdm.plugin.web3.gas.limit", "300000"))
        );
        return new Web3PluginModule(config);
    }

    /** Environment variable only — never system property (for secrets). */
    private static String envOnly(String envKey, String defaultValue) {
        String val = System.getenv(envKey);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    private static String envOrProp(String key, String defaultValue) {
        String envKey = key.replace('.', '_').toUpperCase();
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isEmpty()) return envVal;
        return System.getProperty(key, defaultValue);
    }

    private static Map.Entry<String, String> entry(String k, String v) {
        return Map.entry(k, v);
    }
}
