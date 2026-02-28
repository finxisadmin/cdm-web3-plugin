package cdm.plugin.web3.impl;

import cdm.plugin.api.PluginResult;
import cdm.plugin.api.blockchain.CdmBlockchainProvider;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SPI implementation of CdmBlockchainProvider using Web3j.
 *
 * Supports multiple networks via a connection pool. Each network
 * gets its own Web3j instance, created lazily on first use.
 *
 * Key capabilities:
 *   - Record CDM event hashes on-chain via a registry contract
 *   - Read back event records by transaction hash
 *   - Call/send to arbitrary smart contracts
 *   - Query native token balances
 *
 * The registry contract is expected to have the interface:
 *   function recordHash(bytes32 eventHash) returns (bool)
 *   event HashRecorded(bytes32 indexed eventHash, address sender)
 */
public class Web3CdmProvider implements CdmBlockchainProvider {

    private static final Logger LOG =
        Logger.getLogger(Web3CdmProvider.class.getName());

    /** Default RPC endpoints per network. */
    private static final Map<String, String> DEFAULT_RPC = Map.of(
        "ethereum",  "https://eth.llamarpc.com",
        "sepolia",   "https://rpc.sepolia.org",
        "polygon",   "https://polygon-rpc.com",
        "arbitrum",  "https://arb1.arbitrum.io/rpc",
        "optimism",  "https://mainnet.optimism.io",
        "avalanche", "https://api.avax.network/ext/bc/C/rpc",
        "base",      "https://mainnet.base.org",
        "localhost", "http://localhost:8545"
    );

    private static final Map<String, Long> CHAIN_IDS = Map.of(
        "ethereum", 1L,
        "sepolia", 11155111L,
        "polygon", 137L,
        "arbitrum", 42161L,
        "optimism", 10L,
        "avalanche", 43114L,
        "base", 8453L,
        "localhost", 31337L
    );

    private Map<String, String> config;
    private Credentials credentials;
    private final ConcurrentHashMap<String, Web3j> connections =
        new ConcurrentHashMap<>();
    private boolean initialized = false;

    // Default gas settings (can be overridden via config)
    private BigInteger gasPrice = BigInteger.valueOf(20_000_000_000L); // 20 gwei
    private BigInteger gasLimit = BigInteger.valueOf(300_000L);

    // --- CdmPlugin lifecycle ---

    @Override
    public String getId()      { return "cdm-web3"; }

