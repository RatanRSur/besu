package org.hyperledger.besu.plugin.services.storage.rocksdb;

import com.google.common.base.Supplier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageAdapter;

import java.util.List;

public class BeamSyncRocksDbKeyValueStorageFactory extends RocksDBKeyValueStorageFactory {
  BeamSyncRocksDbKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> segments,
      final int defaultVersion,
      final RocksDBMetricsFactory rocksDBMetricsFactory) {
    super(configuration, segments, defaultVersion, rocksDBMetricsFactory);
  }

  public BeamSyncRocksDbKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> segments,
      final RocksDBMetricsFactory rocksDBMetricsFactory) {
    super(configuration, segments, rocksDBMetricsFactory);
  }

  @Override
  public KeyValueStorage create(
      SegmentIdentifier segment, BesuConfiguration commonConfiguration, MetricsSystem metricsSystem)
      throws StorageException {
    if (segment.equals(KeyValueSegmentIdentifier.WORLD_STATE)) {
      return new SegmentedKeyValueStorageAdapter<>(
          segment,
          new BeamRocksDBColumnarKeyValueStorage(
              super.rocksDBConfiguration,
              super.segments,
              metricsSystem,
              super.rocksDBMetricsFactory));
    }
    return super.create(segment, commonConfiguration, metricsSystem);
  }
}
