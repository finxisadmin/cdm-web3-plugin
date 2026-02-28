package cdm.plugin.web3;

import cdm.plugin.api.CdmPluginModule;
import cdm.plugin.api.PluginResult;
import cdm.plugin.api.blockchain.CdmBlockchainProvider;
import cdm.plugin.api.blockchain.CdmBlockchainProvider.BalanceResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CDM Web3 Plugin.
 *
 * SPI wiring tests run without a blockchain node.
 * Integration tests require a running Ethereum node (Ganache,
 * Hardhat, or a testnet). Enable by setting system property
 * -Dweb3.rpc.url=http://localhost:8545
 */
class Web3PluginTest {

    private CdmBlockchainProvider provider;

    @BeforeEach
    void setup() {
        provider = ServiceLoader.load(CdmBlockchainProvider.class)
            .findFirst().orElse(null);
    }

    // -------------------------------------------------------
    // SPI Discovery
    // -------------------------------------------------------

    @Test
    @DisplayName("ServiceLoader discovers Web3CdmProvider")
    void testProviderDiscovery() {
        assertNotNull(provider,
            "ServiceLoader should discover Web3CdmProvider");
        assertEquals("cdm-web3", provider.getId());
        assertEquals("CDM Web3 (Ethereum)", provider.getName());
        assertEquals("1.0.0", provider.getVersion());
    }

    @Test
    @DisplayName("ServiceLoader discovers Web3PluginModuleSpi")
    void testPluginModuleSpiDiscovery() {
        var module = ServiceLoader.load(CdmPluginModule.class)
            .findFirst();
        assertTrue(module.isPresent());
        assertEquals("cdm-plugin-web3", module.get().getId());
        assertEquals("CDM Web3 (Ethereum)", module.get().getName());
        assertNotNull(module.get().getModule());
    }

    // -------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------

    @Test
    @DisplayName("Provider throws before initialization")
    void testNotInitialized() {
        assertThrows(IllegalStateException.class, () ->
            provider.getBalance(
                "0x0000000000000000000000000000000000000000",
                "localhost"));
    }

    @Test
    @DisplayName("Initializes in read-only mode without private key")
    void testReadOnlyInit() {
        assertDoesNotThrow(() ->
            provider.initialize(Map.of()));
        provider.shutdown();
    }

    @Test
    @DisplayName("State-changing operations require a private key")
    void testSignerRequired() {
        provider.initialize(Map.of()); // no private key

        assertThrows(IllegalStateException.class, () ->
            provider.recordEvent(
                "{\"test\": true}", "localhost", null));

        assertThrows(IllegalStateException.class, () ->
            provider.sendTransaction(
                "0xabcd", "transfer", List.of(), "localhost"));

        provider.shutdown();
    }

    @Test
    @DisplayName("Initializes with custom RPC and private key")
    void testFullInit() {
        // Use a well-known test private key (Hardhat default #0)
        assertDoesNotThrow(() ->
            provider.initialize(Map.of(
                "ethereum.rpc.url", "http://localhost:8545",
                "ethereum.private.key",
                    "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
                "gas.price.gwei", "1",
                "gas.limit", "500000"
            )));
        provider.shutdown();
    }

    // -------------------------------------------------------
    // Integration tests (requires running node)
    // -------------------------------------------------------

    // Enable with: mvn test -Dweb3.rpc.url=http://localhost:8545
    // Requires Hardhat or Ganache running locally.

    /*
    @Test
    @DisplayName("Get balance from local node")
    void testGetBalance() {
        String rpcUrl = System.getProperty(
            "web3.rpc.url", "http://localhost:8545");

        provider.initialize(Map.of(
            "ethereum.rpc.url", rpcUrl
        ));

        // Hardhat default account #0
        PluginResult<BalanceResult> result = provider.getBalance(
            "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
            "localhost");

        assertTrue(result.success(),
            "getBalance should succeed: " + result.error());
        assertNotNull(result.data());
        assertTrue(result.data().balanceWei().signum() > 0,
            "Default Hardhat account should have balance");

        System.out.printf("Balance: %s ETH%n",
            result.data().balanceEther().toPlainString());

        provider.shutdown();
    }

    @Test
    @DisplayName("Record and read CDM event on local chain")
    void testRecordAndReadEvent() {
        String rpcUrl = System.getProperty(
            "web3.rpc.url", "http://localhost:8545");

        provider.initialize(Map.of(
            "ethereum.rpc.url", rpcUrl,
            "ethereum.private.key",
                "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
            "registry.address", System.getProperty(
                "web3.registry.address", "")
        ));

        String cdmEvent = "{\"eventType\":\"Execution\","
            + "\"trade\":{\"tradeId\":\"T-001\"}}";

        // Record
        var recordResult = provider.recordEvent(
            cdmEvent, "localhost",
            System.getProperty("web3.registry.address"));

        assertTrue(recordResult.success(),
            "recordEvent should succeed: " + recordResult.error());
        assertNotNull(recordResult.data().txHash());
        assertNotNull(recordResult.data().eventHash());

        // Read back
        var readResult = provider.readEvent(
            recordResult.data().txHash(), "localhost");

        assertTrue(readResult.success());
        assertEquals(recordResult.data().eventHash(),
            readResult.data().eventHash());

        provider.shutdown();
    }

    @Test
    @DisplayName("Call read-only contract function")
    void testCallContract() {
        String rpcUrl = System.getProperty(
            "web3.rpc.url", "http://localhost:8545");

        provider.initialize(Map.of(
            "ethereum.rpc.url", rpcUrl
        ));

        // Call a simple getter on a deployed contract
        var result = provider.callContract(
            System.getProperty("web3.test.contract"),
            "getEventCount",
            List.of(),
            "localhost");

        assertTrue(result.success(),
            "callContract should succeed: " + result.error());

        provider.shutdown();
    }
    */
}
