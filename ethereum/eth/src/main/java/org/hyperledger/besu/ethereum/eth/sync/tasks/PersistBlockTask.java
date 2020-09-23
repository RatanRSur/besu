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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractEthTask;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.infrastructure.async.SafeFuture;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PersistBlockTask extends AbstractEthTask<Block> {

  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final Block block;
  private final HeaderValidationMode validateHeaders;
  private boolean blockImported;

  private PersistBlockTask(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.block = block;
    this.validateHeaders = headerValidationMode;
    blockImported = false;
  }

  public static PersistBlockTask create(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final MetricsSystem metricsSystem) {
    return new PersistBlockTask(
        protocolSchedule, protocolContext, ethContext, block, headerValidationMode, metricsSystem);
  }

  public static Supplier<SafeFuture<List<Block>>> forSequentialBlocks(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final List<Block> blocks,
      final HeaderValidationMode headerValidationMode,
      final MetricsSystem metricsSystem) {
    checkArgument(!blocks.isEmpty(), "No blocks to import provided");
    return () -> {
      final List<Block> successfulImports = new ArrayList<>();
      final Iterator<Block> blockIterator = blocks.iterator();
      SafeFuture<Block> future =
          importBlockAndAddToList(
              protocolSchedule,
              protocolContext,
              ethContext,
              blockIterator.next(),
              successfulImports,
              headerValidationMode,
              metricsSystem);
      while (blockIterator.hasNext()) {
        final Block block = blockIterator.next();
        future =
            future.thenCompose(
                b ->
                    importBlockAndAddToList(
                        protocolSchedule,
                        protocolContext,
                        ethContext,
                        block,
                        successfulImports,
                        headerValidationMode,
                        metricsSystem));
      }
      return future.thenApply(r -> successfulImports);
    };
  }

  private static SafeFuture<Block> importBlockAndAddToList(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final Block block,
      final List<Block> list,
      final HeaderValidationMode headerValidationMode,
      final MetricsSystem metricsSystem) {
    return PersistBlockTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            block,
            headerValidationMode,
            metricsSystem)
        .run()
        .whenComplete(
            (r, t) -> {
              if (r != null) {
                list.add(r);
              }
            });
  }

  public static Supplier<SafeFuture<List<Block>>> forUnorderedBlocks(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final List<Block> blocks,
      final HeaderValidationMode headerValidationMode,
      final MetricsSystem metricsSystem) {
    checkArgument(!blocks.isEmpty(), "No blocks to import provided");
    return () -> {
      final SafeFuture<List<Block>> finalResult = new SafeFuture<>();
      final List<Block> successfulImports = new ArrayList<>();
      final Iterator<PersistBlockTask> tasks =
          blocks.stream()
              .map(
                  block ->
                      PersistBlockTask.create(
                          protocolSchedule,
                          protocolContext,
                          ethContext,
                          block,
                          headerValidationMode,
                          metricsSystem))
              .iterator();

      SafeFuture<Block> future = tasks.next().run();
      while (tasks.hasNext()) {
        final PersistBlockTask task = tasks.next();
        future =
            future
                .handle((r, t) -> r)
                .thenCompose(
                    r -> {
                      if (r != null) {
                        successfulImports.add(r);
                      }
                      return task.run();
                    });
      }
      future.whenComplete(
          (r, t) -> {
            if (r != null) {
              successfulImports.add(r);
            }
            if (successfulImports.size() > 0) {
              finalResult.complete(successfulImports);
            } else {
              finalResult.completeExceptionally(t);
            }
          });

      return finalResult;
    };
  }

  @Override
  protected void executeTask() {
    try {
      final ProtocolSpec protocolSpec =
          protocolSchedule.getByBlockNumber(block.getHeader().getNumber());
      final BlockImporter blockImporter = protocolSpec.getBlockImporter();
      blockImported = blockImporter.importBlock(protocolContext, block, validateHeaders);
      if (!blockImported) {
        result.completeExceptionally(
            new InvalidBlockException(
                "Failed to import block", block.getHeader().getNumber(), block.getHash()));
        return;
      }
      result.complete(block);
    } catch (final Exception e) {
      result.completeExceptionally(e);
    }
  }

  @Override
  protected void cleanup() {
    if (blockImported) {
      final double timeInS = getTaskTimeInSec();
      LOG.info(
          String.format(
              "Imported #%,d / %d tx / %d om / %,d (%01.1f%%) gas / (%s) in %01.3fs. Peers: %d",
              block.getHeader().getNumber(),
              block.getBody().getTransactions().size(),
              block.getBody().getOmmers().size(),
              block.getHeader().getGasUsed(),
              (block.getHeader().getGasUsed() * 100.0) / block.getHeader().getGasLimit(),
              block.getHash().toHexString(),
              timeInS,
              ethContext.getEthPeers().peerCount()));
    }
  }
}
