package org.apache.blur.manager.writer;

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
import static org.apache.blur.utils.BlurConstants.SEP;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.blur.analysis.FieldManager;
import org.apache.blur.log.Log;
import org.apache.blur.log.LogFactory;
import org.apache.blur.server.ShardContext;
import org.apache.blur.server.TableContext;
import org.apache.blur.thrift.generated.Column;
import org.apache.blur.thrift.generated.Record;
import org.apache.blur.thrift.generated.Row;
import org.apache.blur.utils.BlurConstants;
import org.apache.blur.utils.BlurUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.ChecksumException;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.record.Utils;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.BlurIndexWriter;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TrackingIndexWriter;

public class TransactionRecorder extends TimerTask implements Closeable {

  enum TYPE {
    DELETE((byte) 0), ROW((byte) 1);
    private byte b;

    private TYPE(byte b) {
      this.b = b;
    }

    public byte value() {
      return b;
    }

    public static TYPE lookup(byte b) {
      switch (b) {
      case 0:
        return DELETE;
      case 1:
        return ROW;
      default:
        throw new RuntimeException("Type not found [" + b + "]");
      }
    }
  }

  private static final Log LOG = LogFactory.getLog(TransactionRecorder.class);
  private static final FieldType SUPER_FIELD_TYPE;
  static {
    SUPER_FIELD_TYPE = new FieldType(TextField.TYPE_NOT_STORED);
    SUPER_FIELD_TYPE.setOmitNorms(true);
  }
  public static FieldType ID_TYPE;
  static {
    ID_TYPE = new FieldType();
    ID_TYPE.setIndexed(true);
    ID_TYPE.setTokenized(false);
    ID_TYPE.setOmitNorms(true);
    ID_TYPE.setStored(true);
    ID_TYPE.freeze();
  }

  private final AtomicBoolean _running = new AtomicBoolean(true);
  private final AtomicReference<FSDataOutputStream> _outputStream = new AtomicReference<FSDataOutputStream>();
  private final long _timeBetweenSyncsNanos;
  private final AtomicLong _lastSync = new AtomicLong();

  private final Path _walPath;
  private final Configuration _configuration;
  private final FileSystem _fileSystem;
  private final Timer _timer;
  private final String _table;
  private final String _shard;
  private final FieldManager _fieldManager;

  public TransactionRecorder(ShardContext shardContext) throws IOException {
    TableContext tableContext = shardContext.getTableContext();
    _configuration = tableContext.getConfiguration();
    _fieldManager = tableContext.getFieldManager();
    _walPath = shardContext.getWalShardPath();
    _fileSystem = _walPath.getFileSystem(_configuration);
    _timeBetweenSyncsNanos = tableContext.getTimeBetweenWALSyncsNanos();
    _timer = new Timer("wal-sync-[" + tableContext.getTable() + "/" + shardContext.getShard() + "]", true);
    _timer.schedule(this, TimeUnit.NANOSECONDS.toMillis(_timeBetweenSyncsNanos),
        TimeUnit.NANOSECONDS.toMillis(_timeBetweenSyncsNanos));
    _table = tableContext.getTable();
    _shard = shardContext.getShard();
  }

  public void open() throws IOException {
    if (_fileSystem.exists(_walPath)) {
      throw new IOException("WAL path [" + _walPath + "] still exists, replay must have not worked.");
    } else {
      _outputStream.set(_fileSystem.create(_walPath));
    }
    if (_outputStream == null) {
      throw new RuntimeException();
    }
    _lastSync.set(System.nanoTime());
  }

  public void replay(BlurIndexWriter writer) throws IOException {
    if (_fileSystem.exists(_walPath)) {
      FSDataInputStream inputStream = _fileSystem.open(_walPath);
      replay(writer, inputStream);
      inputStream.close();
      commit(writer);
    } else {
      open();
    }
  }

