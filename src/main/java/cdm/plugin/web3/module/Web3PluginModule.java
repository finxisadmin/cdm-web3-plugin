package cdm.plugin.web3.module;

import cdm.plugin.api.blockchain.CdmBlockchainProvider;
import cdm.plugin.web3.bridge.*;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.util.Map;
import java.util.ServiceLoader;

/**
 * Guice module wiring the Web3 plugin into the CDM runtime.
 *
 * Config keys:
 *   ethereum.private.key       — signer private key (hex, no 0x prefix)
 *   ethereum.rpc.url           — default RPC endpoint
 *   rpc.url.{network}          — per-network RPC endpoint override
 *   registry.address           — default event registry contract
 *   registry.address.{network} — per-network registry address
 *   gas.price.gwei             — gas price in gwei (default: 20)
 *   gas.limit                  — gas limit (default: 300000)
 */
public class Web3PluginModule extends AbstractModule {

    private final Map<String, String> config;

    public Web3PluginModule() {
        this(Map.of());
    }

    public Web3PluginModule(Map<String, String> config) {
        this.config = config;
    }

    @Override
    protected void configure() {
        // Bind Rune-generated abstract functions to bridges:
        // bind(Web3RecordEvent.class).to(Web3RecordEventBridge.class);
        // bind(Web3ReadEvent.class).to(Web3ReadEventBridge.class);
        // bind(Web3CallContract.class).to(Web3CallContractBridge.class);
        // bind(Web3GetBalance.class).to(Web3GetBalanceBridge.class);

        bind(Web3RecordEventBridge.class).asEagerSingleton();
        bind(Web3ReadEventBridge.class).asEagerSingleton();
        bind(Web3CallContractBridge.class).asEagerSingleton();
        bind(Web3GetBalanceBridge.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    CdmBlockchainProvider provideBlockchain() {
        CdmBlockchainProvider provider =
            ServiceLoader.load(CdmBlockchainProvider.class)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                    "No CdmBlockchainProvider found on classpath."));
        provider.initialize(config);
        return provider;
    }
}
