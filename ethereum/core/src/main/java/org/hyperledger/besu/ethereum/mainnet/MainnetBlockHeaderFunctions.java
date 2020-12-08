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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.encoding.ProtocolRLPSpec;
import org.hyperledger.besu.ethereum.rlp.RLP;

/** Implements the block hashing algorithm for MainNet as per the yellow paper. */
public class MainnetBlockHeaderFunctions implements BlockHeaderFunctions {

  @Override
  public Hash hash(final BlockHeader header) {
    return createHash(header);
  }

  public static Hash createHash(final BlockHeader blockHeader) {
    return Hash.hash(RLP.encode(rlpOutput -> ProtocolRLPSpec.encode(blockHeader, rlpOutput)));
  }

  @Override
  public ParsedExtraData parseExtraData(final BlockHeader header) {
    return null;
  }
}
