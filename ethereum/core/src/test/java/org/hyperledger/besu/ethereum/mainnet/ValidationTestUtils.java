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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.encoding.ProtocolRLPSpec;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.io.IOException;

import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;

public final class ValidationTestUtils {

  public static BlockHeader readHeader(final long num) throws IOException {
    final RLPInput input =
        new BytesValueRLPInput(
            Bytes.wrap(
                Resources.toByteArray(
                    EthHashTest.class.getResource(String.format("block_%d.blocks", num)))),
            false);
    input.enterList();
    return ProtocolRLPSpec.decodeBlockHeaderStandalone(input, new MainnetBlockHeaderFunctions());
  }

  public static Block readBlock(final long num) throws IOException {
    final RLPInput input =
        new BytesValueRLPInput(
            Bytes.wrap(
                Resources.toByteArray(
                    EthHashTest.class.getResource(String.format("block_%d.blocks", num)))),
            false);
    return ProtocolRLPSpec.decodeBlockStandalone(
        ProtocolScheduleFixture.MAINNET, new MainnetBlockHeaderFunctions(), input);
  }
}
