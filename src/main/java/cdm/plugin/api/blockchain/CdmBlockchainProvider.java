package cdm.plugin.api.blockchain;

import cdm.plugin.api.CdmPlugin;
import cdm.plugin.api.PluginResult;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * SPI for blockchain / DLT integration (Ethereum, etc.)
 *
 * Plugin authors implement this interface to provide on-chain
 * event recording, smart contract interaction, and blockchain
 * state queries. Bridges convert CDM types to/from SPI calls.
 *
 * All blockchain-specific details (signing, gas estimation, ABI
 * encoding) are handled by the implementation. Callers work
 * with plain strings and the SPI abstracts the chain.
 */
public interface CdmBlockchainProvider extends CdmPlugin {

    /**
     * Record a CDM event hash on the blockchain.
     * The implementation should:
     *   1. Compute Keccak-256 hash of the event JSON
     *   2. Submit a transaction to the registry contract
     *   3. Wait for confirmation
     *   4. Return the event record with tx details
     *
     * @param eventJson        CDM event serialized as JSON
     * @param networkId        network identifier (e.g. "ethereum", "sepolia")
     * @param registryAddress  optional registry contract address
     * @return event record with tx hash, block number, event hash
     */
    PluginResult<EventRecord> recordEvent(
        String eventJson, String networkId, String registryAddress);

    /**
     * Read a previously recorded event from the blockchain.
     *
     * @param txHash    transaction hash of the recording
     * @param networkId network identifier
     * @return event record details
     */
    PluginResult<EventRecord> readEvent(
        String txHash, String networkId);

    /**
     * Call a read-only smart contract function (no gas, no signing).
     *
     * @param contractAddress deployed contract address
     * @param functionName    Solidity function name
     * @param params          ABI-encoded parameters as strings
     * @param networkId       network identifier
     * @return decoded return value as string
     */
    PluginResult<String> callContract(
        String contractAddress, String functionName,
        List<String> params, String networkId);

    /**
     * Send a state-changing transaction to a smart contract.
     * Requires a configured signer (private key or wallet).
     *
     * @param contractAddress deployed contract address
     * @param functionName    Solidity function name
     * @param params          ABI-encoded parameters as strings
     * @param networkId       network identifier
     * @return transaction receipt with hash, block, gas used
     */
    PluginResult<TxReceipt> sendTransaction(
        String contractAddress, String functionName,
        List<String> params, String networkId);

    /**
     * Get the native token balance of an address.
     *
     * @param address   wallet address (0x-prefixed)
     * @param networkId network identifier
     * @return balance in wei (as BigInteger) and ether (as BigDecimal)
     */
    PluginResult<BalanceResult> getBalance(
        String address, String networkId);

    /**
     * An event hash recorded on-chain.
     */
    record EventRecord(
        String txHash,
        String eventHash,
        long blockNumber,
        long timestamp,
        String networkId,
        String contractAddress
    ) {}

    /**
     * A transaction receipt.
     */
    record TxReceipt(
        String txHash,
        String status,
        long blockNumber,
        String blockHash,
        long gasUsed,
        long effectiveGasPrice,
        String contractAddress,
        String error
    ) {}

    /**
     * Native token balance result.
     */
    record BalanceResult(
        String address,
        BigInteger balanceWei,
        BigDecimal balanceEther,
        String networkId
    ) {}
}
