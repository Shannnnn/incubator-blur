package org.apache.blur.thrift;

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
import static org.apache.blur.utils.BlurConstants.BLUR_SHARD_CACHE_MAX_QUERYCACHE_ELEMENTS;
import static org.apache.blur.utils.BlurConstants.BLUR_SHARD_CACHE_MAX_TIMETOLIVE;
import static org.apache.blur.utils.BlurConstants.BLUR_SHARD_DATA_FETCH_THREAD_COUNT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;

import org.apache.blur.concurrent.Executors;
import org.apache.blur.log.Log;
import org.apache.blur.log.LogFactory;
import org.apache.blur.manager.BlurQueryChecker;
import org.apache.blur.manager.IndexManager;
import org.apache.blur.manager.IndexServer;
import org.apache.blur.manager.results.BlurResultIterable;
import org.apache.blur.manager.writer.BlurIndex;
import org.apache.blur.server.ShardServerContext;
import org.apache.blur.server.platform.CommandException;
import org.apache.blur.server.platform.CommandShardServer;
import org.apache.blur.thirdparty.thrift_0_9_0.TException;
import org.apache.blur.thrift.generated.Blur.Iface;
import org.apache.blur.thrift.generated.BlurCommandRequest;
import org.apache.blur.thrift.generated.BlurCommandResponse;
import org.apache.blur.thrift.generated.BlurException;
import org.apache.blur.thrift.generated.BlurQuery;
import org.apache.blur.thrift.generated.BlurQueryStatus;
import org.apache.blur.thrift.generated.BlurResults;
import org.apache.blur.thrift.generated.FetchResult;
import org.apache.blur.thrift.generated.HighlightOptions;
import org.apache.blur.thrift.generated.Query;
import org.apache.blur.thrift.generated.RowMutation;
import org.apache.blur.thrift.generated.Schema;
import org.apache.blur.thrift.generated.Selector;
import org.apache.blur.thrift.generated.ShardState;
import org.apache.blur.thrift.generated.Status;
import org.apache.blur.thrift.generated.TableStats;
import org.apache.blur.thrift.generated.User;
import org.apache.blur.utils.BlurConstants;
import org.apache.blur.utils.BlurUtil;
import org.apache.blur.utils.QueryCache;
import org.apache.blur.utils.QueryCacheEntry;
import org.apache.blur.utils.QueryCacheKey;

public class BlurShardServer extends TableAdmin implements Iface {

  private static final Log LOG = LogFactory.getLog(BlurShardServer.class);
  private static final boolean ENABLE_CACHE = false;
  private IndexManager _indexManager;
  private IndexServer _indexServer;
  private boolean _closed;
  private long _maxTimeToLive = TimeUnit.MINUTES.toMillis(1);
  private int _maxQueryCacheElements = 128;
  private QueryCache _queryCache;
  private BlurQueryChecker _queryChecker;
  private ExecutorService _dataFetch;
  private String _cluster = BlurConstants.BLUR_CLUSTER;
  private int _dataFetchThreadCount = 32;
  private CommandShardServer _commandShardServer;

  public void init() throws BlurException {
    _queryCache = new QueryCache("shard-cache", _maxQueryCacheElements, _maxTimeToLive);
    _dataFetch = Executors.newThreadPool("data-fetch-", _dataFetchThreadCount);

    if (_configuration == null) {
      throw new BException("Configuration must be set before initialization.");
    }
    _cluster = _configuration.get(BlurConstants.BLUR_CLUSTER_NAME, BlurConstants.BLUR_CLUSTER);
    _dataFetchThreadCount = _configuration.getInt(BLUR_SHARD_DATA_FETCH_THREAD_COUNT, 8);
    _maxQueryCacheElements = _configuration.getInt(BLUR_SHARD_CACHE_MAX_QUERYCACHE_ELEMENTS, 128);
    _maxTimeToLive = _configuration.getLong(BLUR_SHARD_CACHE_MAX_TIMETOLIVE, TimeUnit.MINUTES.toMillis(1));
  }

  @Override
  public BlurResults query(String table, BlurQuery blurQuery) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      resetSearchers();
      _queryChecker.checkQuery(blurQuery);
      checkSelectorFetchSize(blurQuery.getSelector());
      BlurQuery original = new BlurQuery(blurQuery);
      Selector selector = original.getSelector();
      if (selector != null) {
        HighlightOptions highlightOptions = selector.getHighlightOptions();
        if (highlightOptions != null && highlightOptions.getQuery() == null) {
          highlightOptions.setQuery(blurQuery.getQuery());
        }
      }

