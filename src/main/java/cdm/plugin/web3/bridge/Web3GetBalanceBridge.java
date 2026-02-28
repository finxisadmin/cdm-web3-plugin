package cdm.plugin.web3.bridge;

import cdm.plugin.api.PluginResult;
import cdm.plugin.api.blockchain.CdmBlockchainProvider;
import cdm.plugin.api.blockchain.CdmBlockchainProvider.BalanceResult;
import cdm.plugin.web3.type.Web3NetworkConfig;
import cdm.plugin.web3.type.Web3Balance;

import java.math.BigDecimal;
import java.util.ServiceLoader;

/**
 * Bridge for Web3GetBalance Rune function.
 */
public class Web3GetBalanceBridge /* extends Web3GetBalance */ {

    private final CdmBlockchainProvider provider;

    public Web3GetBalanceBridge() {
        this.provider = ServiceLoader.load(CdmBlockchainProvider.class)
            .findFirst().orElseThrow(() -> new RuntimeException(
                "No CdmBlockchainProvider found on classpath."));
    }

    public Web3GetBalanceBridge(CdmBlockchainProvider provider) {
        this.provider = provider;
    }

    public Web3Balance evaluate(
            String web3Address,
            Web3NetworkConfig web3Network) {

        String networkId = web3Network.getWeb3Network().name()
            .toLowerCase();

        PluginResult<BalanceResult> result = provider.getBalance(
            web3Address, networkId);

        if (!result.success()) {
            throw new RuntimeException(
                "Web3GetBalance failed: " + result.error());
        }

        BalanceResult bal = result.data();
        return Web3Balance.builder()
            .setWeb3Address(bal.address())
            .setWeb3BalanceWei(bal.balanceWei().toString())
            .setWeb3BalanceEther(bal.balanceEther())
            .setWeb3Network(web3Network.getWeb3Network())
            .build();
    }
}
