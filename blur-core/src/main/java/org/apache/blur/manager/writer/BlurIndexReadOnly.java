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
package org.apache.blur.manager.writer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.blur.server.IndexSearcherClosable;
import org.apache.blur.thrift.generated.Row;

public class BlurIndexReadOnly extends BlurIndex {

  private final BlurIndex _blurIndex;

  public BlurIndexReadOnly(BlurIndex blurIndex) {
    _blurIndex = blurIndex;
  }

  public void replaceRow(boolean waitToBeVisible, boolean wal, Row row) throws IOException {
    throw new IOException("Read Only");
  }

  public void deleteRow(boolean waitToBeVisible, boolean wal, String rowId) throws IOException {
    throw new IOException("Read Only");
  }

  public IndexSearcherClosable getIndexReader() throws IOException {
    return _blurIndex.getIndexReader();
  }

  public void close() throws IOException {
    _blurIndex.close();
  }

  public void refresh() throws IOException {
    _blurIndex.refresh();
  }

  public AtomicBoolean isClosed() {
    return _blurIndex.isClosed();
  }

  public void optimize(int numberOfSegmentsPerShard) throws IOException {
    _blurIndex.optimize(numberOfSegmentsPerShard);
  }

  public void createSnapshot(String name) throws IOException {
    _blurIndex.createSnapshot(name);
  }

  public void removeSnapshot(String name) throws IOException {
    _blurIndex.removeSnapshot(name);
  }

  public List<String> getSnapshots() throws IOException {
    return _blurIndex.getSnapshots();
  }

}
