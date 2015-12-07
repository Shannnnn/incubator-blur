package org.apache.blur.store.hdfs;

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

import static org.apache.blur.metrics.MetricsConstants.HDFS;
import static org.apache.blur.metrics.MetricsConstants.ORG_APACHE_BLUR;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.blur.BlurConfiguration;
import org.apache.blur.log.Log;
import org.apache.blur.log.LogFactory;
import org.apache.blur.memory.MemoryLeakDetector;
import org.apache.blur.store.blockcache.LastModified;
import org.apache.blur.store.hdfs_v2.HdfsUtils;
import org.apache.blur.trace.Trace;
import org.apache.blur.trace.Tracer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.lucene.store.BufferedIndexOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.NoLockFactory;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricName;

public class HdfsDirectory extends Directory implements LastModified, HdfsSymlink {

  private static final Log LOG = LogFactory.getLog(HdfsDirectory.class);

  public static final String LNK = ".lnk";
  public static final String TMP = ".tmp";

  private static final String UTF_8 = "UTF-8";
  private static final String HDFS_SCHEMA = "hdfs";

  /**
   * We keep the metrics separate per filesystem.
   */
  protected static Map<URI, MetricsGroup> _metricsGroupMap = new WeakHashMap<URI, MetricsGroup>();

  private static final Timer TIMER;
  private static final BlockingQueue<Closeable> CLOSING_QUEUE = new LinkedBlockingQueue<Closeable>();

  static class FStat {
    FStat(FileStatus fileStatus) {
      this(fileStatus.getModificationTime(), fileStatus.getLen());
    }

    FStat(long lastMod, long length) {
      _lastMod = lastMod;
      _length = length;
    }

    final long _lastMod;
    final long _length;
  }

  static {
    TIMER = new Timer("HdfsDirectory-Timer", true);
    TIMER.schedule(getClosingQueueTimerTask(), TimeUnit.SECONDS.toMillis(3), TimeUnit.SECONDS.toMillis(3));
  }

  protected final Path _path;
  protected final FileSystem _fileSystem;
  protected final MetricsGroup _metricsGroup;
  protected final FStatusCache _fileStatusCache;
  protected final Map<String, Boolean> _symlinkMap = new ConcurrentHashMap<String, Boolean>();
  protected final Map<String, Path> _symlinkPathMap = new ConcurrentHashMap<String, Path>();
  protected final Map<String, Boolean> _copyFileMap = new ConcurrentHashMap<String, Boolean>();
  protected final Map<String, Path> _copyFilePathMap = new ConcurrentHashMap<String, Path>();
  protected final boolean _useCache = true;
  protected final boolean _asyncClosing;
  protected final SequentialReadControl _sequentialReadControl;
  protected final boolean _resourceTracking;

  static class FStatusCache {

    final Map<String, FStat> _cache = new ConcurrentHashMap<String, FStat>();
    final Path _path;
    final FileSystem _fileSystem;
    final Path _newManifest;
    final Path _manifest;
    final WriteLock _writeLock;
    final ReadLock _readLock;
    final Path _newManifestTmp;

    public FStatusCache(FileSystem fileSystem, Path path) {
      _fileSystem = fileSystem;
      _path = path;
      _newManifest = new Path(_path, "file_manifest.new");
      _newManifestTmp = new Path(_path, "file_manifest.tmp");
      _manifest = new Path(_path, "file_manifest");
      ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
      _writeLock = lock.writeLock();
      _readLock = lock.readLock();
    }

    public void putAllFStat(Map<String, FStat> bulk) throws IOException {
      _writeLock.lock();
      try {
        _cache.putAll(bulk);
        syncFileCache();
      } finally {
        _writeLock.unlock();
      }
    }

    public void putFStat(String name, FStat fStat) throws IOException {
      _writeLock.lock();
      try {
        _cache.put(name, fStat);
        syncFileCache();
      } finally {
        _writeLock.unlock();
      }
    }

