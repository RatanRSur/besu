package org.hyperledger.besu.plugin.services.storage.rocksdb;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.unsegmented.RocksDBKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.unsegmented.RocksDBTransaction;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.Transaction;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

public class BeamRocksDBColumnarKeyValueStorage extends RocksDBColumnarKeyValueStorage {
  public BeamRocksDBColumnarKeyValueStorage(
      RocksDBConfiguration rocksDBConfiguration,
      List<SegmentIdentifier> segments,
      MetricsSystem metricsSystem,
      RocksDBMetricsFactory rocksDBMetricsFactory) {
    super(rocksDBConfiguration, segments, metricsSystem, rocksDBMetricsFactory);
  }

  @Override
  public Optional<byte[]> get(ColumnFamilyHandle segment, byte[] key) throws StorageException {
    return super.get(segment, key) // get local result
        .or(
            () -> {
              // get it from the network
              final Optional<byte[]> networkResult = retrieveNode(key);
              // and persist it
              networkResult.ifPresent(
                  value -> {
                    final var transaction = this.startTransaction();
                    transaction.put(segment, key, value);
                    transaction.commit();
                  });
              return networkResult;
            });
  }

  private Optional<byte[]> retrieveNode(byte[] key) {
    vertx().eventBus()
  }
}
