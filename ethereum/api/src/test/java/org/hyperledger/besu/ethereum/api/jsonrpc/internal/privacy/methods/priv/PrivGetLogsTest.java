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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import io.vertx.ext.auth.User;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivGetLogsTest {

  private final String ENCLAVE_KEY = "enclave_key";
  private final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private PrivacyQueries privacyQueries;
  @Mock private PrivacyController privacyController;
  @Mock private EnclavePublicKeyProvider enclavePublicKeyProvider;

  private PrivGetLogs method;

  @Before
  public void before() {
    method =
        new PrivGetLogs(
            blockchainQueries, privacyQueries, privacyController, enclavePublicKeyProvider);
  }

  @Test
  public void getMethodReturnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("priv_getLogs");
  }

  @Test
  public void privacyGroupIdIsRequired() {
    final JsonRpcRequestContext request = privGetLogRequest(null, mock(FilterParameter.class));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 0");
  }

  @Test
  public void filterParameterIsRequired() {
    final JsonRpcRequestContext request = privGetLogRequest(PRIVACY_GROUP_ID, null);

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 1");
  }

  @Test
  public void logQueryIsBuiltCorrectly() {
    final Hash blockHash = Hash.hash(Bytes32.random());
    final List<Address> addresses = List.of(Address.ZERO);
    final List<List<LogTopic>> logTopics = List.of(List.of(LogTopic.of(Bytes32.random())));

    final FilterParameter blockHashFilter =
        new FilterParameter(null, null, addresses, logTopics, blockHash);

    final LogsQuery expectedQuery =
        new LogsQuery.Builder().addresses(addresses).topics(logTopics).build();

    final JsonRpcRequestContext request = privGetLogRequest(PRIVACY_GROUP_ID, blockHashFilter);
    method.response(request);

    verify(privacyQueries).matchingLogs(eq(PRIVACY_GROUP_ID), eq(blockHash), eq((expectedQuery)));
  }

  @Test
  public void filterWithBlockHashQueriesOneBlockByHash() {
    final Hash blockHash = Hash.hash(Bytes32.random());
    final FilterParameter blockHashFilter =
        new FilterParameter(
            null, null, Collections.emptyList(), Collections.emptyList(), blockHash);

    final List<LogWithMetadata> logWithMetadataList = logWithMetadataList(3);
    final LogsResult expectedLogsResult = new LogsResult(logWithMetadataList);

    when(privacyQueries.matchingLogs(eq(PRIVACY_GROUP_ID), eq(blockHash), any(LogsQuery.class)))
        .thenReturn(logWithMetadataList);

    final JsonRpcRequestContext request = privGetLogRequest(PRIVACY_GROUP_ID, blockHashFilter);
    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    final LogsResult logsResult = (LogsResult) response.getResult();

    assertThat(logsResult).usingRecursiveComparison().isEqualTo(expectedLogsResult);
  }

  @Test
  public void filterWithBlockRangeQueriesRangeOfBlock() {
    long chainHeadBlockNumber = 3L;
    final FilterParameter blockHashFilter =
        new FilterParameter(
            BlockParameter.EARLIEST,
            BlockParameter.LATEST,
            Collections.emptyList(),
            Collections.emptyList(),
            null);
    final List<LogWithMetadata> logWithMetadataList = logWithMetadataList(3);
    final LogsResult expectedLogsResult = new LogsResult(logWithMetadataList);

    when(blockchainQueries.headBlockNumber()).thenReturn(chainHeadBlockNumber);
    when(privacyQueries.matchingLogs(
            eq(PRIVACY_GROUP_ID), eq(0L), eq(chainHeadBlockNumber), any(LogsQuery.class)))
        .thenReturn(logWithMetadataList);

    final JsonRpcRequestContext request = privGetLogRequest(PRIVACY_GROUP_ID, blockHashFilter);
    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    final LogsResult logsResult = (LogsResult) response.getResult();

    assertThat(logsResult).usingRecursiveComparison().isEqualTo(expectedLogsResult);
  }

  @Test
  public void multiTenancyCheckFailure() {
    final User user = mock(User.class);
    final FilterParameter filterParameter = mock(FilterParameter.class);

    when(enclavePublicKeyProvider.getEnclaveKey(any())).thenReturn(ENCLAVE_KEY);
    doThrow(new MultiTenancyValidationException("msg"))
        .when(privacyController)
        .verifyPrivacyGroupContainsEnclavePublicKey(eq(PRIVACY_GROUP_ID), eq(ENCLAVE_KEY));

    final JsonRpcRequestContext request =
        privGetLogRequestWithUser(PRIVACY_GROUP_ID, filterParameter, user);

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(MultiTenancyValidationException.class);
  }

  private JsonRpcRequestContext privGetLogRequest(
      final String privacyGroupId, final FilterParameter filterParameter) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "priv_getLogs", new Object[] {privacyGroupId, filterParameter}));
  }

  private JsonRpcRequestContext privGetLogRequestWithUser(
      final String privacyGroupId, final FilterParameter filterParameter, final User user) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "priv_getLogs", new Object[] {privacyGroupId, filterParameter}),
        user);
  }

  private List<LogWithMetadata> logWithMetadataList(final int length) {
    return IntStream.range(0, length).mapToObj(this::logWithMetadata).collect(Collectors.toList());
  }

  private LogWithMetadata logWithMetadata(final int logIndex) {
    return new LogWithMetadata(
        logIndex,
        100L,
        Hash.ZERO,
        Hash.ZERO,
        0,
        Address.fromHexString("0x0"),
        Bytes.EMPTY,
        Lists.newArrayList(),
        false);
  }
}