    public void removeFStat(String name) throws IOException {
      _writeLock.lock();
      try {
        _cache.remove(name);
        syncFileCache();
      } finally {
        _writeLock.unlock();
      }
    }

    public Set<String> getNames() {
      _readLock.lock();
      try {
        return new HashSet<String>(_cache.keySet());
      } finally {
        _readLock.unlock();
      }
    }

    public boolean containsFile(String name) {
      _readLock.lock();
      try {
        return _cache.containsKey(name);
      } finally {
        _readLock.unlock();
      }
    }

    public FStat getFStat(String name) {
      _readLock.lock();
      try {
        return _cache.get(name);
      } finally {
        _readLock.unlock();
      }
    }

    public boolean loadCacheFromManifest() throws IOException {
      // Check file_manifest.new first, if is doesn't check file_manifest, if it
      // doesn't exist can't load cache.
      if (_fileSystem.exists(_newManifest)) {
        loadCacheFromManifest(_newManifest);
        return true;
      } else if (_fileSystem.exists(_manifest)) {
        loadCacheFromManifest(_manifest);
        return true;
      } else {
        return false;
      }
    }

    private void syncFileCache() throws IOException {
      FSDataOutputStream outputStream = _fileSystem.create(_newManifestTmp, true);
      writeFileCache(outputStream);
      outputStream.close();
      _fileSystem.delete(_newManifest, false);
      if (_fileSystem.rename(_newManifestTmp, _newManifest)) {
        _fileSystem.delete(_manifest, false);
        if (_fileSystem.rename(_newManifest, _manifest)) {
          LOG.debug("Manifest sync complete for [{0}]", _manifest);
        } else {
          throw new IOException("Could not rename [" + _newManifest + "] to [" + _manifest + "]");
        }
      } else {
        throw new IOException("Could not rename [" + _newManifestTmp + "] to [" + _newManifest + "]");
      }
    }

    private void writeFileCache(FSDataOutputStream outputStream) throws IOException {
      Set<Entry<String, FStat>> entrySet = _cache.entrySet();
      outputStream.writeInt(_cache.size());
      for (Entry<String, FStat> e : entrySet) {
        String name = e.getKey();
        FStat fstat = e.getValue();
        writeString(outputStream, name);
        outputStream.writeLong(fstat._lastMod);
        outputStream.writeLong(fstat._length);
      }
    }

    private void loadCacheFromManifest(Path manifest) throws IOException {
      FSDataInputStream inputStream = _fileSystem.open(manifest);
      int count = inputStream.readInt();
      for (int i = 0; i < count; i++) {
        String name = readString(inputStream);
        long lastMod = inputStream.readLong();
        long length = inputStream.readLong();
        FStat fstat = new FStat(lastMod, length);
        _cache.put(name, fstat);
      }
      inputStream.close();
    }

    private String readString(FSDataInputStream inputStream) throws IOException {
      int length = inputStream.readInt();
      byte[] buf = new byte[length];
      inputStream.readFully(buf);
      return new String(buf, UTF_8);
    }

    private void writeString(FSDataOutputStream outputStream, String s) throws IOException {
      byte[] bs = s.getBytes(UTF_8);
      outputStream.writeInt(bs.length);
      outputStream.write(bs);
    }

  }

  public HdfsDirectory(Configuration configuration, Path path) throws IOException {
    this(configuration, path, new SequentialReadControl(new BlurConfiguration()));
  }

  public HdfsDirectory(Configuration configuration, Path path, SequentialReadControl sequentialReadControl)
      throws IOException {
    this(configuration, path, sequentialReadControl, false);
  }