    @Override
    public String getName()    { return "CDM Web3 (Ethereum)"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public void initialize(Map<String, String> config) {
        this.config = new HashMap<>(config);

        // Load signer credentials
        String privateKey = config.get("ethereum.private.key");
        if (privateKey != null && !privateKey.isEmpty()) {
            this.credentials = Credentials.create(privateKey);
            LOG.info(() -> "Web3 signer configured: "
                + credentials.getAddress());
        } else {
            LOG.info("No private key configured — "
                + "read-only mode (call only, no send)");
        }

        // Gas settings
        String gasPriceStr = config.get("gas.price.gwei");
        if (gasPriceStr != null) {
            gasPrice = Convert.toWei(gasPriceStr, Convert.Unit.GWEI)
                .toBigInteger();
        }
        String gasLimitStr = config.get("gas.limit");
        if (gasLimitStr != null) {
            gasLimit = new BigInteger(gasLimitStr);
        }

        this.initialized = true;
        LOG.info("Web3 provider initialized");
    }

    @Override
    public void shutdown() {
        connections.values().forEach(Web3j::shutdown);
        connections.clear();
        initialized = false;
        LOG.info("Web3 provider shut down");
    }

    // --- CdmBlockchainProvider operations ---

    @Override
    public PluginResult<EventRecord> recordEvent(
            String eventJson, String networkId,
            String registryAddress) {
        ensureInitialized();
        ensureSigner();

        try {
            Web3j web3 = getConnection(networkId);
            long chainId = getChainId(networkId);

            // 1. Compute Keccak-256 hash of the CDM event
            byte[] eventBytes = eventJson.getBytes(StandardCharsets.UTF_8);
            String eventHash = Hash.sha3String(
                Numeric.toHexStringNoPrefix(eventBytes));

            // 2. Encode the recordHash(bytes32) function call
            byte[] hashBytes = Numeric.hexStringToByteArray(
                eventHash.startsWith("0x")
                    ? eventHash.substring(2) : eventHash);
            // Pad to 32 bytes
            byte[] padded = new byte[32];
            System.arraycopy(hashBytes, 0, padded,
                32 - Math.min(hashBytes.length, 32),
                Math.min(hashBytes.length, 32));

            Function function = new Function(
                "recordHash",
                List.of(new Bytes32(padded)),
                List.of(new TypeReference<Bool>() {}));

            String encodedFunction = FunctionEncoder.encode(function);

            // 3. Send transaction
            String contractAddr = registryAddress != null
                ? registryAddress
                : config.getOrDefault("registry.address." + networkId,
                    config.get("registry.address"));

            if (contractAddr == null || contractAddr.isEmpty()) {
                return PluginResult.fail(
                    "No registry contract address configured for "
                    + networkId);
            }

            RawTransactionManager txManager =
                new RawTransactionManager(web3, credentials, chainId);

            EthSendTransaction ethSendTx = txManager.sendTransaction(
                gasPrice, gasLimit,
                contractAddr, encodedFunction, BigInteger.ZERO);

            if (ethSendTx.hasError()) {
                return PluginResult.fail(
                    "Transaction failed: "
                    + ethSendTx.getError().getMessage());
            }

            String txHash = ethSendTx.getTransactionHash();

            // 4. Wait for receipt
            TransactionReceipt receipt = waitForReceipt(web3, txHash);

            EventRecord record = new EventRecord(
                txHash,
                eventHash,
                receipt.getBlockNumber().longValue(),
                System.currentTimeMillis() / 1000,
                networkId,
                contractAddr
            );

            return PluginResult.ok(record, Map.of(
                "txHash", txHash,
                "eventHash", eventHash,
                "blockNumber",
                    receipt.getBlockNumber().toString(),
                "gasUsed",
                    receipt.getGasUsed().toString()
            ));

        } catch (Exception e) {
            LOG.log(Level.WARNING, "recordEvent failed", e);
            return PluginResult.fail(
                "recordEvent failed: " + e.getMessage());
        }
    }

    @Override
    public PluginResult<EventRecord> readEvent(
            String txHash, String networkId) {
        ensureInitialized();

        try {
            Web3j web3 = getConnection(networkId);

            EthTransaction ethTx = web3.ethGetTransactionByHash(txHash)
                .send();

            if (ethTx.getTransaction().isEmpty()) {
                return PluginResult.fail(
                    "Transaction not found: " + txHash);
            }

            org.web3j.protocol.core.methods.response.Transaction tx =
                ethTx.getTransaction().get();

            // Extract event hash from input data
            // recordHash(bytes32) has 4-byte selector + 32-byte arg
            String inputData = tx.getInput();
            String eventHash = "0x";
            if (inputData != null && inputData.length() >= 74) {
                // Skip 0x (2) + function selector (8) = 10 chars
                eventHash = "0x" + inputData.substring(10, 74);
            }

            // Get the block for timestamp
            EthBlock ethBlock = web3.ethGetBlockByNumber(
                org.web3j.protocol.core.DefaultBlockParameter
                    .valueOf(tx.getBlockNumber()),
                false).send();

            long timestamp = ethBlock.getBlock()
                .getTimestamp().longValue();

            EventRecord record = new EventRecord(
                txHash,
                eventHash,
                tx.getBlockNumber().longValue(),
                timestamp,
                networkId,
                tx.getTo()
            );

            return PluginResult.ok(record);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "readEvent failed", e);
            return PluginResult.fail(
                "readEvent failed: " + e.getMessage());
        }
    }

    @Override
    public PluginResult<String> callContract(
            String contractAddress, String functionName,
            List<String> params, String networkId) {
        ensureInitialized();

        try {
            Web3j web3 = getConnection(networkId);

            // Build function with Utf8String params
            List<Type> inputParams = params.stream()
                .map(p -> (Type) new Utf8String(p))
                .toList();

            Function function = new Function(
                functionName,
                inputParams,
                List.of(new TypeReference<Utf8String>() {}));

            String encodedFunction = FunctionEncoder.encode(function);

            // eth_call (read-only, no gas)
            String fromAddress = credentials != null
                ? credentials.getAddress()
                : "0x0000000000000000000000000000000000000000";

            org.web3j.protocol.core.methods.request.Transaction callTx =
                org.web3j.protocol.core.methods.request.Transaction
                    .createEthCallTransaction(
                        fromAddress, contractAddress, encodedFunction);

            EthCall ethCall = web3.ethCall(
                callTx, DefaultBlockParameterName.LATEST).send();

            if (ethCall.hasError()) {
                return PluginResult.fail(
                    "Contract call failed: "
                    + ethCall.getError().getMessage());
            }

            // Decode the return value
            List<Type> results = FunctionReturnDecoder.decode(
                ethCall.getValue(), function.getOutputParameters());

            String resultStr = results.isEmpty()
                ? ethCall.getValue()
                : results.get(0).getValue().toString();

            return PluginResult.ok(resultStr, Map.of(
                "contractAddress", contractAddress,
                "function", functionName
            ));

        } catch (Exception e) {
            LOG.log(Level.WARNING, "callContract failed", e);
            return PluginResult.fail(
                "callContract failed: " + e.getMessage());
        }
    }

