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
package org.hyperledger.besu.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.util.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;

public class SafeFutureAssertTest {

  @Test
  public void isCompletedWithEmptyOptional_withEmptyOptional() {
    SafeFuture<Optional<?>> result = SafeFuture.completedFuture(Optional.empty());
    assertThatSafeFuture(result).isCompletedWithEmptyOptional();
  }

  @Test
  public void isCompletedWithEmptyOptional_withIncompleteFuture() {
    SafeFuture<Optional<?>> result = new SafeFuture<>();
    assertThatThrownBy(() -> assertThatSafeFuture(result).isCompletedWithEmptyOptional())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  public void isCompletedWithEmptyOptional_withNonEmptyOptional() {
    SafeFuture<Optional<?>> result = SafeFuture.completedFuture(Optional.of(11));
    assertThatThrownBy(() -> assertThatSafeFuture(result).isCompletedWithEmptyOptional())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  public void isCompletedWithEmptyOptional_withNonOptional() {
    SafeFuture<?> result = SafeFuture.completedFuture(12);
    assertThatThrownBy(() -> assertThatSafeFuture(result).isCompletedWithEmptyOptional())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  public void isCompletedWithNonEmptyOptional_withNonEmptyOptional() {
    SafeFuture<Optional<?>> result = SafeFuture.completedFuture(Optional.of(11));
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
  }

  @Test
  public void isCompletedWithNonEmptyOptional_withEmptyOptional() {
    SafeFuture<Optional<?>> result = SafeFuture.completedFuture(Optional.empty());
    assertThatThrownBy(() -> assertThatSafeFuture(result).isCompletedWithNonEmptyOptional())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  public void isCompletedWithNonEmptyOptional_withIncompleteFuture() {
    SafeFuture<Optional<?>> result = new SafeFuture<>();
    assertThatThrownBy(() -> assertThatSafeFuture(result).isCompletedWithNonEmptyOptional())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  public void isCompletedWithNonEmptyOptional_withNonOptional() {
    SafeFuture<?> result = SafeFuture.completedFuture(12);
    assertThatThrownBy(() -> assertThatSafeFuture(result).isCompletedWithNonEmptyOptional())
        .isInstanceOf(AssertionError.class);
  }
}
