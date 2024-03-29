/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.snapshot;

import org.apache.hadoop.hbase.client.SnapshotDescription;
import org.apache.yetus.audience.InterfaceAudience;

/**
 * Thrown when a snapshot could not be restored/cloned because the ttl for snapshot has already
 * expired
 */
@SuppressWarnings("serial")
@InterfaceAudience.Public
public class SnapshotTTLExpiredException extends HBaseSnapshotException {
  /**
   * Failure when the ttl for snapshot has already expired.
   * @param message the full description of the failure
   */
  public SnapshotTTLExpiredException(String message) {
    super(message);
  }

  /**
   * Failure when the ttl for snapshot has already expired.
   * @param snapshotDescription snapshot that was attempted
   */
  public SnapshotTTLExpiredException(SnapshotDescription snapshotDescription) {
    super("TTL for snapshot '" + snapshotDescription.getName() + "' has already expired.",
      snapshotDescription);
  }
}