  public HdfsDirectory(Configuration configuration, Path path, SequentialReadControl sequentialReadControl,
      boolean resourceTracking) throws IOException {
    _resourceTracking = resourceTracking;
    if (sequentialReadControl == null) {
      _sequentialReadControl = new SequentialReadControl(new BlurConfiguration());
    } else {
      _sequentialReadControl = sequentialReadControl;
    }
    _fileSystem = path.getFileSystem(configuration);
    _path = _fileSystem.makeQualified(path);

    if (_path.toUri().getScheme().equals(HDFS_SCHEMA)) {
      _asyncClosing = true;
    } else {
      _asyncClosing = false;
    }
    _fileSystem.mkdirs(path);
    setLockFactory(NoLockFactory.getNoLockFactory());
    synchronized (_metricsGroupMap) {
      URI uri = _fileSystem.getUri();
      MetricsGroup metricsGroup = _metricsGroupMap.get(uri);
      if (metricsGroup == null) {
        String scope = uri.toString();
        metricsGroup = createNewMetricsGroup(scope);
        _metricsGroupMap.put(uri, metricsGroup);
      }
      _metricsGroup = metricsGroup;
    }

    if (_useCache) {
      _fileStatusCache = new FStatusCache(_fileSystem, _path);
      if (!_fileStatusCache.loadCacheFromManifest()) {
        FileStatus[] listStatus = _fileSystem.listStatus(_path);
        addToCache(listStatus);
      }
    } else {
      _fileStatusCache = null;
    }
  }

  private void addToCache(FileStatus[] listStatus) throws IOException {
    Map<String, FStat> bulk = new HashMap<String, FStat>();
    for (FileStatus fileStatus : listStatus) {
      if (!fileStatus.isDir()) {
        Path p = fileStatus.getPath();
        String name = p.getName();
        long lastMod;
        long length;
        String resolvedName;
        if (name.endsWith(LNK)) {
          resolvedName = getRealFileName(name);
          Path resolvedPath = getPath(resolvedName);
          FileStatus resolvedFileStatus = _fileSystem.getFileStatus(resolvedPath);
          lastMod = resolvedFileStatus.getModificationTime();
        } else {
          resolvedName = name;
          lastMod = fileStatus.getModificationTime();
        }
        length = length(resolvedName);
        bulk.put(resolvedName, new FStat(lastMod, length));
      }
    }
    LOG.info("Bulk cache update for [{0}] complete", _path);
    _fileStatusCache.putAllFStat(bulk);
  }

  private static TimerTask getClosingQueueTimerTask() {
    return new TimerTask() {
      @Override
      public void run() {
        try {
          while (true) {
            Closeable closeable = CLOSING_QUEUE.poll();
            if (closeable == null) {
              return;
            }
            LOG.info("Closing [{0}] [{1}]", System.identityHashCode(closeable), closeable);
            org.apache.hadoop.io.IOUtils.cleanup(LOG, closeable);
          }
        } catch (Throwable t) {
          LOG.error("Unknown error.", t);
        }
      }
    };
  }

  public static String getRealFileName(String name) {
    if (name.endsWith(LNK)) {
      int lastIndexOf = name.lastIndexOf(LNK);
      return name.substring(0, lastIndexOf);
    }
    return name;
  }

