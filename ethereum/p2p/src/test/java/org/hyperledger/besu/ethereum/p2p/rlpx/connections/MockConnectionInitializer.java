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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.ConnectCallback;
import org.hyperledger.besu.util.Subscribers;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SafeFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class MockConnectionInitializer implements ConnectionInitializer {
  private static final AtomicInteger NEXT_PORT = new AtomicInteger(0);

  private final PeerConnectionEventDispatcher eventDispatcher;
  private final Subscribers<ConnectCallback> connectCallbacks = Subscribers.create();
  private boolean autocompleteConnections = true;
  private final Map<Peer, SafeFuture<PeerConnection>> incompleteConnections = new HashMap<>();

  public MockConnectionInitializer(final PeerConnectionEventDispatcher eventDispatcher) {
    this.eventDispatcher = eventDispatcher;
  }

  public void setAutocompleteConnections(final boolean shouldAutocomplete) {
    this.autocompleteConnections = shouldAutocomplete;
  }

  public void completePendingFutures() {
    for (Map.Entry<Peer, SafeFuture<PeerConnection>> conn : incompleteConnections.entrySet()) {
      conn.getValue().complete(MockPeerConnection.create(conn.getKey()));
    }
    incompleteConnections.clear();
  }

  public void simulateIncomingConnection(final PeerConnection incomingConnection) {
    connectCallbacks.forEach(c -> c.onConnect(incomingConnection));
  }

  @Override
  public SafeFuture<InetSocketAddress> start() {
    InetSocketAddress socketAddress =
        new InetSocketAddress("127.0.0.1", NEXT_PORT.incrementAndGet());
    return SafeFuture.completedFuture(socketAddress);
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  public void subscribeIncomingConnect(final ConnectCallback callback) {
    connectCallbacks.subscribe(callback);
  }

  @Override
  public SafeFuture<PeerConnection> connect(final Peer peer) {
    if (autocompleteConnections) {
      return SafeFuture.completedFuture(MockPeerConnection.create(peer, eventDispatcher));
    } else {
      final SafeFuture<PeerConnection> future = new SafeFuture<>();
      incompleteConnections.put(peer, future);
      return future;
    }
  }
}
