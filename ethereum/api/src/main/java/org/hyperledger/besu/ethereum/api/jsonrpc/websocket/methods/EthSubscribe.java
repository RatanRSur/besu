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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.InvalidSubscriptionRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscribeRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;

public class EthSubscribe extends AbstractSubscriptionMethod {

  EthSubscribe(
      final SubscriptionManager subscriptionManager, final SubscriptionRequestMapper mapper) {
    super(subscriptionManager, mapper);
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SUBSCRIBE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    try {
      final SubscribeRequest subscribeRequest = getMapper().mapSubscribeRequest(request);
      final Long subscriptionId = getSubscriptionManager().subscribe(subscribeRequest);

      return new JsonRpcSuccessResponse(request.getId(), Quantity.create(subscriptionId));
    } catch (final InvalidSubscriptionRequestException isEx) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_REQUEST);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INTERNAL_ERROR);
    }
  }
}
