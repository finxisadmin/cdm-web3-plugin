package cdm.plugin.web3.bridge;

import cdm.plugin.api.PluginResult;
import cdm.plugin.api.blockchain.CdmBlockchainProvider;
import cdm.plugin.api.blockchain.CdmBlockchainProvider.TxReceipt;
import cdm.plugin.web3.type.Web3ContractCall;
import cdm.plugin.web3.type.Web3ContractResult;
import cdm.plugin.web3.type.Web3TransactionReceipt;
import cdm.plugin.web3.enums.Web3CallTypeEnum;
import cdm.plugin.web3.enums.Web3TransactionStatusEnum;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Bridge for Web3CallContract Rune function.
 * Routes to callContract (read-only) or sendTransaction
 * (state-changing) based on the Web3CallTypeEnum.
 */
public class Web3CallContractBridge /* extends Web3CallContract */ {

    private final CdmBlockchainProvider provider;

    public Web3CallContractBridge() {
        this.provider = ServiceLoader.load(CdmBlockchainProvider.class)
            .findFirst().orElseThrow(() -> new RuntimeException(
                "No CdmBlockchainProvider found on classpath."));
    }

    public Web3CallContractBridge(CdmBlockchainProvider provider) {
        this.provider = provider;
    }

    public Web3ContractResult evaluate(Web3ContractCall web3ContractCall) {

        String contractAddress = web3ContractCall.getWeb3ContractAddress();
        String functionName = web3ContractCall.getWeb3FunctionName();
        List<String> params = web3ContractCall.getWeb3FunctionParams();
        if (params == null) params = List.of();

        String networkId = web3ContractCall.getWeb3Network()
            .getWeb3Network().name().toLowerCase();

        Web3ContractResult.Web3ContractResultBuilder builder =
            Web3ContractResult.builder();

        if (web3ContractCall.getWeb3CallType() == Web3CallTypeEnum.CALL) {
            // Read-only call
            PluginResult<String> result = provider.callContract(
                contractAddress, functionName, params, networkId);

            builder.setWeb3Success(result.success());
            if (result.success()) {
                builder.setWeb3ResultData(result.data());
            } else {
                builder.setWeb3Error(result.error());
            }

        } else {
            // State-changing send
            PluginResult<TxReceipt> result = provider.sendTransaction(
                contractAddress, functionName, params, networkId);

            builder.setWeb3Success(result.success());
            if (result.success() && result.data() != null) {
                TxReceipt receipt = result.data();
                Web3TransactionReceipt txReceipt =
                    Web3TransactionReceipt.builder()
                        .setWeb3TxHash(receipt.txHash())
                        .setWeb3TxStatus(
                            "Confirmed".equals(receipt.status())
                                ? Web3TransactionStatusEnum.CONFIRMED
                                : Web3TransactionStatusEnum.FAILED)
                        .setWeb3BlockNumber(
                            (int) receipt.blockNumber())
                        .setWeb3BlockHash(receipt.blockHash())
                        .setWeb3GasUsed((int) receipt.gasUsed())
                        .setWeb3ContractAddress(
                            receipt.contractAddress())
                        .setWeb3Error(receipt.error())
                        .build();
                builder.setWeb3TxReceipt(txReceipt);
            } else {
                builder.setWeb3Error(result.error());
            }
        }

        return builder.build();
    }
}