  protected MetricsGroup createNewMetricsGroup(String scope) {
    MetricName readRandomAccessName = new MetricName(ORG_APACHE_BLUR, HDFS, "Read Random Latency in \u00B5s", scope);
    MetricName readStreamAccessName = new MetricName(ORG_APACHE_BLUR, HDFS, "Read Stream Latency in \u00B5s", scope);
    MetricName writeAcccessName = new MetricName(ORG_APACHE_BLUR, HDFS, "Write Latency in \u00B5s", scope);
    MetricName readRandomThroughputName = new MetricName(ORG_APACHE_BLUR, HDFS, "Read Random Throughput", scope);
    MetricName readStreamThroughputName = new MetricName(ORG_APACHE_BLUR, HDFS, "Read Stream Throughput", scope);
    MetricName readSeekName = new MetricName(ORG_APACHE_BLUR, HDFS, "Read Stream Seeks", scope);
    MetricName writeThroughputName = new MetricName(ORG_APACHE_BLUR, HDFS, "Write Throughput", scope);
    MetricName totalHdfsBlocks = new MetricName(ORG_APACHE_BLUR, HDFS, "Hdfs Blocks Total", scope);
    MetricName localHdfsBlocks = new MetricName(ORG_APACHE_BLUR, HDFS, "Hdfs Blocks Local", scope);

    Histogram readRandomAccess = Metrics.newHistogram(readRandomAccessName);
    Histogram readStreamAccess = Metrics.newHistogram(readStreamAccessName);
    Histogram writeAccess = Metrics.newHistogram(writeAcccessName);
    Meter readRandomThroughput = Metrics.newMeter(readRandomThroughputName, "Read Random Bytes", TimeUnit.SECONDS);
    Meter readStreamThroughput = Metrics.newMeter(readStreamThroughputName, "Read Stream Bytes", TimeUnit.SECONDS);
    Meter readStreamSeek = Metrics.newMeter(readSeekName, "Read Stream Seeks", TimeUnit.SECONDS);
    Meter writeThroughput = Metrics.newMeter(writeThroughputName, "Write Bytes", TimeUnit.SECONDS);
    Counter totalHdfsBlock = Metrics.newCounter(totalHdfsBlocks);
    Counter localHdfsBlock = Metrics.newCounter(localHdfsBlocks);

    return new MetricsGroup(readRandomAccess, readStreamAccess, writeAccess, readRandomThroughput,
        readStreamThroughput, readStreamSeek, writeThroughput, totalHdfsBlock, localHdfsBlock);
  }

  @Override
  public String toString() {
    return "HdfsDirectory path=[" + getPath() + "]";
  }

  @Override
  public IndexOutput createOutput(final String name, IOContext context) throws IOException {
    LOG.debug("createOutput [{0}] [{1}] [{2}]", name, context, getPath());
    if (fileExists(name)) {
      deleteFile(name);
    }
    if (_useCache) {
      _fileStatusCache.putFStat(name, new FStat(System.currentTimeMillis(), 0L));
    }
    final FSDataOutputStream outputStream = openForOutput(name);
    trackObject(outputStream, "Outputstream", name, _path);
    return new BufferedIndexOutput() {

      @Override
      public long length() throws IOException {
        return outputStream.getPos();
      }

      @Override
      protected void flushBuffer(byte[] b, int offset, int len) throws IOException {
        long start = System.nanoTime();
        outputStream.write(b, offset, len);
        long end = System.nanoTime();
        _metricsGroup.writeAccess.update((end - start) / 1000);
        _metricsGroup.writeThroughput.mark(len);
      }

      @Override
      public void close() throws IOException {
        super.close();
        long length = outputStream.getPos();
        if (_useCache) {
          _fileStatusCache.putFStat(name, new FStat(System.currentTimeMillis(), length));
        }
        // This exists because HDFS is so slow to close files. There are
        // built-in sleeps during the close call.
        if (_asyncClosing && _useCache) {
          outputStream.sync();
          CLOSING_QUEUE.add(outputStream);
        } else {
          outputStream.close();
        }
      }

      @Override
      public void seek(long pos) throws IOException {
        throw new IOException("seeks not allowed on IndexOutputs.");
      }
    };
  }

  protected <T> void trackObject(T t, String message, Object... args) {
    if (_resourceTracking) {
      MemoryLeakDetector.record(t, message, args);
    }
  }

  protected FSDataOutputStream openForOutput(String name) throws IOException {
    Path path = getPath(name);
    Tracer trace = Trace.trace("filesystem - create", Trace.param("path", path));
    try {
      return _fileSystem.create(path);
    } finally {
      trace.done();
    }
  }

  @Override
  public IndexInput openInput(String name, IOContext context) throws IOException {
    LOG.debug("openInput [{0}] [{1}] [{2}]", name, context, getPath());
    if (!fileExists(name)) {
      throw new FileNotFoundException("File [" + name + "] not found.");
    }
    long fileLength = fileLength(name);
    Path path = getPath(name);
    FSInputFileHandle fsInputFileHandle = new FSInputFileHandle(_fileSystem, path, fileLength, name, _resourceTracking,
        _asyncClosing && _useCache);
    HdfsIndexInput input = new HdfsIndexInput(this, fsInputFileHandle, fileLength, _metricsGroup, name,
        _sequentialReadControl.clone());
    return input;
  }

