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
package org.hyperledger.besu.ethereum.eth.transactions;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus.REJECTED_UNDERPRICED_REPLACEMENT;

import org.hyperledger.besu.ethereum.core.AccountTransactionOrder;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.util.Subscribers;
import org.hyperledger.besu.util.number.Percentage;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.EvictingQueue;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class PendingTransactions {

  private final int maxTransactionRetentionHours;
  private final Clock clock;

  private final EvictingQueue<Hash> newPooledHashes;
  private final Map<Hash, TransactionInfo> pendingTransactions = new ConcurrentHashMap<>();
  private final NavigableSet<TransactionInfo> prioritizedTransactionsStaticRange =
      new TreeSet<>(
          comparing(TransactionInfo::isReceivedFromLocalSource)
              .thenComparing(
                  transactionInfo ->
                      // safe to .get() here because it has to a 1559 tx
                      // non-1559 txs are always in the dynamic range
                      transactionInfo
                          .getTransaction()
                          .getMaxPriorityFeePerGas()
                          .get()
                          .getValue()
                          .longValue())
              .thenComparing(TransactionInfo::getSequence)
              .reversed());
  private final NavigableSet<TransactionInfo> prioritizedTransactionsDynamicRange =
      new TreeSet<>(
          comparing(TransactionInfo::isReceivedFromLocalSource)
              .thenComparing(
                  transactionInfo ->
                      transactionInfo
                          .getTransaction()
                          .getMaxFeePerGas()
                          .map(maxFeePerGas -> maxFeePerGas.getValue().longValue())
                          .orElse(transactionInfo.getTransaction().getGasPrice().toLong()))
              .thenComparing(TransactionInfo::getSequence)
              .reversed());
  private final ReentrantReadWriteLock collectionLock = new ReentrantReadWriteLock();
  // todo what if 1559 is enabled at genesis
  private Long baseFee;
  private final Map<Address, TransactionsForSenderInfo> transactionsBySender =
      new ConcurrentHashMap<>();

  private final Subscribers<PendingTransactionListener> pendingTransactionSubscribers =
      Subscribers.create();

  private final Subscribers<PendingTransactionDroppedListener> transactionDroppedListeners =
      Subscribers.create();

  private final LabelledMetric<Counter> transactionRemovedCounter;
  private final Counter localTransactionAddedCounter;
  private final Counter remoteTransactionAddedCounter;
  private final Counter localTransactionHashesAddedCounter;

  private final long maxPendingTransactions;
  private final TransactionPoolReplacementHandler transactionReplacementHandler;
  private final Supplier<BlockHeader> chainHeadHeaderSupplier;

  public PendingTransactions(
      final int maxTransactionRetentionHours,
      final int maxPendingTransactions,
      final int maxPooledTransactionHashes,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier,
      final Percentage priceBump) {
    this.maxTransactionRetentionHours = maxTransactionRetentionHours;
    this.maxPendingTransactions = maxPendingTransactions;
    this.clock = clock;
    this.newPooledHashes = EvictingQueue.create(maxPooledTransactionHashes);
    this.chainHeadHeaderSupplier = chainHeadHeaderSupplier;
    this.baseFee = chainHeadHeaderSupplier.get().getBaseFee().orElse(null);
    this.transactionReplacementHandler = new TransactionPoolReplacementHandler(priceBump);
    final LabelledMetric<Counter> transactionAddedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_added_total",
            "Count of transactions added to the transaction pool",
            "source");
    localTransactionAddedCounter = transactionAddedCounter.labels("local");
    remoteTransactionAddedCounter = transactionAddedCounter.labels("remote");
    localTransactionHashesAddedCounter = transactionAddedCounter.labels("pool");

    transactionRemovedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_removed_total",
            "Count of transactions removed from the transaction pool",
            "source",
            "operation");

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "transactions",
        "Current size of the transaction pool",
        pendingTransactions::size);
  }

  public void evictOldTransactions() {
    final Instant removeTransactionsBefore =
        clock.instant().minus(maxTransactionRetentionHours, ChronoUnit.HOURS);

    pendingTransactions.values().stream()
        .filter(transaction -> transaction.getAddedToPoolAt().isBefore(removeTransactionsBefore))
        .forEach(transaction -> removeTransaction(transaction.getTransaction()));
  }

  List<Transaction> getLocalTransactions() {
    return pendingTransactions.values().stream()
        .filter(TransactionInfo::isReceivedFromLocalSource)
        .map(TransactionInfo::getTransaction)
        .collect(Collectors.toList());
  }

  public boolean addRemoteTransaction(final Transaction transaction) {
    final TransactionInfo transactionInfo =
        new TransactionInfo(transaction, false, clock.instant());
    final TransactionAddedStatus transactionAddedStatus = addTransaction(transactionInfo);
    final boolean added = transactionAddedStatus.equals(ADDED);
    if (added) {
      remoteTransactionAddedCounter.inc();
    }
    return added;
  }

  boolean addTransactionHash(final Hash transactionHash) {
    final boolean hashAdded;
    synchronized (newPooledHashes) {
      hashAdded = newPooledHashes.add(transactionHash);
    }
    if (hashAdded) {
      localTransactionHashesAddedCounter.inc();
    }
    return hashAdded;
  }

  @VisibleForTesting
  public TransactionAddedStatus addLocalTransaction(final Transaction transaction) {
    final TransactionAddedStatus transactionAdded =
        addTransaction(new TransactionInfo(transaction, true, clock.instant()));
    if (transactionAdded.equals(ADDED)) {
      localTransactionAddedCounter.inc();
    }
    return transactionAdded;
  }

  void removeTransaction(final Transaction transaction) {
    doRemoveTransaction(transaction, false);
    notifyTransactionDropped(transaction);
  }

  void transactionAddedToBlock(final Transaction transaction) {
    doRemoveTransaction(transaction, true);
  }

  private void doRemoveTransaction(final Transaction transaction, final boolean addedToBlock) {
    final Lock lock = collectionLock.writeLock();
    try {
      final TransactionInfo removedTransactionInfo =
          pendingTransactions.remove(transaction.getHash());
      if (removedTransactionInfo != null) {
        checkState(
            prioritizedTransactionsStaticRange.remove(removedTransactionInfo)
                ^ prioritizedTransactionsDynamicRange.remove(removedTransactionInfo),
            "It shouldn't be possible that the transaction was remove from neither or both collections. It should only have been removed from one.");
        removeTransactionTrackedBySenderAndNonce(transaction);
        incrementTransactionRemovedCounter(
            removedTransactionInfo.isReceivedFromLocalSource(), addedToBlock);
      }
    } finally {
      lock.unlock();
    }
  }

  private void incrementTransactionRemovedCounter(
      final boolean receivedFromLocalSource, final boolean addedToBlock) {
    final String location = receivedFromLocalSource ? "local" : "remote";
    final String operation = addedToBlock ? "addedToBlock" : "dropped";
    transactionRemovedCounter.labels(location, operation).inc();
  }

  // todo make sure we only select valid transaction types. Usually they're blocked before they get
  // here but in the case of reorging back to a block lower than the upgrade block, we could have
  // let a future transaction type in that shouldn't be mined
  public void selectTransactions(final TransactionSelector selector) {
    final Lock lock = collectionLock.writeLock();
    try {
      final List<Transaction> transactionsToRemove = new ArrayList<>();
      final Map<Address, AccountTransactionOrder> accountTransactions = new HashMap<>();
      final Iterator<TransactionInfo> prioritizedTransactions = prioritizedTransactions();
      while (prioritizedTransactions.hasNext()) {
        final TransactionInfo highestPriorityTransactionInfo = prioritizedTransactions.next();
        final AccountTransactionOrder accountTransactionOrder =
            accountTransactions.computeIfAbsent(
                highestPriorityTransactionInfo.getSender(), this::createSenderTransactionOrder);

        for (final Transaction transactionToProcess :
            accountTransactionOrder.transactionsToProcess(
                highestPriorityTransactionInfo.getTransaction())) {
          final TransactionSelectionResult result =
              selector.evaluateTransaction(transactionToProcess);
          switch (result) {
            case DELETE_TRANSACTION_AND_CONTINUE:
              transactionsToRemove.add(transactionToProcess);
              break;
            case CONTINUE:
              break;
            case COMPLETE_OPERATION:
              transactionsToRemove.forEach(this::removeTransaction);
              return;
            default:
              throw new RuntimeException("Illegal value for TransactionSelectionResult.");
          }
        }
      }
      transactionsToRemove.forEach(this::removeTransaction);
    } finally {
      lock.unlock();
    }
  }

  private Iterator<TransactionInfo> prioritizedTransactions() {
    return new Iterator<>() {
      final Iterator<TransactionInfo> staticRangeIterable =
          prioritizedTransactionsStaticRange.iterator();
      final Iterator<TransactionInfo> dynamicRangeIterable =
          prioritizedTransactionsStaticRange.iterator();

      Optional<TransactionInfo> currentStaticRangeTransaction =
          getNextOptional(staticRangeIterable);
      Optional<TransactionInfo> currentDynamicRangeTransaction =
          getNextOptional(dynamicRangeIterable);

      @Override
      public boolean hasNext() {
        return staticRangeIterable.hasNext() || dynamicRangeIterable.hasNext();
      }

      @Override
      public TransactionInfo next() {
        if (currentStaticRangeTransaction.isEmpty() && currentDynamicRangeTransaction.isEmpty()) {
          throw new NoSuchElementException("Tried to iterate past end of iterator.");
        } else if (currentStaticRangeTransaction.isEmpty()) {
          // only dynamic range txs left
          final TransactionInfo best = currentDynamicRangeTransaction.get();
          currentDynamicRangeTransaction = getNextOptional(dynamicRangeIterable);
          return best;
        } else if (currentDynamicRangeTransaction.isEmpty()) {
          // only static range txs left
          final TransactionInfo best = currentStaticRangeTransaction.get();
          currentStaticRangeTransaction = getNextOptional(staticRangeIterable);
          return best;
        } else {
          // there are both static and dynamic txs remaining so we need to compare them by their
          // effective priority fees
          final long dynamicRangeEffectivePriorityFee =
              effectivePriorityFeePerGas(
                  currentDynamicRangeTransaction.get().getTransaction(), baseFee);
          final long staticRangeEffectivePriorityFee =
              effectivePriorityFeePerGas(
                  currentStaticRangeTransaction.get().getTransaction(), baseFee);
          final TransactionInfo best;
          if (dynamicRangeEffectivePriorityFee > staticRangeEffectivePriorityFee) {
            best = currentDynamicRangeTransaction.get();
            currentDynamicRangeTransaction = getNextOptional(dynamicRangeIterable);
          } else {
            best = currentStaticRangeTransaction.get();
            currentStaticRangeTransaction = getNextOptional(staticRangeIterable);
          }
          return best;
        }
      }

      private Optional<TransactionInfo> getNextOptional(
          final Iterator<TransactionInfo> transactionInfoIterator) {
        return transactionInfoIterator.hasNext()
            ? Optional.of(transactionInfoIterator.next())
            : Optional.empty();
      }
    };
  }

  private AccountTransactionOrder createSenderTransactionOrder(final Address address) {
    return new AccountTransactionOrder(
        transactionsBySender
            .get(address)
            .streamTransactionInfos()
            .map(TransactionInfo::getTransaction));
  }

  private TransactionAddedStatus addTransaction(final TransactionInfo transactionInfo) {
    Optional<Transaction> droppedTransaction = Optional.empty();
    final Transaction transaction = transactionInfo.getTransaction();
    final Lock lock = collectionLock.writeLock();
    try {
      if (pendingTransactions.containsKey(transactionInfo.getHash())) {
        return ALREADY_KNOWN;
      }

      final TransactionAddedStatus transactionAddedStatus =
          addTransactionForSenderAndNonce(transactionInfo);
      if (!transactionAddedStatus.equals(ADDED)) {
        return transactionAddedStatus;
      }
      // check if it's in static or dynamic range
      if (isInStaticRange(transaction, baseFee)) {
        prioritizedTransactionsStaticRange.add(transactionInfo);
      } else {
        prioritizedTransactionsDynamicRange.add(transactionInfo);
      }
      pendingTransactions.put(transactionInfo.getHash(), transactionInfo);
      tryEvictTransactionHash(transactionInfo.getHash());

      if (pendingTransactions.size() > maxPendingTransactions) {
        final TransactionInfo staticRemovalCandidate = prioritizedTransactionsStaticRange.last();
        final TransactionInfo dynamicRemovalCandidate = prioritizedTransactionsDynamicRange.last();
        final TransactionInfo toRemove =
            effectivePriorityFeePerGas(dynamicRemovalCandidate.getTransaction(), baseFee)
                    > effectivePriorityFeePerGas(staticRemovalCandidate.getTransaction(), baseFee)
                ? staticRemovalCandidate
                : dynamicRemovalCandidate;
        doRemoveTransaction(toRemove.getTransaction(), false);
        droppedTransaction = Optional.of(toRemove.getTransaction());
      }
    } finally {
      lock.unlock();
    }
    notifyTransactionAdded(transaction);
    droppedTransaction.ifPresent(this::notifyTransactionDropped);
    return ADDED;
  }

  private boolean isInStaticRange(final Transaction transaction, final long baseFee) {
    return transaction
        .getMaxPriorityFeePerGas()
        .map(
            maxPriorityFeePerGas ->
                effectivePriorityFeePerGas(transaction, baseFee)
                    >= maxPriorityFeePerGas.getValue().longValue())
        .orElse(
            // non-eip-1559 txs can't be in static range
            false);
  }

  private long effectivePriorityFeePerGas(final Transaction transaction, final long baseFee) {
    final long maybeNegativePriorityFeePerGas;
    if (transaction.getType().equals(TransactionType.EIP1559)) {
      maybeNegativePriorityFeePerGas =
          Math.min(
              transaction.getMaxPriorityFeePerGas().get().getValue().longValue(),
              transaction.getMaxFeePerGas().get().getValue().longValue() - baseFee);
    } else {
      maybeNegativePriorityFeePerGas = transaction.getGasPrice().getValue().longValue() - baseFee;
    }
    return maybeNegativePriorityFeePerGas;
  }

  public void updateBaseFee(final Long baseFee) {
    if (Objects.equal(this.baseFee, baseFee)) {
      return;
    }
    final Lock lock = collectionLock.writeLock();
    try {
      final boolean baseFeeIncreased = baseFee > this.baseFee;
      this.baseFee = baseFee;
      if (baseFeeIncreased) {
        // base fee increases can only cause transactions to go from static to dynamic range
        final List<TransactionInfo> transactionInfosToTransfer =
            prioritizedTransactionsStaticRange.stream()
                .filter(
                    // these are the transactions whose effective priority fee have now dropped
                    // below their max priority fee
                    transactionInfo -> !isInStaticRange(transactionInfo.getTransaction(), baseFee))
                .collect(toUnmodifiableList());
        transactionInfosToTransfer.forEach(
            transactionInfo -> {
              prioritizedTransactionsStaticRange.remove(transactionInfo);
              prioritizedTransactionsDynamicRange.add(transactionInfo);
            });
      } else {
        // base fee decreases can only cause transactions to go from dynamic to static range
        final List<TransactionInfo> transactionInfosToTransfer =
            prioritizedTransactionsDynamicRange.stream()
                .filter(
                    // these are the transactions whose effective priority fee are now above their
                    // max priority fee
                    transactionInfo -> isInStaticRange(transactionInfo.getTransaction(), baseFee))
                .collect(toUnmodifiableList());
        transactionInfosToTransfer.forEach(
            transactionInfo -> {
              prioritizedTransactionsDynamicRange.remove(transactionInfo);
              prioritizedTransactionsStaticRange.add(transactionInfo);
            });
      }
    } finally {
      lock.unlock();
    }
  }

  private TransactionAddedStatus addTransactionForSenderAndNonce(
      final TransactionInfo transactionInfo) {
    final TransactionInfo existingTransaction =
        getTrackedTransactionBySenderAndNonce(transactionInfo);
    if (existingTransaction != null) {
      // todo we should abstract replacement strategy. This is currently buggy because it doesn't
      // work for access list
      if (existingTransaction.transaction.getType().equals(TransactionType.FRONTIER)
          && !transactionReplacementHandler.shouldReplace(
              existingTransaction, transactionInfo, chainHeadHeaderSupplier.get())) {
        return REJECTED_UNDERPRICED_REPLACEMENT;
      }
      removeTransaction(existingTransaction.getTransaction());
    }
    trackTransactionBySenderAndNonce(transactionInfo);
    return ADDED;
  }

  private void trackTransactionBySenderAndNonce(final TransactionInfo transactionInfo) {
    final TransactionsForSenderInfo transactionsForSenderInfo =
        transactionsBySender.computeIfAbsent(
            transactionInfo.getSender(), key -> new TransactionsForSenderInfo());
    transactionsForSenderInfo.addTransactionToTrack(transactionInfo.getNonce(), transactionInfo);
  }

  private void removeTransactionTrackedBySenderAndNonce(final Transaction transaction) {
    Optional.ofNullable(transactionsBySender.get(transaction.getSender()))
        .ifPresent(
            transactionsForSender ->
                transactionsForSender.removeTrackedTransaction(transaction.getNonce()));
  }

  private TransactionInfo getTrackedTransactionBySenderAndNonce(
      final TransactionInfo transactionInfo) {
    final TransactionsForSenderInfo transactionsForSenderInfo =
        transactionsBySender.computeIfAbsent(
            transactionInfo.getSender(), key -> new TransactionsForSenderInfo());
    return transactionsForSenderInfo.getTransactionInfoForNonce(transactionInfo.getNonce());
  }

  private void notifyTransactionAdded(final Transaction transaction) {
    pendingTransactionSubscribers.forEach(listener -> listener.onTransactionAdded(transaction));
  }

  private void notifyTransactionDropped(final Transaction transaction) {
    transactionDroppedListeners.forEach(listener -> listener.onTransactionDropped(transaction));
  }

  public long maxSize() {
    return maxPendingTransactions;
  }

  public int size() {
    return pendingTransactions.size();
  }

  public boolean containsTransaction(final Hash transactionHash) {
    return pendingTransactions.containsKey(transactionHash);
  }

  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return Optional.ofNullable(pendingTransactions.get(transactionHash))
        .map(TransactionInfo::getTransaction);
  }

  public Set<TransactionInfo> getTransactionInfo() {
    return new HashSet<>(pendingTransactions.values());
  }

  long subscribePendingTransactions(final PendingTransactionListener listener) {
    return pendingTransactionSubscribers.subscribe(listener);
  }

  void unsubscribePendingTransactions(final long id) {
    pendingTransactionSubscribers.unsubscribe(id);
  }

  long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener) {
    return transactionDroppedListeners.subscribe(listener);
  }

  void unsubscribeDroppedTransactions(final long id) {
    transactionDroppedListeners.unsubscribe(id);
  }

  public OptionalLong getNextNonceForSender(final Address sender) {
    final TransactionsForSenderInfo transactionsForSenderInfo = transactionsBySender.get(sender);
    return transactionsForSenderInfo == null
        ? OptionalLong.empty()
        : transactionsForSenderInfo.maybeNextNonce();
  }

  public void tryEvictTransactionHash(final Hash hash) {
    synchronized (newPooledHashes) {
      newPooledHashes.remove(hash);
    }
  }

  List<Hash> getNewPooledHashes() {
    synchronized (newPooledHashes) {
      return List.copyOf(newPooledHashes);
    }
  }

  /**
   * Tracks the additional metadata associated with transactions to enable prioritization for mining
   * and deciding which transactions to drop when the transaction pool reaches its size limit.
   */
  public static class TransactionInfo {

    private static final AtomicLong TRANSACTIONS_ADDED = new AtomicLong();
    private final Transaction transaction;
    private final boolean receivedFromLocalSource;
    private final Instant addedToPoolAt;
    private final long sequence; // Allows prioritization based on order transactions are added

    public TransactionInfo(
        final Transaction transaction,
        final boolean receivedFromLocalSource,
        final Instant addedToPoolAt) {
      this.transaction = transaction;
      this.receivedFromLocalSource = receivedFromLocalSource;
      this.addedToPoolAt = addedToPoolAt;
      this.sequence = TRANSACTIONS_ADDED.getAndIncrement();
    }

    public Transaction getTransaction() {
      return transaction;
    }

    public Wei getGasPrice() {
      if (transaction.getGasPrice() == null) {
        return Wei.ZERO;
      }
      return transaction.getGasPrice();
    }

    public long getSequence() {
      return sequence;
    }

    public long getNonce() {
      return transaction.getNonce();
    }

    public Address getSender() {
      return transaction.getSender();
    }

    public boolean isReceivedFromLocalSource() {
      return receivedFromLocalSource;
    }

    public Hash getHash() {
      return transaction.getHash();
    }

    public Instant getAddedToPoolAt() {
      return addedToPoolAt;
    }
  }

  public enum TransactionSelectionResult {
    DELETE_TRANSACTION_AND_CONTINUE,
    CONTINUE,
    COMPLETE_OPERATION
  }

  @FunctionalInterface
  public interface TransactionSelector {

    TransactionSelectionResult evaluateTransaction(final Transaction transaction);
  }

  public enum TransactionAddedStatus {
    ALREADY_KNOWN(TransactionInvalidReason.TRANSACTION_ALREADY_KNOWN),
    REJECTED_UNDERPRICED_REPLACEMENT(TransactionInvalidReason.TRANSACTION_REPLACEMENT_UNDERPRICED),
    ADDED();

    private final Optional<TransactionInvalidReason> invalidReason;

    TransactionAddedStatus() {
      this.invalidReason = Optional.empty();
    }

    TransactionAddedStatus(final TransactionInvalidReason invalidReason) {
      this.invalidReason = Optional.of(invalidReason);
    }

    public Optional<TransactionInvalidReason> getInvalidReason() {
      return invalidReason;
    }
  }
}