      // Note: Querying the Shard Server directly if query.startTime == 0
      BlurUtil.setStartTime(blurQuery);
      if (ENABLE_CACHE) {
        if (blurQuery.useCacheIfPresent && selector == null) {
          // Selector has to be null because we might cache data if it's not.
          LOG.debug("Using cache for query [{0}] on table [{1}].", blurQuery, table);
          QueryCacheKey key = QueryCache.getNormalizedBlurQueryKey(table, blurQuery);
          QueryCacheEntry queryCacheEntry = _queryCache.get(key);
          if (_queryCache.isValid(queryCacheEntry, _indexServer.getShardListCurrentServerOnly(table))) {
            LOG.debug("Cache hit for query [{0}] on table [{1}].", blurQuery, table);
            return queryCacheEntry.getBlurResults(blurQuery);
          } else {
            _queryCache.remove(key);
          }
        }
      }
      BlurUtil.setStartTime(original);
      BlurResultIterable hitsIterable = null;
      try {
        AtomicLongArray facetCounts = BlurUtil.getAtomicLongArraySameLengthAsList(blurQuery.facets);
        hitsIterable = _indexManager.query(table, blurQuery, facetCounts);
        // Data will be fetch by IndexManager if selector is provided.
        // This should only happen if the Shard server is accessed directly.
        BlurResults blurResults = BlurUtil.convertToHits(hitsIterable, blurQuery, facetCounts, null, null, this, table);
        if (selector != null) {
          return blurResults;
        }
        if (ENABLE_CACHE) {
          return _queryCache.cache(table, original, blurResults);
        }
        return blurResults;
      } catch (BlurException e) {
        throw e;
      } catch (Exception e) {
        LOG.error("Unknown error during search of [table={0},searchQuery={1}]", e, table, blurQuery);
        throw new BException(e.getMessage(), e);
      } finally {
        if (hitsIterable != null) {
          hitsIterable.close();
        }
      }
    } catch (Exception e) {
      LOG.error("Unknown error during search of [table={0},searchQuery={1}]", e, table, blurQuery);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public String parseQuery(String table, Query simpleQuery) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      resetSearchers();
      return _indexManager.parseQuery(table, simpleQuery);
    } catch (Exception e) {
      LOG.error("Unknown error during parsing of [table={0},simpleQuery={1}]", e, table, simpleQuery);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public FetchResult fetchRow(String table, Selector selector) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      checkSelectorFetchSize(selector);
      FetchResult fetchResult = new FetchResult();
      _indexManager.fetchRow(table, selector, fetchResult);
      return fetchResult;
    } catch (Exception e) {
      LOG.error("Unknown error while trying to get fetch row [table={0},selector={1}]", e, table, selector);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public List<FetchResult> fetchRowBatch(String table, List<Selector> selectors) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      for (Selector selector : selectors) {
        checkSelectorFetchSize(selector);
      }
      return _indexManager.fetchRowBatch(table, selectors);
    } catch (Exception e) {
      LOG.error("Unknown error while trying to get fetch row [table={0},selector={1}]", e, table, selectors);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public void cancelQuery(String table, String uuid) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      resetSearchers();
      _indexManager.cancelQuery(table, uuid);
    } catch (Exception e) {
      LOG.error("Unknown error while trying to cancel search [uuid={0}]", e, uuid);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  private void resetSearchers() {
    ShardServerContext.resetSearchers();
  }

  @Override
  public TableStats tableStats(String table) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      resetSearchers();
      TableStats tableStats = new TableStats();
      tableStats.tableName = table;
      tableStats.recordCount = _indexServer.getRecordCount(table);
      tableStats.rowCount = _indexServer.getRowCount(table);
      tableStats.bytes = _indexServer.getTableSize(table);
      return tableStats;
    } catch (Exception e) {
      LOG.error("Unknown error while trying to get table stats [table={0}]", e, table);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  public synchronized void close() {
    if (!_closed) {
      _closed = true;
      _indexManager.close();
      _dataFetch.shutdownNow();
    }
  }

  @Override
  public Map<String, String> shardServerLayout(String table) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      resetSearchers();
      Map<String, BlurIndex> blurIndexes = _indexServer.getIndexes(table);
      Map<String, String> result = new TreeMap<String, String>();
      String nodeName = _indexServer.getNodeName();
      for (String shard : blurIndexes.keySet()) {
        result.put(shard, nodeName);
      }
      return result;
    } catch (Exception e) {
      LOG.error("Unknown error while trying to getting shardServerLayout for table [" + table + "]", e);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public Map<String, Map<String, ShardState>> shardServerLayoutState(String table) throws BlurException, TException {
    try {
      resetSearchers();
      Map<String, Map<String, ShardState>> result = new TreeMap<String, Map<String, ShardState>>();
      String nodeName = _indexServer.getNodeName();
      Map<String, ShardState> stateMap = _indexServer.getShardState(table);
      for (Entry<String, ShardState> entry : stateMap.entrySet()) {
        result.put(entry.getKey(), newMap(nodeName, entry.getValue()));
      }
      return result;
    } catch (Exception e) {
      LOG.error("Unknown error while trying to getting shardServerLayoutState for table [" + table + "]", e);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  private Map<String, ShardState> newMap(String nodeName, ShardState state) {
    Map<String, ShardState> map = new HashMap<String, ShardState>();
    map.put(nodeName, state);
    return map;
  }

  @Override
  public long recordFrequency(String table, String columnFamily, String columnName, String value) throws BlurException,
      TException {
    try {
      checkTable(_cluster, table);
      resetSearchers();
      return _indexManager.recordFrequency(table, columnFamily, columnName, value);
    } catch (Exception e) {
      LOG.error(
          "Unknown error while trying to get record frequency for [table={0},columnFamily={1},columnName={2},value={3}]",
          e, table, columnFamily, columnName, value);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public Schema schema(String table) throws BlurException, TException {
    try {
      resetSearchers();
      return super.schema(table);
    } catch (Exception e) {
      LOG.error("Unknown error while trying to get schema for [table={0}]", e, table);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public List<String> terms(String table, String columnFamily, String columnName, String startWith, short size)
      throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      resetSearchers();
      return _indexManager.terms(table, columnFamily, columnName, startWith, size);
    } catch (Exception e) {
      LOG.error(
          "Unknown error while trying to get terms list for [table={0},columnFamily={1},columnName={2},startWith={3},size={4}]",
          e, table, columnFamily, columnName, startWith, size);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public void mutate(RowMutation mutation) throws BlurException, TException {
    try {
      checkTable(_cluster, mutation.table);
      checkForUpdates(_cluster, mutation.table);
      resetSearchers();
      MutationHelper.validateMutation(mutation);
      _indexManager.mutate(mutation);
    } catch (Exception e) {
      LOG.error("Unknown error during processing of [mutation={0}]", e, mutation);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public void enqueueMutate(RowMutation mutation) throws BlurException, TException {
    try {
      checkTable(_cluster, mutation.table);
      checkForUpdates(_cluster, mutation.table);
      resetSearchers();
      MutationHelper.validateMutation(mutation);
      _indexManager.enqueue(mutation);
    } catch (Exception e) {
      LOG.error("Unknown error during processing of [mutation={0}]", e, mutation);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public void mutateBatch(List<RowMutation> mutations) throws BlurException, TException {
    try {
      resetSearchers();
      for (RowMutation mutation : mutations) {
        checkTable(_cluster, mutation.table);
        checkForUpdates(_cluster, mutation.table);
        MutationHelper.validateMutation(mutation);
      }
      _indexManager.mutate(mutations);
    } catch (Exception e) {
      LOG.error("Unknown error during processing of [mutations={0}]", e, mutations);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public void enqueueMutateBatch(List<RowMutation> mutations) throws BlurException, TException {
    try {
      resetSearchers();
      for (RowMutation mutation : mutations) {
        checkTable(_cluster, mutation.table);
        checkForUpdates(_cluster, mutation.table);
        MutationHelper.validateMutation(mutation);
      }
      _indexManager.enqueue(mutations);
    } catch (Exception e) {
      LOG.error("Unknown error during processing of [mutations={0}]", e, mutations);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  public long getMaxTimeToLive() {
    return _maxTimeToLive;
  }

  public void setMaxTimeToLive(long maxTimeToLive) {
    _maxTimeToLive = maxTimeToLive;
  }

  public int getMaxQueryCacheElements() {
    return _maxQueryCacheElements;
  }

  public void setMaxQueryCacheElements(int maxQueryCacheElements) {
    _maxQueryCacheElements = maxQueryCacheElements;
  }

  public void setQueryChecker(BlurQueryChecker queryChecker) {
    _queryChecker = queryChecker;
  }

  public void setIndexManager(IndexManager indexManager) {
    _indexManager = indexManager;
  }

  public void setIndexServer(IndexServer indexServer) {
    _indexServer = indexServer;
  }

  public void setCommandShardServer(CommandShardServer commandShardServer) {
    _commandShardServer = commandShardServer;
  }

  @Override
  public BlurQueryStatus queryStatusById(String table, String uuid) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      resetSearchers();
      BlurQueryStatus blurQueryStatus;
      blurQueryStatus = _indexManager.queryStatus(table, uuid);
      if (blurQueryStatus == null) {
        blurQueryStatus = new BlurQueryStatus();
        blurQueryStatus.status = Status.NOT_FOUND;
      } else {
        blurQueryStatus.status = Status.FOUND;
      }
      return blurQueryStatus;
    } catch (Exception e) {
      LOG.error("Unknown error while trying to get current query status [table={0},uuid={1}]", e, table, uuid);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public List<String> queryStatusIdList(String table) throws BlurException, TException {
    checkTable(_cluster, table);
    resetSearchers();
    try {
      return _indexManager.queryStatusIdList(table);
    } catch (Exception e) {
      LOG.error("Unknown error while trying to get query status id list [table={0}]", e, table);
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public void optimize(String table, int numberOfSegmentsPerShard) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      resetSearchers();
      _indexManager.optimize(table, numberOfSegmentsPerShard);
    } catch (Exception e) {
      LOG.error("Unknown error while trying to optimize [table={0},numberOfSegmentsPerShard={1}]", e, table,
          numberOfSegmentsPerShard);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public void createSnapshot(final String table, final String name) throws BlurException, TException {
    try {
      checkTable(_cluster, table);
      Map<String, BlurIndex> indexes = _indexServer.getIndexes(table);
      for (Entry<String, BlurIndex> entry : indexes.entrySet()) {
        BlurIndex index = entry.getValue();
        index.createSnapshot(name);
      }
    } catch (Exception e) {
      LOG.error("Unknown error while trying to getting indexes for table [" + table + "]", e);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public void removeSnapshot(final String table, final String name) throws BlurException, TException {
    try {
      Map<String, BlurIndex> indexes = _indexServer.getIndexes(table);
      for (Entry<String, BlurIndex> entry : indexes.entrySet()) {
        BlurIndex index = entry.getValue();
        index.removeSnapshot(name);
      }
    } catch (Exception e) {
      LOG.error("Unknown error while trying to getting indexes for table [" + table + "]", e);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  @Override
  public Map<String, List<String>> listSnapshots(final String table) throws BlurException, TException {
    try {
      Map<String, List<String>> snapshots = new HashMap<String, List<String>>();
      Map<String, BlurIndex> indexes = _indexServer.getIndexes(table);
      for (Entry<String, BlurIndex> entry : indexes.entrySet()) {
        BlurIndex index = entry.getValue();
        List<String> shardSnapshots = index.getSnapshots();
        if (shardSnapshots == null) {
          shardSnapshots = new ArrayList<String>();
        }
        snapshots.put(entry.getKey(), shardSnapshots);
      }
      return snapshots;
    } catch (Exception e) {
      LOG.error("Unknown error while trying to getting indexes for table [" + table + "]", e);
      if (e instanceof BlurException) {
        throw (BlurException) e;
      }
      throw new BException(e.getMessage(), e);
    }
  }

  public int getDataFetchThreadCount() {
    return _dataFetchThreadCount;
  }

  public void setDataFetchThreadCount(int dataFetchThreadCount) {
    _dataFetchThreadCount = dataFetchThreadCount;
  }

  @Override
  public void setUser(User user) throws TException {
    ShardServerContext context = ShardServerContext.getShardServerContext();
    context.setUser(user);
  }

  @Override
  public void startTrace(String rootId, String requestId) throws TException {
    ShardServerContext context = ShardServerContext.getShardServerContext();
    context.setTraceRootId(rootId);
    context.setTraceRequestId(requestId);
  }

  protected boolean inSafeMode(boolean useCache, String table) throws BlurException {
    // Shard server cannot be processing requests if it's in safe mode.
    return false;
  }

  @Override
  public BlurCommandResponse execute(BlurCommandRequest request) throws BlurException, TException {
    List<String> tableList = tableList();
    try {
      return _commandShardServer.execute(new HashSet<String>(tableList), request);
    } catch (IOException e) {
      throw new BException("Unknown error.", e);
    } catch (CommandException e) {
      throw new BException("Unknown error.", e);
    }
  }
}
