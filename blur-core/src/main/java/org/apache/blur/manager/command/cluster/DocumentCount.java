package org.apache.blur.manager.command.cluster;

import java.util.Map;

import org.apache.blur.manager.command.Args;
import org.apache.blur.manager.command.ClusterCommand;
import org.apache.blur.manager.command.CommandContext;
import org.apache.blur.manager.command.Response;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

@SuppressWarnings("serial")
public class DocumentCount extends ClusterCommand {

  @Override
  public String getName() {
    return "docCount";
  }

  @Override
  public Response execute(Args args, CommandContext context) {
    // where the key is the shard in the table
    Map<String, Object> results = context.execute(args, "docCount");
    return Response.createNewResponse(results);
  }

}
