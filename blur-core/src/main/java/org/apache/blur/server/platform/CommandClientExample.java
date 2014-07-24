/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.blur.server.platform;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.blur.manager.writer.BlurIndex;
import org.apache.blur.thirdparty.thrift_0_9_0.TException;
import org.apache.blur.thrift.BlurClient;
import org.apache.blur.thrift.generated.Blur.Iface;
import org.apache.blur.thrift.generated.BlurException;

public class CommandClientExample {

  public static void main(String[] args) throws BlurException, TException, IOException {
    Iface client = BlurClient.getClient("localhost:40020");
    CommandClient commandClient = new CommandClient(client);

    String str = commandClient.execute("test", new Command<String, String>() {
      @Override
      public Map<TableShardKey, String> processShard(TableShardKey tableShardKey, BlurIndex blurIndex)
          throws IOException {
        Map<TableShardKey, String> result = new HashMap<TableShardKey, String>();
        result.put(tableShardKey, "hi");
        return result;
      }

      @Override
      public String merge(Map<TableShardKey, String> results) throws IOException {
        return "hi";
      }

    });
    
    System.out.println(str);
  }

}
