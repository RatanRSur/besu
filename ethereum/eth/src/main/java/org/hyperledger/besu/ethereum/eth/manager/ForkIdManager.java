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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ForkIdManager {

  private Hash genesisHash;
  private CRC32 crc = new CRC32();
  private Long currentHead;
  private Long forkNext;
  private Long highestKnownFork = 0L;
  private ForkId lastKnownEntry = null;
  private ArrayDeque<ForkId> forkAndHashList;

  public ForkIdManager(final Hash genesisHash, final Set<Long> forks, final Long currentHead) {
    this.genesisHash = genesisHash;
    this.currentHead = currentHead;
    if (forks != null) {
      forkAndHashList = collectForksAndHashes(forks, currentHead);
    } else {
      forkAndHashList = new ArrayDeque<>();
    }
  };

  public static ForkIdManager buildCollection(
      final Hash genesisHash,
      final List<Long> forks,
      final Blockchain blockchain,
      final List<Capability> caps) {
    if (forks == null) {
      return new ForkIdManager(genesisHash, null, blockchain.getChainHeadBlockNumber());
    } else {
      Set<Long> forkSet = new LinkedHashSet<>(forks);
      return new ForkIdManager(genesisHash, forkSet, blockchain.getChainHeadBlockNumber());
    }
  };

  public static ForkIdManager buildCollection(final Hash genesisHash, final List<Long> forks) {
    if (forks == null) {
      return new ForkIdManager(genesisHash, null, Long.MAX_VALUE);
    } else {
      Set<Long> forkSet = new LinkedHashSet<>(forks);
      return new ForkIdManager(genesisHash, forkSet, Long.MAX_VALUE);
    }
  };

  public static ForkIdManager buildCollection(final Hash genesisHash) {
    return new ForkIdManager(genesisHash, null, Long.MAX_VALUE);
  };

  public static ForkId readFrom(final RLPInput in) {
    in.enterList();
    final BytesValue hash = in.readBytesValue();
    final BytesValue next = in.readBytesValue();
//    final long next = in.readLong();
    in.leaveList();
    return new ForkId(hash, next);
  }

//  public static ForkId readFrom(final RLPInput in) {
//    in.enterList();
//    final BytesValue hash = in.readBytesValue();
//    final long next = in.readLong();
//    in.leaveList();
//    return new ForkId(hash, next);
//  }

  // Non-RLP entry (for tests)
  public static ForkId createIdEntry(final String hash, final long next) {
    return new ForkId(BytesValue.wrap(hexStringToByteArray(hash)), BytesValue.wrap(longToBigEndian(next)));
  }

  public static ForkId createIdEntry(final String hash, final String next) {
    BytesValue bHash;
    System.out.print("String hash: "); // todo remove dev item
    System.out.print(hash); // todo remove dev item
    System.out.print(", String next: "); // todo remove dev item
    System.out.println(next); // todo remove dev item
    bHash = BytesValue.wrap(hexStringToByteArray(hash));
    if(bHash.size() < 4){
      bHash = padToEightBytes(bHash);
    }
    if(next.equals("") || next.equals("0x")){
      System.out.println("1"); // todo remove dev item
      return new ForkId(bHash, BytesValue.wrap(hexStringToByteArray("0x")));
    } else if (next.startsWith("0x")) {
      System.out.println("2"); // todo remove dev item
      long asLong = Long.parseLong(next.replaceFirst("0x", ""), 16);
      return new ForkId(bHash, BytesValues.trimLeadingZeros(BytesValue.wrap(longToBigEndian(asLong))));
    }  else {
      System.out.println("3"); // todo remove dev item
      return new ForkId(bHash,  BytesValue.wrap(longToBigEndian(Long.parseLong(next))));
    }
  }

  public static ForkId createIdEntry(final BytesValue hash, final String next) {
    if (next.startsWith("0x")) {
      String temp = next.replaceFirst("0x", "");
      BytesValue temp2 =  BytesValue.wrap(longToBigEndian(Long.parseLong(temp, 16)));
      return new ForkId(hash, BytesValues.trimLeadingZeros(temp2));
    } else if(next.equals("")){
      return new ForkId(hash, BytesValue.wrap(hexStringToByteArray("0x")));
    } else {
      return new ForkId(hash, BytesValue.wrap(longToBigEndian(Long.parseLong(next))));
    }
  }

  public static ForkId createIdEntry(final BytesValue hash, final long next) {
    return new ForkId(hash, BytesValue.wrap(longToBigEndian(next)));
  }

  private static BytesValue padToEightBytes(final BytesValue hash){
    if(hash.size() < 4){
      BytesValue padded = BytesValues.concatenate(hash, BytesValue.wrap(hexStringToByteArray("0x00")));
     return padToEightBytes(padded);
    } else {
      return hash;
    }
  }

  public ArrayDeque<ForkId> getForkAndHashList() {
    return this.forkAndHashList;
  }

  public ForkId getLatestForkId() {
    return lastKnownEntry;
  }

  public boolean forkIdCapable() {
    return forkAndHashList.size() != 0;
  }

  /**
   * EIP-2124 behaviour
   *
   * @param forkId to be validated.
   * @return boolean
   */
  public boolean peerCheck(final ForkId forkId) {
    System.out.println(getForkAndHashList()); // todo remove dev item
    if (forkId == null) {
      return false;
    }
    // Run the fork checksum validation ruleset:
    //   1. If local and remote FORK_CSUM matches, connect.
    //        The two nodes are in the same fork state currently. They might know
    //        of differing future forks, but that's not relevant until the fork
    //        triggers (might be postponed, nodes might be updated to match).
    //   2. If the remote FORK_CSUM is a subset of the local past forks and the
    //      remote FORK_NEXT matches with the locally following fork block number,
    //      connect.
    //        Remote node is currently syncing. It might eventually diverge from
    //        us, but at this current point in time we don't have enough information.
    //   3. If the remote FORK_CSUM is a superset of the local past forks and can
    //      be completed with locally known future forks, connect.
    //        Local node is currently syncing. It might eventually diverge from
    //        the remote, but at this current point in time we don't have enough
    //        information.
    //   4. Reject in all other cases.
    if (isHashKnown(forkId.hash)) {
      if (currentHead < forkNext) {
        return true;
      } else {
        if (isForkKnown(forkId.getNextAsLong())) {
          return isRemoteAwareOfPresent(forkId.hash, forkId.getNextAsLong());
        } else {
          return false;
        }
      }
    } else {
      return false;
    }
  }

  /**
   * Non EIP-2124 behaviour
   *
   * @param peerGenesisOrCheckSumHash Hash or checksum to be validated.
   * @return boolean
   */
  public boolean peerCheck(final Bytes32 peerGenesisOrCheckSumHash) {
    return !peerGenesisOrCheckSumHash.equals(genesisHash);
  }

  private boolean isHashKnown(final BytesValue forkHash) {
    System.out.println(forkHash); // todo remove dev item
    for (ForkId j : forkAndHashList) {
      if (forkHash.equals(j.hash)) {
        return true;
      }
    }
    return false;
  }

  private boolean isForkKnown(final Long nextFork) {
    if (highestKnownFork < nextFork) {
      return true;
    }
    for (ForkId j : forkAndHashList) {
      if (nextFork.equals(j.getNextAsLong())) {
        return true;
      }
    }
    return false;
  }

  private boolean isRemoteAwareOfPresent(final BytesValue forkHash, final Long nextFork) {
    for (ForkId j : forkAndHashList) {
      if (forkHash.equals(j.hash)) {
        if (nextFork.equals(j.getNextAsLong())) {
          return true;
        } else if (j.getNextAsLong() == 0L) {
          return highestKnownFork <= nextFork; // Remote aware of future fork
        } else {
          return false;
        }
      }
    }
    return false;
  }
// TODO: sort these when the list of forks is first gathered
  private ArrayDeque<ForkId> collectForksAndHashes(final Set<Long> forks, final Long currentHead) {
    boolean first = true;
    System.out.println(forks); // todo remove dev item
    ArrayDeque<ForkId> forkList = new ArrayDeque<>();
    Iterator<Long> iterator = forks.iterator();
    while (iterator.hasNext()) {
      Long forkBlockNumber = iterator.next();
      if (highestKnownFork < forkBlockNumber) {
        highestKnownFork = forkBlockNumber;
      }
      if (first) {
        // first fork
        first = false;
        forkList.add(
            new ForkId(updateCrc(this.genesisHash.getHexString()), forkBlockNumber)); // Genesis
        updateCrc(forkBlockNumber);

      } else if (!iterator.hasNext()) {
        // most recent fork
        forkList.add(new ForkId(getCurrentCrcHash(), forkBlockNumber));
        updateCrc(forkBlockNumber);
        lastKnownEntry = new ForkId(getCurrentCrcHash(), 0L);
        forkList.add(lastKnownEntry);
        if (currentHead > forkBlockNumber) {
          this.forkNext = 0L;
        } else {
          this.forkNext = forkBlockNumber;
        }

      } else {
        forkList.add(new ForkId(getCurrentCrcHash(), forkBlockNumber));
        updateCrc(forkBlockNumber);
      }
    }
    return forkList;
  }

  private BytesValue updateCrc(final Long block) {
    byte[] byteRepresentationFork = longToBigEndian(block);
    crc.update(byteRepresentationFork, 0, byteRepresentationFork.length);
    return getCurrentCrcHash();
  }

  private BytesValue updateCrc(final String hash) {
    byte[] byteRepresentation = hexStringToByteArray(hash);
    crc.update(byteRepresentation, 0, byteRepresentation.length);
    return getCurrentCrcHash();
  }

  public BytesValue getCurrentCrcHash() {
    return BytesValues.ofUnsignedInt(crc.getValue());
  }

  // TODO use Hash class instead of string for checksum.  convert to or from string only when needed
  // ^ the crc is not hashed/checksum-ed any further so the hash class is not suited for this case
  public static class ForkId {
    BytesValue hash;
    BytesValue next;
    BytesValue forkIdRLP;

    public ForkId(final BytesValue hash, final BytesValue next) {
      this.hash = hash;
      this.next = next;
      createForkIdRLP();
    }

    public ForkId(final String hash, final String next) {
      this.hash = BytesValue.wrap(hexStringToByteArray(hash));
      if(this.hash.size() < 4){
        this.hash = padToEightBytes(this.hash);
      }
      if(next.equals("") || next.equals("0x")){
        this.next = BytesValue.wrap(hexStringToByteArray("0x"));
      } else if (next.startsWith("0x")) {
        long asLong = Long.parseLong(next.replaceFirst("0x", ""), 16);
        this.next = BytesValues.trimLeadingZeros(BytesValue.wrap(longToBigEndian(asLong)));
      }  else {
        this.next = BytesValue.wrap(longToBigEndian(Long.parseLong(next)));
      }
      createForkIdRLP();
    }

    public ForkId(final String hash, final long next) {
      this.hash = BytesValue.wrap(hexStringToByteArray(hash));
      this.next = BytesValue.wrap(longToBigEndian(next));
      createForkIdRLP();
    }

    public ForkId(final BytesValue hash, final String next) {
      this.hash = hash;
      this.next = BytesValue.wrap(longToBigEndian(Long.parseLong(next)));
      createForkIdRLP();
    }

    public ForkId(final BytesValue hash, final long next) {
      this.hash = hash;
      this.next = BytesValue.wrap(longToBigEndian(next));
      createForkIdRLP();
    }

    public long getNextAsLong(){
      return BytesValues.extractLong(next);
    }


    public void createForkIdRLP() {
      BytesValueRLPOutput out = new BytesValueRLPOutput();
//      out.writeList(asList(), ForkId::writeTo);
      writeTo(out);
      forkIdRLP = out.encoded();
    }

    public void writeTo(final RLPOutput out) {
      out.startList();
      out.writeBytesValue(hash);
      out.writeBytesValue(next);
      out.endList();
    }

    // Non-RLP entry (for tests)
    public BytesValue createNotAsListForkIdRLP() {
      BytesValueRLPOutput outPlain = new BytesValueRLPOutput();
      outPlain.startList();
      outPlain.writeBytesValue(hash);
      outPlain.writeBytesValue(next);
      outPlain.endList();
      return outPlain.encoded();
    }

    public static ForkId readFrom(final RLPInput in) {
      in.enterList();
      final BytesValue hash = in.readBytesValue();
      final long next = in.readLong();
      in.leaveList();
      return new ForkId(hash, next);
    }

    public List<byte[]> asByteList() {
      ArrayList<byte[]> forRLP = new ArrayList<byte[]>();
      forRLP.add(hash.getByteArray());
      forRLP.add(next.getByteArray());
      return forRLP;
    }

    public List<ForkId> asList() {
      ArrayList<ForkId> forRLP = new ArrayList<>();
      forRLP.add(this);
      return forRLP;
    }

    @Override
    public String toString() {
      return "ForkId(hash=" + this.hash + ", next=" + this.next + ")";
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj instanceof ForkId) {
        ForkId other = (ForkId) obj;
        return other.hash.equals(this.hash) && other.next.equals(this.next);
//        return other.hash.equals(this.hash) && other.next == this.next;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return super.hashCode();
    }
  }

  // TODO: Ask / look to see if there is a helper for these below <----------
  private static byte[] hexStringToByteArray(final String s) {
    String string = "";
    if (s.startsWith("0x")) {
      string = s.replaceFirst("0x", "");
    }
    string = (string.length() % 2 == 0 ? "" : "0") + string;
    return decodeHexString(string);
  }

  // next three methods adopted from:
  // https://github.com/bcgit/bc-java/blob/master/core/src/main/java/org/bouncycastle/util/Pack.java
  private static byte[] longToBigEndian(final long n) {
    byte[] bs = new byte[8];
    intToBigEndian((int) (n >>> 32), bs, 0);
    intToBigEndian((int) (n & 0xffffffffL), bs, 4);
    return bs;
  }

  @SuppressWarnings("MethodInputParametersMustBeFinal")
  private static void intToBigEndian(final int n, final byte[] bs, int off) {
    bs[off] = (byte) (n >>> 24);
    bs[++off] = (byte) (n >>> 16);
    bs[++off] = (byte) (n >>> 8);
    bs[++off] = (byte) (n);
  }

  private static byte[] decodeHexString(final String hexString) {
    if (hexString.length() % 2 == 1) {
      throw new IllegalArgumentException("Invalid hexadecimal String supplied.");
    }

    byte[] bytes = new byte[hexString.length() / 2];
    for (int i = 0; i < hexString.length(); i += 2) {
      bytes[i / 2] = hexToByte(hexString.substring(i, i + 2));
    }
    return bytes;
  }

  private static byte hexToByte(final String hexString) {
    int firstDigit = toDigit(hexString.charAt(0));
    int secondDigit = toDigit(hexString.charAt(1));
    return (byte) ((firstDigit << 4) + secondDigit);
  }

  private static int toDigit(final char hexChar) {
    int digit = Character.digit(hexChar, 16);
    if (digit == -1) {
      throw new IllegalArgumentException("Invalid Hexadecimal Character: " + hexChar);
    }
    return digit;
  }
}
