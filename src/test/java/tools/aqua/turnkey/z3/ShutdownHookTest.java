/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2024 The TurnKey Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools.aqua.turnkey.z3;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.assertj.core.api.Assumptions.assumeThatThrownBy;

import com.microsoft.z3.Native;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Test that the loader library schedules the unpacked Z3 native libraries for deletion. */
class ShutdownHookTest {

  /** The library names to look out for. */
  private static final List<String> LIBRARY_NAMES = asList("z3", "z3java");

  private static String getFileBaseName(final String file) {
    final String fileName = requireNonNull(Paths.get(file).getFileName()).toString();
    final int fileNameDot = fileName.indexOf('.');
    return (fileNameDot == -1) ? fileName : fileName.substring(0, fileNameDot);
  }

  private static String getLibraryBaseName(final String library) {
    final String name = getFileBaseName(library);
    return name.startsWith("lib") ? name.substring("lib".length()) : name;
  }

  /**
   * Reflectively obtain the list of deletion-schedules files and verify that files starting with
   * {@code name.} for {@code name} in {@link #LIBRARY_NAMES} are scheduled for deletion on JVM exit
   * after Z3 has been invoked.
   *
   * @throws ClassNotFoundException on reflection error.
   * @throws NoSuchFieldException on reflection error.
   * @throws IllegalAccessException on reflection error.
   */
  @Test
  void testShutdownHookGeneration()
      throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {

    final Class<?> deleteOnExitHook = Class.forName("java.io.DeleteOnExitHook");
    final Field files = deleteOnExitHook.getDeclaredField("files");
    assumeThatThrownBy(() -> files.setAccessible(true)).doesNotThrowAnyException();

    @SuppressWarnings("unchecked")
    final Set<String> before = new HashSet<>((Collection<String>) files.get(null));

    assumeThat(before)
        .map(ShutdownHookTest::getLibraryBaseName)
        .doesNotContainAnyElementsOf(LIBRARY_NAMES);

    Native.getFullVersion();

    @SuppressWarnings("unchecked")
    final Set<String> after = new HashSet<>((Collection<String>) files.get(null));
    final Set<String> newFiles = new HashSet<>(after);
    newFiles.removeAll(before);

    assertThat(newFiles).map(ShutdownHookTest::getLibraryBaseName).containsAll(LIBRARY_NAMES);
  }
}
