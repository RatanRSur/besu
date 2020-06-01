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
 *
 */
package org.hyperledger.besu.ethereum.mainnet.precompiles;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

import org.apache.tuweni.bytes.Bytes;

public class BLS12G2MulPrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12G2MulPrecompiledContract() {
    super("BLS12_G2MUL", LibEthPairings.BLS12_G2MUL_OPERATION_RAW_VALUE);
  }

  @Override
  public Gas gasRequirement(final Bytes input) {
    return Gas.of(55_000);
  }
}
