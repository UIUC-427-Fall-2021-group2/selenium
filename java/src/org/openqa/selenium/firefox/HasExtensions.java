// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.firefox;

import org.openqa.selenium.Beta;

import java.nio.file.Path;

/**
 * Used by classes to indicate that they can install and uninstall browser extensions on the fly.
 */
@Beta
public interface HasExtensions {

  /**
   * Installs an extension.
   *
   * @param path absolute path to the extension file that should be installed.
   * @return the unique identifier of the installed extension.
   */
  String installExtension(Path path);

  /**
   * Uninstall the extension by the given identifier.
   * This value can be found in the extension's manifest, and typically ends with "@mozilla.org".
   *
   * @param extensionId The unique extension identifier returned by {{@link #installExtension(Path)}}
   */
  void uninstallExtension(String extensionId);
}