    @Override
    public PluginResult<TxReceipt> sendTransaction(
            String contractAddress, String functionName,
            List<String> params, String networkId) {
        ensureInitialized();
        ensureSigner();

        try {
            Web3j web3 = getConnection(networkId);
            long chainId = getChainId(networkId);

            List<Type> inputParams = params.stream()
                .map(p -> (Type) new Utf8String(p))
                .toList();

            Function function = new Function(
                functionName, inputParams, List.of());

            String encodedFunction = FunctionEncoder.encode(function);

            RawTransactionManager txManager =
                new RawTransactionManager(web3, credentials, chainId);

            EthSendTransaction ethSendTx = txManager.sendTransaction(
                gasPrice, gasLimit,
                contractAddress, encodedFunction, BigInteger.ZERO);

            if (ethSendTx.hasError()) {
                return PluginResult.fail(
                    "Send failed: "
                    + ethSendTx.getError().getMessage());
            }

            String txHash = ethSendTx.getTransactionHash();
            TransactionReceipt receipt = waitForReceipt(web3, txHash);

            TxReceipt txReceipt = new TxReceipt(
                txHash,
                receipt.isStatusOK() ? "Confirmed" : "Failed",
                receipt.getBlockNumber().longValue(),
                receipt.getBlockHash(),
                receipt.getGasUsed().longValue(),
                0L, // effectiveGasPrice — EIP-1559 specific
                receipt.getContractAddress(),
                receipt.isStatusOK() ? null : "Transaction reverted"
            );

            return PluginResult.ok(txReceipt, Map.of(
                "txHash", txHash,
                "blockNumber", receipt.getBlockNumber().toString()
            ));

        } catch (Exception e) {
            LOG.log(Level.WARNING, "sendTransaction failed", e);
            return PluginResult.fail(
                "sendTransaction failed: " + e.getMessage());
        }
    }

    @Override
    public PluginResult<BalanceResult> getBalance(
            String address, String networkId) {
        ensureInitialized();

        try {
            Web3j web3 = getConnection(networkId);

            EthGetBalance ethBalance = web3.ethGetBalance(
                address, DefaultBlockParameterName.LATEST).send();

            if (ethBalance.hasError()) {
                return PluginResult.fail(
                    "getBalance failed: "
                    + ethBalance.getError().getMessage());
            }

            BigInteger balanceWei = ethBalance.getBalance();
            BigDecimal balanceEther = Convert.fromWei(
                new BigDecimal(balanceWei), Convert.Unit.ETHER);

            BalanceResult result = new BalanceResult(
                address, balanceWei, balanceEther, networkId);

            return PluginResult.ok(result, Map.of(
                "address", address,
                "balanceWei", balanceWei.toString(),
                "balanceEther", balanceEther.toPlainString()
            ));

        } catch (Exception e) {
            LOG.log(Level.WARNING, "getBalance failed", e);
            return PluginResult.fail(
                "getBalance failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Connection management
    // ---------------------------------------------------------------

    private Web3j getConnection(String networkId) {
        return connections.computeIfAbsent(
            networkId.toLowerCase(), this::createConnection);
    }

    private Web3j createConnection(String networkId) {
        // Check for custom RPC URL in config
        String rpcUrl = config.get("rpc.url." + networkId);
        if (rpcUrl == null) {
            rpcUrl = config.get("ethereum.rpc.url");
        }
        if (rpcUrl == null) {
            rpcUrl = DEFAULT_RPC.getOrDefault(networkId,
                "http://localhost:8545");
        }

        LOG.info(() -> "Connecting to " + networkId + " via " + rpcUrl);
        return Web3j.build(new HttpService(rpcUrl));
    }

    private long getChainId(String networkId) {
        String configChainId = config.get("chain.id." + networkId);
        if (configChainId != null) {
            return Long.parseLong(configChainId);
        }
        return CHAIN_IDS.getOrDefault(
            networkId.toLowerCase(), 1L);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private TransactionReceipt waitForReceipt(Web3j web3, String txHash)
            throws Exception {
        int maxAttempts = Integer.parseInt(
            config.getOrDefault("receipt.poll.attempts", "40"));
        long pollInterval = Long.parseLong(
            config.getOrDefault("receipt.poll.interval.ms", "3000"));

        for (int i = 0; i < maxAttempts; i++) {
            EthGetTransactionReceipt ethReceipt =
                web3.ethGetTransactionReceipt(txHash).send();

            if (ethReceipt.getTransactionReceipt().isPresent()) {
                return ethReceipt.getTransactionReceipt().get();
            }

            Thread.sleep(pollInterval);
        }

        throw new RuntimeException(
            "Transaction receipt not available after "
            + maxAttempts + " attempts: " + txHash);
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                "Web3 provider not initialized. Call initialize() first.");
        }
    }

    private void ensureSigner() {
        if (credentials == null) {
            throw new IllegalStateException(
                "No private key configured. "
                + "Set 'ethereum.private.key' in config for "
                + "state-changing transactions.");
        }
    }
}
