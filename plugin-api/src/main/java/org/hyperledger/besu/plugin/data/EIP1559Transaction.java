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

package org.hyperledger.besu.plugin.data;

/**
 * A transaction is a single cryptographically-signed instruction constructed by an actor externally
 * to the scope of Ethereum. While it is assumed that the ultimate external actor will be human in
 * nature, software tools will be used in its construction and dissemination.
 *
 * <p>There are two types of transactions: those which result in message calls and those which
 * result in the creation of new accounts with associated code (known informally as ‘contract
 * creation’). Message call transactions will have an address present in the {@link #getTo} method
 * whereas contract creation transactions will not.
 */
public interface EIP1559Transaction
    extends NoncedTransaction,
        GasLimitedTransaction,
        ECDSASignedTransaction,
        SenderTransaction,
        ChainIdTransaction,
        PayloadTransaction,
        ToTransaction,
        ValueTransaction,
        TypedTransaction {

  /**
   * A scalar value equal to the number of Wei to be paid on top of base fee, as specified in
   * EIP-1559.
   *
   * @return the quantity of Wei for gas premium.
   */
  Quantity getGasPremium();

  /**
   * A scalar value equal to the number of Wei to be paid in total, as specified in EIP-1559.
   *
   * @return the quantity of Wei for fee cap.
   */
  Quantity getFeeCap();
}
