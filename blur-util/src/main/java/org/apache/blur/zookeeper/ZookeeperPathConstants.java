package org.apache.blur.zookeeper;

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

public class ZookeeperPathConstants {

  public static String getBasePath() {
    return "/blur";
  }

  public static String getClusterPath(String cluster) {
    return getClustersPath() + "/" + cluster;
  }

  public static String getClustersPath() {
    return getBasePath() + "/clusters";
  }

  public static String getOnlineControllersPath() {
    return getBasePath() + "/online-controller-nodes";
  }

  public static String getControllersPath() {
    return getBasePath() + "/controller-nodes";
  }

  public static String getOnlineShardsPath(String cluster) {
    return getClusterPath(cluster) + "/online-nodes";
  }

  public static String getTablesPath(String cluster) {
    return getClusterPath(cluster) + "/tables";
  }

  public static String getTablePath(String cluster, String table) {
    return getTablesPath(cluster) + "/" + table;
  }

  public static String getSafemodePath(String cluster) {
    return getClusterPath(cluster) + "/safemode";
  }
  
  public static String getShutdownPath(String cluster) {
    return getClusterPath(cluster) + "/shutdown";
  }

  public static String getRegisteredShardsPath(String cluster) {
    return getClusterPath(cluster) + "/registered-nodes";
  }

  public static String getShardLayoutPath(String cluster) {
    return getClusterPath(cluster) + "/layout";
  }

  public static String getShardLayoutPathLocks(String cluster) {
    return getShardLayoutPath(cluster) + "/locks";
  }

}
