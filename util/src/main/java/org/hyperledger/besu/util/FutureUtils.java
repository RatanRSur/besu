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

import static org.hyperledger.besu.util.SafeFuture.completedFuture;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FutureUtils {
  private static final Logger LOG = LogManager.getLogger();

  /**
   * Returns a new CompletionStage that, when the provided stage completes exceptionally, is
   * executed with the provided stage's exception as the argument to the supplied function.
   * Otherwise the returned stage completes successfully with the same value as the provided stage.
   *
   * <p>This is the exceptional equivalent to {@link CompletionStage#thenCompose(Function)}
   *
   * @param future the future to handle results or exceptions from
   * @param errorHandler the function returning a new CompletionStage
   * @param <T> the type of the CompletionStage's result
   * @return the CompletionStage
   */
  public static <T> SafeFuture<T> exceptionallyCompose(
      final SafeFuture<T> future, final Function<Throwable, CompletionStage<T>> errorHandler) {
    final SafeFuture<T> result = new SafeFuture<>();
    future.whenComplete(
        (value, error) -> {
          try {
            final CompletionStage<T> nextStep =
                error != null ? errorHandler.apply(error) : completedFuture(value);
            propagateResult(nextStep, result);
          } catch (final Throwable t) {
            result.completeExceptionally(t);
          }
        });
    return result;
  }

  /**
   * Propagates the result of one {@link CompletionStage} to a different {@link SafeFuture}.
   *
   * <p>When <code>from</code> completes successfully, <code>to</code> will be completed
   * successfully with the same value. When <code>from</code> completes exceptionally, <code>to
   * </code> will be completed exceptionally with the same exception.
   *
   * @param from the CompletionStage to take results and exceptions from
   * @param to the SafeFuture to propagate results and exceptions to
   * @param <T> the type of the success value
   */
  public static <T> void propagateResult(final CompletionStage<T> from, final SafeFuture<T> to) {
    from.whenComplete(
        (value, error) -> {
          if (error != null) {
            to.completeExceptionally(error);
          } else {
            to.complete(value);
          }
        });
  }

  /**
   * Propagates the result of the {@link SafeFuture} returned from a {@link Supplier} to a different
   * {@link SafeFuture}.
   *
   * <p>When <code>from</code> completes successfully, <code>to</code> will be completed
   * successfully with the same value. When <code>from</code> completes exceptionally, <code>to
   * </code> will be completed exceptionally with the same exception.
   *
   * <p>If the Supplier throws an exception, the target SafeFuture will be completed exceptionally
   * with the thrown exception.
   *
   * @param from the Supplier to get the SafeFuture to take results and exceptions from
   * @param to the SafeFuture to propagate results and exceptions to
   * @param <T> the type of the success value
   */
  public static <T> void propagateResult(
      final Supplier<SafeFuture<T>> from, final SafeFuture<T> to) {
    try {
      propagateResult(from.get(), to);
    } catch (final Throwable t) {
      to.completeExceptionally(t);
    }
  }

  /**
   * Propagates cancellation, and only cancellation, from one future to another.
   *
   * <p>When <code>from</code> is completed with a {@link
   * java.util.concurrent.CancellationException} {@link java.util.concurrent.Future#cancel(boolean)}
   * will be called on <code>to</code>, allowing interruption if the future is currently running.
   *
   * @param from the SafeFuture to take cancellation from
   * @param to the SafeFuture to propagate cancellation to
   */
  public static void propagateCancellation(final SafeFuture<?> from, final SafeFuture<?> to) {
    from.exceptionally(
        error -> {
          if (error instanceof CancellationException) {
            to.cancel(true);
          }
          return null;
        });
  }

  static void runWithFixedDelay(
      AsyncRunner runner,
      ExceptionThrowingRunnable runnable,
      Cancellable task,
      long delayAmount,
      TimeUnit delayUnit,
      Consumer<Throwable> exceptionHandler) {

    runner
        .runAfterDelay(
            () -> {
              if (!task.isCancelled()) {
                try {
                  runnable.run();
                } catch (Throwable throwable) {
                  try {
                    exceptionHandler.accept(throwable);
                  } catch (Exception e) {
                    LOG.warn("Exception in exception handler", e);
                  }
                } finally {
                  runWithFixedDelay(
                      runner, runnable, task, delayAmount, delayUnit, exceptionHandler);
                }
              }
            },
            delayAmount,
            delayUnit)
        .finish(() -> {}, exceptionHandler);
  }

  static Cancellable createCancellable() {
    return new Cancellable() {
      private volatile boolean cancelled;

      @Override
      public void cancel() {
        cancelled = true;
      }

      @Override
      public boolean isCancelled() {
        return cancelled;
      }
    };
  }
}
