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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnectionEventDispatcher;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.Handshaker;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.util.SafeFuture;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class HandshakeHandlerOutbound extends AbstractHandshakeHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final ByteBuf first;

  public HandshakeHandlerOutbound(
      final NodeKey nodeKey,
      final Peer peer,
      final List<SubProtocol> subProtocols,
      final LocalNode localNode,
      final SafeFuture<PeerConnection> connectionFuture,
      final PeerConnectionEventDispatcher connectionEventDispatcher,
      final MetricsSystem metricsSystem) {
    super(
        subProtocols,
        localNode,
        Optional.of(peer),
        connectionFuture,
        connectionEventDispatcher,
        metricsSystem);
    handshaker.prepareInitiator(nodeKey, SECP256K1.PublicKey.create(peer.getId()));
    this.first = handshaker.firstMessage();
  }

  @Override
  protected Optional<ByteBuf> nextHandshakeMessage(final ByteBuf msg) {
    final Optional<ByteBuf> nextMsg;
    if (handshaker.getStatus() == Handshaker.HandshakeStatus.IN_PROGRESS) {
      nextMsg = handshaker.handleMessage(msg);
    } else {
      nextMsg = Optional.empty();
    }
    return nextMsg;
  }

  @Override
  public void channelActive(final ChannelHandlerContext ctx) throws Exception {
    super.channelActive(ctx);
    ctx.writeAndFlush(first)
        .addListener(
            f -> {
              if (f.isSuccess()) {
                LOG.debug(
                    "Wrote initial crypto handshake message to {}.", ctx.channel().remoteAddress());
              }
            });
  }
}