  @Override
  public String[] listAll() throws IOException {
    LOG.debug("listAll [{0}]", getPath());

    if (_useCache) {
      Set<String> names = _fileStatusCache.getNames();
      return names.toArray(new String[names.size()]);
    }

    Tracer trace = Trace.trace("filesystem - list", Trace.param("path", getPath()));
    try {
      FileStatus[] files = _fileSystem.listStatus(getPath(), new PathFilter() {
        @Override
        public boolean accept(Path path) {
          try {
            return _fileSystem.isFile(path);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
      SortedSet<String> result = new TreeSet<String>();
      for (int i = 0; i < files.length; i++) {
        String name = files[i].getPath().getName();
        if (name.endsWith(LNK)) {
          result.add(getRealFileName(name));
        } else {
          result.add(name);
        }
      }
      return result.toArray(new String[result.size()]);
    } finally {
      trace.done();
    }
  }

  @Override
  public boolean fileExists(String name) throws IOException {
    LOG.debug("fileExists [{0}] [{1}]", name, getPath());
    if (_useCache) {
      return _fileStatusCache.containsFile(name);
    }
    return exists(name);
  }

  protected boolean exists(String name) throws IOException {
    Path path = getPath(name);
    Tracer trace = Trace.trace("filesystem - exists", Trace.param("path", path));
    try {
      return _fileSystem.exists(path);
    } finally {
      trace.done();
    }
  }

  @Override
  public void deleteFile(String name) throws IOException {
    LOG.debug("deleteFile [{0}] [{1}]", name, getPath());
    if (fileExists(name)) {
      if (_useCache) {
        _fileStatusCache.removeFStat(name);
      }
      delete(name);
    } else {
      throw new FileNotFoundException("File [" + name + "] not found");
    }
  }

  protected void delete(String name) throws IOException {
    Tracer trace = Trace.trace("filesystem - delete", Trace.param("path", getPath(name)));
    if (_useCache) {
      _symlinkMap.remove(name);
      _symlinkPathMap.remove(name);
    }
    try {
      Path symlinkPath = getPathOrSymlinkForDelete(name);
      _fileSystem.delete(symlinkPath, true);
    } finally {
      trace.done();
    }
  }

  @Override
  public long fileLength(String name) throws IOException {
    LOG.debug("fileLength [{0}] [{1}]", name, getPath());
    if (_useCache) {
      FStat fStat = _fileStatusCache.getFStat(name);
      if (fStat == null) {
        throw new FileNotFoundException(name);
      }
      return fStat._length;
    }
    return length(name);
  }

  protected long length(String name) throws IOException {
    Path path = getPath(name);
    Tracer trace = Trace.trace("filesystem - length", Trace.param("path", path));
    try {
      if (_fileSystem instanceof DistributedFileSystem) {
        FSDataInputStream in = _fileSystem.open(path);
        try {
          return HdfsUtils.getFileLength(_fileSystem, path, in);
        } finally {
          in.close();
        }
      } else {
        return _fileSystem.getFileStatus(path).getLen();
      }
    } finally {
      trace.done();
    }
  }

  @Override
  public void sync(Collection<String> names) throws IOException {

  }

  @Override
  public void close() throws IOException {
    TIMER.purge();
  }

  public Path getPath() {
    return _path;
  }

  protected Path getPath(String name) throws IOException {
    if (isSymlink(name)) {
      return getRealFilePathFromSymlink(name);
    } else {
      return new Path(_path, name);
    }
  }

  protected Path getRealFilePathFromCopyFileList(FileStatus[] listStatus) throws IOException {
    if (listStatus == null || listStatus.length == 0) {
      throw new IOException("Copy file list empty.");
    }
    Arrays.sort(listStatus);
    return listStatus[listStatus.length - 1].getPath();
  }

  protected Path getPathOrSymlinkForDelete(String name) throws IOException {
    if (isSymlink(name)) {
      return new Path(_path, name + LNK);
    }
    return new Path(_path, name);
  }

  public Path getRealFilePathFromSymlink(String name) throws IOException {
    // need to cache
    if (_useCache) {
      Path path = _symlinkPathMap.get(name);
      if (path != null) {
        return path;
      }
    }
    Tracer trace = Trace.trace("filesystem - getRealFilePathFromSymlink", Trace.param("name", name));
    try {
      Path linkPath = new Path(_path, name + LNK);
      Path path = readRealPathDataFromSymlinkPath(_fileSystem, linkPath);
      if (_useCache) {
        _symlinkPathMap.put(name, path);
      }
      return path;
    } finally {
      trace.done();
    }
  }

  public static Path readRealPathDataFromSymlinkPath(FileSystem fileSystem, Path linkPath) throws IOException,
      UnsupportedEncodingException {
    FileStatus fileStatus = fileSystem.getFileStatus(linkPath);
    FSDataInputStream inputStream = fileSystem.open(linkPath);
    byte[] buf = new byte[(int) fileStatus.getLen()];
    inputStream.readFully(buf);
    inputStream.close();
    Path path = new Path(new String(buf, UTF_8));
    return path;
  }

  protected boolean isSymlink(String name) throws IOException {
    if (_useCache) {
      Boolean b = _symlinkMap.get(name);
      if (b != null) {
        return b;
      }
    }
    Tracer trace = Trace.trace("filesystem - isSymlink", Trace.param("name", name));
    try {
      boolean exists = _fileSystem.exists(new Path(_path, name + LNK));
      if (_useCache) {
        _symlinkMap.put(name, exists);
      }
      return exists;
    } finally {
      trace.done();
    }
  }

  public long getFileModified(String name) throws IOException {
    if (_useCache) {
      FStat fStat = _fileStatusCache.getFStat(name);
      if (fStat == null) {
        throw new FileNotFoundException("File [" + name + "] not found");
      }
      return fStat._lastMod;
    }
    return fileModified(name);
  }

  protected long fileModified(String name) throws IOException {
    Path path = getPath(name);
    Tracer trace = Trace.trace("filesystem - fileModified", Trace.param("path", path));
    try {
      FileStatus fileStatus = _fileSystem.getFileStatus(path);
      if (_useCache) {
        _fileStatusCache.putFStat(name, new FStat(fileStatus));
      }
      return fileStatus.getModificationTime();
    } finally {
      trace.done();
    }
  }

  @Override
  public void copy(Directory to, String src, String dest, IOContext context) throws IOException {
    if (to instanceof DirectoryDecorator) {
      // Unwrap original directory
      copy(((DirectoryDecorator) to).getOriginalDirectory(), src, dest, context);
      return;
    } else if (to instanceof HdfsSymlink) {
      // Attempt to create a symlink and return.
      if (createSymLink(((HdfsSymlink) to).getSymlinkDirectory(), src, dest)) {
        return;
      }
    }
    // if all else fails, just copy the file.
    super.copy(to, src, dest, context);
  }

  protected boolean createSymLink(HdfsDirectory to, String src, String dest) throws IOException {
    Path srcPath = getPath(src);
    Path destDir = to.getPath();
    LOG.info("Creating symlink with name [{0}] to [{1}]", dest, srcPath);
    FSDataOutputStream outputStream = _fileSystem.create(getSymPath(destDir, dest));
    outputStream.write(srcPath.toString().getBytes(UTF_8));
    outputStream.close();
    if (_useCache) {
      to._fileStatusCache.putFStat(dest, _fileStatusCache.getFStat(src));
    }
    return true;
  }

  protected Path getSymPath(Path destDir, String destFilename) {
    return new Path(destDir, destFilename + LNK);
  }

  @Override
  public HdfsDirectory getSymlinkDirectory() {
    return this;
  }

}
