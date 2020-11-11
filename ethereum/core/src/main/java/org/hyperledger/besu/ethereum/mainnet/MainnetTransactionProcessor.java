/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.fees.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.core.transaction.FrontierTransaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.vm.operations.ReturnStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class MainnetTransactionProcessor {

  private static final Logger LOG = LogManager.getLogger();

  private final GasCalculator gasCalculator;

  private final TransactionValidator transactionValidator;

  private final AbstractMessageProcessor contractCreationProcessor;

  private final AbstractMessageProcessor messageCallProcessor;

  private final int maxStackSize;

  private final int createContractAccountVersion;

  private final TransactionPriceCalculator transactionPriceCalculator;
  private final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator;

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @param transactionValidationParams Validation parameters that will be used by the {@link
   *     TransactionValidator}
   * @return the transaction result
   * @see TransactionValidator
   * @see TransactionValidationParams
   */
  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        OperationTracer.NO_TRACING,
        blockHashLookup,
        isPersistingPrivateState,
        transactionValidationParams);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @param transactionValidationParams Validation parameters that will be used by the {@link
   *     TransactionValidator}
   * @param operationTracer operation tracer {@link OperationTracer}
   * @return the transaction result
   * @see TransactionValidator
   * @see TransactionValidationParams
   */
  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingPrivateState,
        transactionValidationParams);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param operationTracer The tracer to record results of each EVM operation
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @return the transaction result
   */
  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingPrivateState,
        new TransactionValidationParams.Builder().build());
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param operationTracer The tracer to record results of each EVM operation
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @param transactionValidationParams The transaction validation parameters to use
   * @return the transaction result
   */
  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingPrivateState,
        transactionValidationParams,
        null);
  }

  private final boolean clearEmptyAccounts;

  public MainnetTransactionProcessor(
      final GasCalculator gasCalculator,
      final TransactionValidator transactionValidator,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final int maxStackSize,
      final int createContractAccountVersion,
      final TransactionPriceCalculator transactionPriceCalculator,
      final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator) {
    this.gasCalculator = gasCalculator;
    this.transactionValidator = transactionValidator;
    this.contractCreationProcessor = contractCreationProcessor;
    this.messageCallProcessor = messageCallProcessor;
    this.clearEmptyAccounts = clearEmptyAccounts;
    this.maxStackSize = maxStackSize;
    this.createContractAccountVersion = createContractAccountVersion;
    this.transactionPriceCalculator = transactionPriceCalculator;
    this.coinbaseFeePriceCalculator = coinbaseFeePriceCalculator;
  }

  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final FrontierTransaction frontierTransaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams,
      final PrivateMetadataUpdater privateMetadataUpdater) {
    try {
      LOG.trace("Starting execution of {}", frontierTransaction);

      ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult =
          transactionValidator.validate(frontierTransaction, blockHeader.getBaseFee());
      // Make sure the transaction is intrinsically valid before trying to
      // compare against a sender account (because the transaction may not
      // be signed correctly to extract the sender).
      if (!validationResult.isValid()) {
        LOG.warn("Invalid transaction: {}", validationResult.getErrorMessage());
        return TransactionProcessingResult.invalid(validationResult);
      }

      final Address senderAddress = frontierTransaction.getSender();
      final EvmAccount sender = worldState.getOrCreate(senderAddress);
      validationResult =
          transactionValidator.validateForSender(
              frontierTransaction, sender, transactionValidationParams);
      if (!validationResult.isValid()) {
        LOG.debug("Invalid transaction: {}", validationResult.getErrorMessage());
        return TransactionProcessingResult.invalid(validationResult);
      }

      final MutableAccount senderMutableAccount = sender.getMutable();
      final long previousNonce = senderMutableAccount.incrementNonce();
      final Wei transactionGasPrice =
          transactionPriceCalculator.price(frontierTransaction, blockHeader.getBaseFee());
      LOG.trace(
          "Incremented sender {} nonce ({} -> {})",
          senderAddress,
          previousNonce,
          sender.getNonce());

      final Wei upfrontGasCost = frontierTransaction.getUpfrontGasCost(transactionGasPrice);
      final Wei previousBalance = senderMutableAccount.decrementBalance(upfrontGasCost);
      LOG.trace(
          "Deducted sender {} upfront gas cost {} ({} -> {})",
          senderAddress,
          upfrontGasCost,
          previousBalance,
          sender.getBalance());

      final Gas intrinsicGas = gasCalculator.transactionIntrinsicGasCost(frontierTransaction);
      final Gas gasAvailable = Gas.of(frontierTransaction.getGasLimit()).minus(intrinsicGas);
      LOG.trace(
          "Gas available for execution {} = {} - {} (limit - intrinsic)",
          gasAvailable,
          frontierTransaction.getGasLimit(),
          intrinsicGas);

      final WorldUpdater worldUpdater = worldState.updater();
      final MessageFrame initialFrame;
      final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();
      final ReturnStack returnStack = new ReturnStack();

      if (frontierTransaction.isContractCreation()) {
        final Address contractAddress =
            Address.contractAddress(senderAddress, sender.getNonce() - 1L);

        initialFrame =
            MessageFrame.builder()
                .type(MessageFrame.Type.CONTRACT_CREATION)
                .messageFrameStack(messageFrameStack)
                .returnStack(returnStack)
                .blockchain(blockchain)
                .worldState(worldUpdater.updater())
                .initialGas(gasAvailable)
                .address(contractAddress)
                .originator(senderAddress)
                .contract(contractAddress)
                .contractAccountVersion(createContractAccountVersion)
                .gasPrice(transactionGasPrice)
                .inputData(Bytes.EMPTY)
                .sender(senderAddress)
                .value(frontierTransaction.getValue())
                .apparentValue(frontierTransaction.getValue())
                .code(new Code(frontierTransaction.getPayload()))
                .blockHeader(blockHeader)
                .depth(0)
                .completer(c -> {})
                .miningBeneficiary(miningBeneficiary)
                .blockHashLookup(blockHashLookup)
                .isPersistingPrivateState(isPersistingPrivateState)
                .maxStackSize(maxStackSize)
                .transactionHash(frontierTransaction.getHash())
                .privateMetadataUpdater(privateMetadataUpdater)
                .build();

      } else {
        final Address to = frontierTransaction.getTo().get();
        final Account contract = worldState.get(to);

        initialFrame =
            MessageFrame.builder()
                .type(MessageFrame.Type.MESSAGE_CALL)
                .messageFrameStack(messageFrameStack)
                .returnStack(returnStack)
                .blockchain(blockchain)
                .worldState(worldUpdater.updater())
                .initialGas(gasAvailable)
                .address(to)
                .originator(senderAddress)
                .contract(to)
                .contractAccountVersion(
                    contract != null ? contract.getVersion() : Account.DEFAULT_VERSION)
                .gasPrice(transactionGasPrice)
                .inputData(frontierTransaction.getPayload())
                .sender(senderAddress)
                .value(frontierTransaction.getValue())
                .apparentValue(frontierTransaction.getValue())
                .code(new Code(contract != null ? contract.getCode() : Bytes.EMPTY))
                .blockHeader(blockHeader)
                .depth(0)
                .completer(c -> {})
                .miningBeneficiary(miningBeneficiary)
                .blockHashLookup(blockHashLookup)
                .maxStackSize(maxStackSize)
                .isPersistingPrivateState(isPersistingPrivateState)
                .transactionHash(frontierTransaction.getHash())
                .privateMetadataUpdater(privateMetadataUpdater)
                .build();
      }

      messageFrameStack.addFirst(initialFrame);

      while (!messageFrameStack.isEmpty()) {
        process(messageFrameStack.peekFirst(), operationTracer);
      }

      if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        worldUpdater.commit();
      }

      if (LOG.isTraceEnabled()) {
        LOG.trace(
            "Gas used by transaction: {}, by message call/contract creation: {}",
            () -> Gas.of(frontierTransaction.getGasLimit()).minus(initialFrame.getRemainingGas()),
            () -> gasAvailable.minus(initialFrame.getRemainingGas()));
      }

      // Refund the sender by what we should and pay the miner fee (note that we're doing them one
      // after the other so that if it is the same account somehow, we end up with the right result)
      final Gas selfDestructRefund =
          gasCalculator.getSelfDestructRefundAmount().times(initialFrame.getSelfDestructs().size());
      final Gas refundGas = initialFrame.getGasRefund().plus(selfDestructRefund);
      final Gas refunded = refunded(frontierTransaction, initialFrame.getRemainingGas(), refundGas);
      final Wei refundedWei = refunded.priceFor(transactionGasPrice);
      senderMutableAccount.incrementBalance(refundedWei);

      final Gas gasUsedByTransaction =
          Gas.of(frontierTransaction.getGasLimit()).minus(initialFrame.getRemainingGas());

      final MutableAccount coinbase = worldState.getOrCreate(miningBeneficiary).getMutable();
      final Gas coinbaseFee = Gas.of(frontierTransaction.getGasLimit()).minus(refunded);
      if (blockHeader.getBaseFee().isPresent() && frontierTransaction.isEIP1559Transaction()) {
        final Wei baseFee = Wei.of(blockHeader.getBaseFee().get());
        if (transactionGasPrice.compareTo(baseFee) < 0) {
          return TransactionProcessingResult.failed(
              gasUsedByTransaction.toLong(),
              refunded.toLong(),
              ValidationResult.invalid(
                  TransactionValidator.TransactionInvalidReason.TRANSACTION_PRICE_TOO_LOW,
                  "transaction price must be greater than base fee"),
              Optional.empty());
        }
      }
      final CoinbaseFeePriceCalculator coinbaseCreditService =
          frontierTransaction.isFrontierTransaction()
              ? CoinbaseFeePriceCalculator.frontier()
              : coinbaseFeePriceCalculator;
      final Wei coinbaseWeiDelta =
          coinbaseCreditService.price(coinbaseFee, transactionGasPrice, blockHeader.getBaseFee());

      coinbase.incrementBalance(coinbaseWeiDelta);

      initialFrame.getSelfDestructs().forEach(worldState::deleteAccount);

      if (clearEmptyAccounts) {
        clearEmptyAccounts(worldState);
      }

      if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        return TransactionProcessingResult.successful(
            initialFrame.getLogs(),
            gasUsedByTransaction.toLong(),
            refunded.toLong(),
            initialFrame.getOutputData(),
            validationResult);
      } else {
        return TransactionProcessingResult.failed(
            gasUsedByTransaction.toLong(),
            refunded.toLong(),
            validationResult,
            initialFrame.getRevertReason());
      }
    } catch (final RuntimeException re) {
      LOG.error("Critical Exception Processing Transaction", re);
      return TransactionProcessingResult.invalid(
          ValidationResult.invalid(
              TransactionInvalidReason.INTERNAL_ERROR,
              "Internal Error in Besu - " + re.toString()));
    }
  }

  private static void clearEmptyAccounts(final WorldUpdater worldState) {
    new ArrayList<>(worldState.getTouchedAccounts())
        .stream().filter(Account::isEmpty).forEach(a -> worldState.deleteAccount(a.getAddress()));
  }

  private void process(final MessageFrame frame, final OperationTracer operationTracer) {
    final AbstractMessageProcessor executor = getMessageProcessor(frame.getType());

    executor.process(frame, operationTracer);
  }

  private AbstractMessageProcessor getMessageProcessor(final MessageFrame.Type type) {
    switch (type) {
      case MESSAGE_CALL:
        return messageCallProcessor;
      case CONTRACT_CREATION:
        return contractCreationProcessor;
      default:
        throw new IllegalStateException("Request for unsupported message processor type " + type);
    }
  }

  private static Gas refunded(
      final Transaction transaction, final Gas gasRemaining, final Gas gasRefund) {
    // Integer truncation takes care of the the floor calculation needed after the divide.
    final Gas maxRefundAllowance =
        Gas.of(transaction.getGasLimit()).minus(gasRemaining).dividedBy(2);
    final Gas refundAllowance = maxRefundAllowance.min(gasRefund);
    return gasRemaining.plus(refundAllowance);
  }
}
