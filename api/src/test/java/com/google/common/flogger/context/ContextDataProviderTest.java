/*
 * Copyright (C) 2019 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.flogger.context;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.flogger.context.ScopedLoggingContext.InvalidLoggingScopeStateException;
import java.io.Closeable;
import java.util.logging.Level;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ContextDataProviderTest {

  private static final Tags TEST_TAGS = Tags.builder().addTag("foo", "bar").build();
  private static final LogLevelMap TEST_MAP =
      LogLevelMap.builder().add(Level.FINEST, String.class).build();

  // A context which fails when the scope is closed. Used to verify that user errors are
  // prioritized in cases where errors cause scopes to be exited.
  private static final ScopedLoggingContext ERROR_CONTEXT =
      new ScopedLoggingContext() {
        @Override
        public Closeable withNewScope() {
          return () -> {
            throw new IllegalArgumentException("BAD CONTEXT");
          };
        }

        @Override
        public boolean applyLogLevelMap(LogLevelMap m) {
          return false;
        }

        @Override
        public boolean addTags(Tags tags) {
          return false;
        }
      };

  @Test
  public void testNoOpImplementation() {
    ContextDataProvider ctxData = new NoOpContextDataProvider();
    assertThat(ctxData.getTags()).isEqualTo(Tags.empty());
    assertThat(ctxData.shouldForceLogging("java.lang.String", Level.FINE, true)).isFalse();

    ScopedLoggingContext api = ctxData.getContextApiSingleton();
    assertThat(api.addTags(TEST_TAGS)).isFalse();
    assertThat(api.applyLogLevelMap(TEST_MAP)).isFalse();
  }

  @Test
  public void testNoOpScopesHaveNoEffect() throws Exception {
    ContextDataProvider ctxData = new NoOpContextDataProvider();
    ScopedLoggingContext api = ctxData.getContextApiSingleton();

    boolean didTest =
        api.call(
            () -> {
              // API reports failure to modify the current context.
              assertThat(api.addTags(TEST_TAGS)).isFalse();
              assertThat(api.applyLogLevelMap(TEST_MAP)).isFalse();

              // And there's no effect on the current context.
              assertThat(ctxData.getTags()).isEqualTo(Tags.empty());
              assertThat(ctxData.shouldForceLogging("java.lang.String", Level.FINE, true))
                  .isFalse();
              return true;
            });
    assertThat(didTest).isTrue();
  }

  @Test
  public void testErrorHandlingWithoutUserError() {
    InvalidLoggingScopeStateException e =
        assertThrows(InvalidLoggingScopeStateException.class, () -> ERROR_CONTEXT.run(() -> {}));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().isEqualTo("BAD CONTEXT");
  }

  @Test
  public void testErrorHandlingWithUserError() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ERROR_CONTEXT.run(
                    () -> {
                      throw new IllegalArgumentException("User error");
                    }));
    assertThat(e).hasMessageThat().isEqualTo("User error");
  }

  // Annoyingly Bazel does not support a sufficiently recent version of JUnit so we have to
  // reimplement assertThrows() ourselves.
  private static <T extends Throwable> T assertThrows(Class<T> clazz, Runnable code) {
    try {
      code.run();
    } catch (Throwable t) {
      if (!clazz.isInstance(t)) {
        fail("expected " + clazz.getName() + " but got " + t.getClass().getName());
      }
      return clazz.cast(t);
    }
    fail("expected " + clazz.getName() + " was not thrown");
    // Unreachable code to keep the compiler happy.
    return null;
  }
}
