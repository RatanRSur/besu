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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.deserializer.HexStringDeserializer;
import org.hyperledger.besu.ethereum.core.deserializer.QuantityToLongDeserializer;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

/** A mined Ethereum block header. */
public class BlockHeader extends SealableBlockHeader
    implements org.hyperledger.besu.plugin.data.BlockHeader {

  public static final int MAX_EXTRA_DATA_BYTES = 32;

  public static final long GENESIS_BLOCK_NUMBER = 0L;

  private final Hash mixHash;

  private final long nonce;

  @JsonIgnore private final Supplier<Hash> hash;

  private final Supplier<ParsedExtraData> parsedExtraData;

  @VisibleForTesting
  @JsonCreator
  // todo see if this works without the @JsonProperty's
  public BlockHeader(
      @JsonProperty("parentHash") final Hash parentHash,
      @JsonProperty("ommersHash") final Hash ommersHash,
      @JsonProperty("coinbase") final Address coinbase,
      @JsonProperty("stateRoot") final Hash stateRoot,
      @JsonProperty("transactionsRoot") final Hash transactionsRoot,
      @JsonProperty("receiptsRoot") final Hash receiptsRoot,
      @JsonProperty("logsBloom") final LogsBloomFilter logsBloom,
      @JsonProperty("difficulty") final Difficulty difficulty,
      @JsonDeserialize(using = QuantityToLongDeserializer.class) @JsonProperty("number")
          final long number,
      @JsonDeserialize(using = QuantityToLongDeserializer.class) @JsonProperty("gasLimit")
          final long gasLimit,
      @JsonDeserialize(using = QuantityToLongDeserializer.class) @JsonProperty("gasUsed")
          final long gasUsed,
      @JsonDeserialize(using = QuantityToLongDeserializer.class) @JsonProperty("timestamp")
          final long timestamp,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("extraData")
          final Bytes extraData,
      @JsonDeserialize(using = QuantityToLongDeserializer.class) @JsonProperty("baseFee")
          final Long baseFee,
      @JsonProperty("mixHash") final Hash mixHash,
      @JsonDeserialize(using = QuantityToLongDeserializer.class) @JsonProperty("nonce")
          final long nonce) {
    this(
        parentHash,
        ommersHash,
        coinbase,
        stateRoot,
        transactionsRoot,
        receiptsRoot,
        logsBloom,
        difficulty,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFee,
        mixHash,
        nonce,
        new MainnetBlockHeaderFunctions());
  }

  public BlockHeader(
      final Hash parentHash,
      final Hash ommersHash,
      final Address coinbase,
      final Hash stateRoot,
      final Hash transactionsRoot,
      final Hash receiptsRoot,
      final LogsBloomFilter logsBloom,
      final Difficulty difficulty,
      final long number,
      final long gasLimit,
      final long gasUsed,
      final long timestamp,
      final Bytes extraData,
      final Long baseFee,
      final Hash mixHash,
      final long nonce,
      final BlockHeaderFunctions blockHeaderFunctions) {
    super(
        parentHash,
        ommersHash,
        coinbase,
        stateRoot,
        transactionsRoot,
        receiptsRoot,
        logsBloom,
        difficulty,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFee);
    this.mixHash = mixHash;
    this.nonce = nonce;
    this.hash = Suppliers.memoize(() -> blockHeaderFunctions.hash(this));
    this.parsedExtraData = Suppliers.memoize(() -> blockHeaderFunctions.parseExtraData(this));
  }

  /**
   * Returns the block mixed hash.
   *
   * @return the block mixed hash
   */
  @Override
  public Hash getMixHash() {
    return mixHash;
  }

  /**
   * Returns the block nonce.
   *
   * @return the block nonce
   */
  @Override
  public long getNonce() {
    return nonce;
  }

  /**
   * Returns the block extra data field, as parsed by the {@link BlockHeaderFunctions}.
   *
   * @return the block extra data field
   */
  public ParsedExtraData getParsedExtraData() {
    return parsedExtraData.get();
  }

  /**
   * Returns the block header hash.
   *
   * @return the block header hash
   */
  public Hash getHash() {
    return hash.get();
  }

  @Override
  public org.hyperledger.besu.plugin.data.Hash getBlockHash() {
    return hash.get();
  }

  /**
   * Write an RLP representation.
   *
   * @param out The RLP output to write to
   */
  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeBytes(parentHash);
    out.writeBytes(ommersHash);
    out.writeBytes(coinbase);
    out.writeBytes(stateRoot);
    out.writeBytes(transactionsRoot);
    out.writeBytes(receiptsRoot);
    out.writeBytes(logsBloom);
    out.writeUInt256Scalar(difficulty);
    out.writeLongScalar(number);
    out.writeLongScalar(gasLimit);
    out.writeLongScalar(gasUsed);
    out.writeLongScalar(timestamp);
    out.writeBytes(extraData);
    out.writeBytes(mixHash);
    out.writeLong(nonce);
    if (ExperimentalEIPs.eip1559Enabled && baseFee != null) {
      out.writeLongScalar(baseFee);
    }
    out.endList();
  }

  public static BlockHeader readFrom(
      final RLPInput input, final BlockHeaderFunctions blockHeaderFunctions) {
    input.enterList();
    final Hash parentHash = Hash.wrap(input.readBytes32());
    final Hash ommersHash = Hash.wrap(input.readBytes32());
    final Address coinbase = Address.readFrom(input);
    final Hash stateRoot = Hash.wrap(input.readBytes32());
    final Hash transactionsRoot = Hash.wrap(input.readBytes32());
    final Hash receiptsRoot = Hash.wrap(input.readBytes32());
    final LogsBloomFilter logsBloom = LogsBloomFilter.readFrom(input);
    final Difficulty difficulty = Difficulty.of(input.readUInt256Scalar());
    final long number = input.readLongScalar();
    final long gasLimit = input.readLongScalar();
    final long gasUsed = input.readLongScalar();
    final long timestamp = input.readLongScalar();
    final Bytes extraData = input.readBytes();
    final Hash mixHash = Hash.wrap(input.readBytes32());
    final long nonce = input.readLong();
    final Long baseFee =
        ExperimentalEIPs.eip1559Enabled && !input.isEndOfCurrentList()
            ? input.readLongScalar()
            : null;
    input.leaveList();
    return new BlockHeader(
        parentHash,
        ommersHash,
        coinbase,
        stateRoot,
        transactionsRoot,
        receiptsRoot,
        logsBloom,
        difficulty,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFee,
        mixHash,
        nonce,
        blockHeaderFunctions);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof BlockHeader)) {
      return false;
    }
    final BlockHeader other = (BlockHeader) obj;
    return getHash().equals(other.getHash());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getHash());
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("BlockHeader{");
    sb.append("hash=").append(getHash()).append(", ");
    sb.append("parentHash=").append(parentHash).append(", ");
    sb.append("ommersHash=").append(ommersHash).append(", ");
    sb.append("coinbase=").append(coinbase).append(", ");
    sb.append("stateRoot=").append(stateRoot).append(", ");
    sb.append("transactionsRoot=").append(transactionsRoot).append(", ");
    sb.append("receiptsRoot=").append(receiptsRoot).append(", ");
    sb.append("logsBloom=").append(logsBloom).append(", ");
    sb.append("difficulty=").append(difficulty).append(", ");
    sb.append("number=").append(number).append(", ");
    sb.append("gasLimit=").append(gasLimit).append(", ");
    sb.append("gasUsed=").append(gasUsed).append(", ");
    sb.append("timestamp=").append(timestamp).append(", ");
    sb.append("extraData=").append(extraData).append(", ");
    sb.append("baseFee=").append(baseFee).append(", ");
    sb.append("mixHash=").append(mixHash).append(", ");
    sb.append("nonce=").append(nonce);
    return sb.append("}").toString();
  }

  public static org.hyperledger.besu.ethereum.core.BlockHeader convertPluginBlockHeader(
      final org.hyperledger.besu.plugin.data.BlockHeader pluginBlockHeader,
      final BlockHeaderFunctions blockHeaderFunctions) {
    return new org.hyperledger.besu.ethereum.core.BlockHeader(
        Hash.fromHexString(pluginBlockHeader.getParentHash().toHexString()),
        Hash.fromHexString(pluginBlockHeader.getOmmersHash().toHexString()),
        org.hyperledger.besu.ethereum.core.Address.fromHexString(
            pluginBlockHeader.getCoinbase().toHexString()),
        Hash.fromHexString(pluginBlockHeader.getStateRoot().toHexString()),
        Hash.fromHexString(pluginBlockHeader.getTransactionsRoot().toHexString()),
        Hash.fromHexString(pluginBlockHeader.getReceiptsRoot().toHexString()),
        LogsBloomFilter.fromHexString(pluginBlockHeader.getLogsBloom().toHexString()),
        Difficulty.fromHexString(pluginBlockHeader.getDifficulty().toHexString()),
        pluginBlockHeader.getNumber(),
        pluginBlockHeader.getGasLimit(),
        pluginBlockHeader.getGasUsed(),
        pluginBlockHeader.getTimestamp(),
        pluginBlockHeader.getExtraData(),
        pluginBlockHeader.getBaseFee().orElse(null),
        Hash.fromHexString(pluginBlockHeader.getMixHash().toHexString()),
        pluginBlockHeader.getNonce(),
        blockHeaderFunctions);
  }
}