  private void replay(BlurIndexWriter writer, DataInputStream inputStream) throws CorruptIndexException, IOException {
    long updateCount = 0;
    long deleteCount = 0;
    byte[] buffer;
    while ((buffer = readBuffer(inputStream)) != null) {
      DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(buffer));
      TYPE lookup = TYPE.lookup(dataInputStream.readByte());
      switch (lookup) {
      case ROW:
        Row row = readRow(dataInputStream);
        writer.updateDocuments(createRowId(row.id), getDocs(row, _fieldManager));
        updateCount++;
        continue;
      case DELETE:
        String deleteRowId = readString(dataInputStream);
        writer.deleteDocuments(createRowId(deleteRowId));
        deleteCount++;
        continue;
      default:
        LOG.error("Unknown type [{0}]", lookup);
        throw new IOException("Unknown type [" + lookup + "]");
      }
    }
    LOG.info("Rows reclaimed from the WAL [{0}]", updateCount);
    LOG.info("Deletes reclaimed from the WAL [{0}]", deleteCount);
  }

  private byte[] readBuffer(DataInputStream inputStream) {
    try {
      int length = inputStream.readInt();
      byte[] buffer = new byte[length];
      inputStream.readFully(buffer);
      return buffer;
    } catch (ChecksumException e) {
      LOG.warn("End of WAL file found.");
      if (LOG.isDebugEnabled()) {
        LOG.error("End of WAL file found.", e);
      }
      return null;
    } catch (IOException e) {
      if (e instanceof EOFException) {
        LOG.warn("End of WAL file found.");
        if (LOG.isDebugEnabled()) {
          LOG.error("End of WAL file found.", e);
        }
        return null;
      }
    }
    return null;
  }

  private void rollLog() throws IOException {
    LOG.debug("Rolling WAL path [" + _walPath + "]");
    FSDataOutputStream os = _outputStream.get();
    if (os != null) {
      os.close();
    }
    _fileSystem.delete(_walPath, false);
    open();
  }

  public void close() throws IOException {
    synchronized (_running) {
      _running.set(false);
    }
    _timer.purge();
    _timer.cancel();
    _outputStream.get().close();
  }

  private static void writeRow(DataOutputStream outputStream, Row row) throws IOException {
    writeString(outputStream, row.id);
    List<Record> records = row.records;
    int size = records.size();
    outputStream.writeInt(size);
    for (int i = 0; i < size; i++) {
      Record record = records.get(i);
      writeRecord(outputStream, record);
    }
  }

  private static Row readRow(DataInputStream inputStream) throws IOException {
    Row row = new Row();
    row.id = readString(inputStream);
    int size = inputStream.readInt();
    for (int i = 0; i < size; i++) {
      row.addToRecords(readRecord(inputStream));
    }
    return row;
  }

  private static void writeRecord(DataOutputStream outputStream, Record record) throws IOException {
    writeString(outputStream, record.recordId);
    writeString(outputStream, record.family);
    List<Column> columns = record.columns;
    int size = columns.size();
    outputStream.writeInt(size);
    for (int i = 0; i < size; i++) {
      writeColumn(outputStream, columns.get(i));
    }
  }

  private static Record readRecord(DataInputStream inputStream) throws IOException {
    Record record = new Record();
    record.recordId = readString(inputStream);
    record.family = readString(inputStream);
    int size = inputStream.readInt();
    for (int i = 0; i < size; i++) {
      record.addToColumns(readColumn(inputStream));
    }
    return record;
  }

  private static void writeColumn(DataOutputStream outputStream, Column column) throws IOException {
    writeString(outputStream, column.name);
    writeString(outputStream, column.value);
  }

  private static Column readColumn(DataInputStream inputStream) throws IOException {
    Column column = new Column();
    column.name = readString(inputStream);
    column.value = readString(inputStream);
    return column;
  }

  private static void writeDelete(DataOutputStream outputStream, String deleteRowId) throws IOException {
    writeString(outputStream, deleteRowId);
  }

  private static void writeString(DataOutputStream outputStream, String s) throws IOException {
    if (s == null) {
      Utils.writeVInt(outputStream, -1);
      return;
    }
    byte[] bs = s.getBytes();
    Utils.writeVInt(outputStream, bs.length);
    outputStream.write(bs);
  }

  private static String readString(DataInputStream inputStream) throws IOException {
    int length = Utils.readVInt(inputStream);
    if (length == -1) {
      return null;
    }
    byte[] buffer = new byte[length];
    inputStream.readFully(buffer);
    return new String(buffer);
  }

  private void sync(byte[] bs) throws IOException {
    if (bs == null || _outputStream == null) {
      throw new RuntimeException("bs [" + bs + "] outputStream [" + _outputStream + "]");
    }
    synchronized (_running) {
      FSDataOutputStream os = _outputStream.get();
      os.writeInt(bs.length);
      os.write(bs);
      tryToSync(os);
    }
  }

  private void tryToSync() throws IOException {
    synchronized (_running) {
      tryToSync(_outputStream.get());
    }
  }

  private void tryToSync(FSDataOutputStream os) throws IOException {
    if (os == null) {
      return;
    }
    long now = System.nanoTime();
    if (_lastSync.get() + _timeBetweenSyncsNanos < now) {
      os.sync();
      _lastSync.set(now);
    }
  }

  public long replaceRow(boolean wal, Row row, TrackingIndexWriter writer) throws IOException {
    if (wal) {
      synchronized (_running) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(baos);
        outputStream.writeByte(TYPE.ROW.value());
        writeRow(outputStream, row);
        outputStream.close();
        sync(baos.toByteArray());
      }
    }
    Term term = createRowId(row.id);
    List<List<Field>> docs = getDocs(row, _fieldManager);
    return writer.updateDocuments(term, docs);
  }

  public long deleteRow(boolean wal, String rowId, TrackingIndexWriter writer) throws IOException {
    if (wal) {
      synchronized (_running) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(baos);
        outputStream.writeByte(TYPE.DELETE.value());
        writeDelete(outputStream, rowId);
        outputStream.close();
        sync(baos.toByteArray());
      }
    }
    return writer.deleteDocuments(createRowId(rowId));
  }

  public void commit(BlurIndexWriter writer) throws CorruptIndexException, IOException {
    synchronized (_running) {
      long s = System.nanoTime();
      writer.commit();
      long m = System.nanoTime();
      LOG.debug("Commit took [{0} ms] for [{1}/{2}]", (m - s) / 1000000.0, _table, _shard);
      rollLog();
      long e = System.nanoTime();
      LOG.debug("Log roller took [{0} ms] for [{1}/{2}]", (e - m) / 1000000.0, _table, _shard);
    }
  }

  public static List<List<Field>> getDocs(Row row, FieldManager fieldManager) throws IOException {
    List<Record> records = row.records;
    if (records == null) {
      return null;
    }
    int size = records.size();
    if (size == 0) {
      return null;
    }
    final String rowId = row.id;
    List<List<Field>> docs = new ArrayList<List<Field>>(size);
    for (int i = 0; i < size; i++) {
      Record record = records.get(i);
      List<Field> fields = getDoc(fieldManager, rowId, record);
      docs.add(fields);
    }
    List<Field> doc = docs.get(0);
    doc.add(new StringField(BlurConstants.PRIME_DOC, BlurConstants.PRIME_DOC_VALUE, Store.NO));
    return docs;
  }

  public static List<Field> getDoc(FieldManager fieldManager, final String rowId, Record record) throws IOException {
    BlurUtil.validateRowIdAndRecord(rowId, record);
    List<Field> fields = fieldManager.getFields(rowId, record);
    return fields;
  }

  // public static Document convert(String rowId, Record record, Analyzer
  // analyzer) {
  // BlurUtil.validateRowIdAndRecord(rowId, record);
  // Document document = new Document();
  // document.add(new Field(BlurConstants.ROW_ID, rowId, ID_TYPE));
  // document.add(new Field(BlurConstants.RECORD_ID, record.recordId, ID_TYPE));
  // document.add(new Field(BlurConstants.FAMILY, record.family, ID_TYPE));
  // addColumns(document, analyzer, record.family, record.columns);
  // return document;
  // }

  private Term createRowId(String id) {
    return new Term(BlurConstants.ROW_ID, id);
  }

  @Override
  public void run() {
    try {
      if (_running.get()) {
        tryToSync();
      }
    } catch (IOException e) {
      if (_running.get()) {
        if (e.getMessage().equals("DFSOutputStream is closed")) {
          LOG.warn("Trying to sync the outputstrema and the stream has been closed.  This is probably a test and the filesystem has been closed.");
          try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
          } catch (InterruptedException ex) {
            return;
          }
        } else {
          LOG.error("Known error while trying to sync.", e);
        }
      }
    }
  }

  // public static boolean addColumns(Document document, Analyzer analyzer,
  // String columnFamily, Iterable<Column> set) {
  // if (set == null) {
  // return false;
  // }
  // OUTER: for (Column column : set) {
  // String name = column.getName();
  // String value = column.value;
  // if (value == null || name == null) {
  // continue OUTER;
  // }
  // String fieldName = getFieldName(columnFamily, name);
  // FieldType fieldType = analyzer.getFieldType(fieldName);
  // Field field = analyzer.getField(fieldName, value, fieldType);
  // document.add(field);
  //
  // if (analyzer.isFullTextField(fieldName)) {
  // document.add(new Field(SUPER, value, SUPER_FIELD_TYPE));
  // }
  // Set<String> subFieldNames = analyzer.getSubIndexNames(fieldName);
  // if (subFieldNames != null) {
  // for (String subFieldName : subFieldNames) {
  // FieldType subFieldType = analyzer.getFieldType(subFieldName);
  // document.add(analyzer.getField(subFieldName, value, subFieldType));
  // }
  // }
  // }
  // return true;
  // }

  public static String getFieldName(String columnFamily, String name) {
    return columnFamily + SEP + name;
  }

}