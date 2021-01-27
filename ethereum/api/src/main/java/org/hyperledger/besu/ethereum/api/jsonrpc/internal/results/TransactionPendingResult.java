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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "blockHash",
  "blockNumber",
  "from",
  "gas",
  "gasPrice",
  "hash",
  "input",
  "nonce",
  "to",
  "transactionIndex",
  "value",
  "v",
  "r",
  "s"
})
public class TransactionPendingResult implements TransactionResult {

  private final String chainId;
  private final String from;
  private final String gas;
  private final String gasPrice;
  private final String hash;
  private final String input;
  private final String nonce;
  private final String publicKey;
  private final String raw;
  private final String to;
  private final String value;
  private final String v;
  private final String r;
  private final String s;

  public TransactionPendingResult(final Transaction transaction) {
    this.chainId = transaction.getChainId().map(Quantity::create).orElse(null);
    this.from = transaction.getSender().toString();
    this.gas = Quantity.create(transaction.getGasLimit());
    this.gasPrice = Quantity.create(transaction.getGasPrice());
    this.hash = transaction.getHash().toString();
    this.input = transaction.getPayload().toString();
    this.nonce = Quantity.create(transaction.getNonce());
    this.publicKey = transaction.getPublicKey().orElse(null);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    transaction.writeTo(out);
    this.raw = out.encoded().toString();
    this.to = transaction.getTo().map(Address::toHexString).orElse(null);
    this.value = Quantity.create(transaction.getValue());
    this.v = Quantity.create(transaction.getV());
    this.r = Quantity.create(transaction.getR());
    this.s = Quantity.create(transaction.getS());
  }

  @JsonGetter(value = "chainId")
  public String getChainId() {
    return chainId;
  }

  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
  }

  @JsonGetter(value = "gas")
  public String getGas() {
    return gas;
  }

  @JsonGetter(value = "gasPrice")
  public String getGasPrice() {
    return gasPrice;
  }

  @JsonGetter(value = "hash")
  public String getHash() {
    return hash;
  }

  @JsonGetter(value = "input")
  public String getInput() {
    return input;
  }

  @JsonGetter(value = "nonce")
  public String getNonce() {
    return nonce;
  }

  @JsonGetter(value = "publicKey")
  public String getPublicKey() {
    return publicKey;
  }

  @JsonGetter(value = "raw")
  public String getRaw() {
    return raw;
  }

  @JsonGetter(value = "to")
  public String getTo() {
    return to;
  }

  @JsonGetter(value = "value")
  public String getValue() {
    return value;
  }

  @JsonGetter(value = "v")
  public String getV() {
    return v;
  }

  @JsonGetter(value = "r")
  public String getR() {
    return r;
  }

  @JsonGetter(value = "s")
  public String getS() {
    return s;
  }
}
