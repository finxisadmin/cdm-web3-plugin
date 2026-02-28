package cdm.plugin.web3.bridge;

import cdm.plugin.api.PluginResult;
import cdm.plugin.api.blockchain.CdmBlockchainProvider;
import cdm.plugin.api.blockchain.CdmBlockchainProvider.EventRecord;
import cdm.plugin.web3.type.Web3NetworkConfig;
import cdm.plugin.web3.type.Web3EventRecord;
import cdm.plugin.web3.enums.Web3NetworkEnum;

import java.util.ServiceLoader;

/**
 * Bridge for Web3ReadEvent Rune function.
 */
public class Web3ReadEventBridge /* extends Web3ReadEvent */ {

    private final CdmBlockchainProvider provider;

    public Web3ReadEventBridge() {
        this.provider = ServiceLoader.load(CdmBlockchainProvider.class)
            .findFirst().orElseThrow(() -> new RuntimeException(
                "No CdmBlockchainProvider found on classpath."));
    }

    public Web3ReadEventBridge(CdmBlockchainProvider provider) {
        this.provider = provider;
    }

    public Web3EventRecord evaluate(
            String web3TxHash,
            Web3NetworkConfig web3Network) {

        String networkId = web3Network.getWeb3Network().name()
            .toLowerCase();

        PluginResult<EventRecord> result = provider.readEvent(
            web3TxHash, networkId);

        if (!result.success()) {
            throw new RuntimeException(
                "Web3ReadEvent failed: " + result.error());
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
