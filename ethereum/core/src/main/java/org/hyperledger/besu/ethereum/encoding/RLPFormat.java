/*
 *
 *  * Copyright ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.encoding;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public interface RLPFormat {
  @FunctionalInterface
  interface Encoder<T> {
    void encode(T object, RLPOutput output);
  }

  @FunctionalInterface
  interface Decoder<T> {
    T decode(RLPInput input);
  }

  static void encodeLatest(Transaction transaction, RLPOutput rlpOutput) {
    // TODO is there a better way to do this?
    new BerlinRLPFormat().encode(transaction, rlpOutput);
  }

  void encode(Transaction transaction, RLPOutput rlpOutput);

  Transaction decode(RLPInput rlpInput);
}