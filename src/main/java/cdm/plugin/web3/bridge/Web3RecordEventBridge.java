package cdm.plugin.web3.bridge;

import cdm.plugin.api.PluginResult;
import cdm.plugin.api.blockchain.CdmBlockchainProvider;
import cdm.plugin.api.blockchain.CdmBlockchainProvider.EventRecord;
import cdm.plugin.web3.type.Web3NetworkConfig;
import cdm.plugin.web3.type.Web3EventRecord;
import cdm.plugin.web3.enums.Web3NetworkEnum;

import java.util.ServiceLoader;

/**
 * Bridge connecting Rune-generated abstract Web3RecordEvent
 * to the SPI-discovered CdmBlockchainProvider.
 */
public class Web3RecordEventBridge /* extends Web3RecordEvent */ {

    private final CdmBlockchainProvider provider;

    public Web3RecordEventBridge() {
        this.provider = ServiceLoader.load(CdmBlockchainProvider.class)
            .findFirst()
            .orElseThrow(() -> new RuntimeException(
                "No CdmBlockchainProvider found on classpath."));
    }

    public Web3RecordEventBridge(CdmBlockchainProvider provider) {
        this.provider = provider;
    }

    public Web3EventRecord evaluate(
            String web3EventJson,
            Web3NetworkConfig web3Network,
            String web3RegistryAddress) {

        String networkId = web3Network.getWeb3Network().name()
            .toLowerCase();

        PluginResult<EventRecord> result = provider.recordEvent(
            web3EventJson, networkId, web3RegistryAddress);

        if (!result.success()) {
            throw new RuntimeException(
                "Web3RecordEvent failed: " + result.error());
        }

        EventRecord record = result.data();
        return Web3EventRecord.builder()
            .setWeb3TxHash(record.txHash())
            .setWeb3EventHash(record.eventHash())
            .setWeb3BlockNumber((int) record.blockNumber())
            .setWeb3Timestamp((int) record.timestamp())
            .setWeb3Network(web3Network.getWeb3Network())
            .setWeb3ContractAddress(record.contractAddress())
            .build();
    }
}
