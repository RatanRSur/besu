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

import java.math.BigInteger;

public interface ECDSASignedTransaction {
  /**
   * Value corresponding to the 'V' component of the signature of the transaction.
   *
   * @return the 'V' component of the signature
   */
  BigInteger getV();

  /**
   * Value corresponding to the 'V' component of the signature of the transaction.
   *
   * @return the 'V' component of the signature
   */
  BigInteger getR();

  /**
   * Value corresponding to the 'V' component of the signature of the transaction.
   *
   * @return the 'V' component of the signature
   */
  BigInteger getS();
}
