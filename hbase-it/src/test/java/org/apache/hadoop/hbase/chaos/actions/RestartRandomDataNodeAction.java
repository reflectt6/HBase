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
package org.apache.hadoop.hbase.chaos.actions;

import java.io.IOException;
import java.util.Arrays;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.chaos.monkies.PolicyBasedChaosMonkey;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Action that restarts a random datanode.
 */
public class RestartRandomDataNodeAction extends RestartActionBaseAction {
  private static final Logger LOG = LoggerFactory.getLogger(RestartRandomDataNodeAction.class);

  public RestartRandomDataNodeAction(long sleepTime) {
    super(sleepTime);
  }

  @Override
  protected Logger getLogger() {
    return LOG;
  }

  @Override
  public void perform() throws Exception {
    getLogger().info("Performing action: Restart random data node");
    final ServerName server = PolicyBasedChaosMonkey.selectRandomItem(getDataNodes());
    restartDataNode(server, sleepTime);
  }

  private ServerName[] getDataNodes() throws IOException {
    try (final DistributedFileSystem dfs = HdfsActionUtils.createDfs(getConf())) {
      final DFSClient dfsClient = dfs.getClient();
      return Arrays.stream(dfsClient.datanodeReport(HdfsConstants.DatanodeReportType.LIVE))
        .map(dn -> ServerName.valueOf(dn.getHostName(), -1, -1)).toArray(ServerName[]::new);
    }
  }
}
