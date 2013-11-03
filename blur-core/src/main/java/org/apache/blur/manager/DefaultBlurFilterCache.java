package org.apache.blur.manager;

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
import org.apache.blur.BlurConfiguration;
import org.apache.blur.manager.writer.BlurIndex;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Filter;

/**
 * This implementation on {@link BlurFilterCache} does nothing and it is the
 * default {@link BlurFilterCache}.
 */
public class DefaultBlurFilterCache extends BlurFilterCache {

  public DefaultBlurFilterCache(BlurConfiguration configuration) {
    super(configuration);
  }

  @Override
  public Filter fetchPreFilter(String table, String filterStr) {
    return null;
  }

  @Override
  public Filter fetchPostFilter(String table, String filterStr) {
    return null;
  }

  @Override
  public Filter storePreFilter(String table, String filterStr, Filter filter, FilterParser filterParser)
      throws ParseException {
    return filter;
  }

  @Override
  public Filter storePostFilter(String table, String filterStr, Filter filter, FilterParser filterParser)
      throws ParseException {
    return filter;
  }

  @Override
  public void closing(String table, String shard, BlurIndex index) {

  }

  @Override
  public void opening(String table, String shard, BlurIndex index) {

  }

}