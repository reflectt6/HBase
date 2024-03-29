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
package org.apache.hadoop.hbase.quotas;

import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * With this limiter resources will be refilled only after a fixed interval of time.
 */
@InterfaceAudience.Private
@InterfaceStability.Evolving
public class FixedIntervalRateLimiter extends RateLimiter {
  private long nextRefillTime = -1L;

  @Override
  public long refill(long limit) {
    final long now = EnvironmentEdgeManager.currentTime();
    if (now < nextRefillTime) {
      return 0;
    }
    nextRefillTime = now + super.getTimeUnitInMillis();
    return limit;
  }

  @Override
  public long getWaitInterval(long limit, long available, long amount) {
    if (nextRefillTime == -1) {
      return 0;
    }
    final long now = EnvironmentEdgeManager.currentTime();
    final long refillTime = nextRefillTime;
    long diff = amount - available;
    // We will add limit at next interval. If diff is less than that limit, the wait interval
    // is just time between now and then.
    long nextRefillInterval = refillTime - now;
    if (diff <= limit) {
      return nextRefillInterval;
    }

    // Otherwise, we need to figure out how many refills are needed.
    // There will be one at nextRefillInterval, and then some number of extra refills.
    // Division will round down if not even, so we can just add that to our next interval
    long extraRefillsNecessary = diff / limit;
    // If it's even, subtract one since that will be covered by nextRefillInterval
    if (diff % limit == 0) {
      extraRefillsNecessary--;
    }
    return nextRefillInterval + (extraRefillsNecessary * super.getTimeUnitInMillis());
  }

  // This method is for strictly testing purpose only
  @Override
  public void setNextRefillTime(long nextRefillTime) {
    this.nextRefillTime = nextRefillTime;
  }

  @Override
  public long getNextRefillTime() {
    return this.nextRefillTime;
  }
}
