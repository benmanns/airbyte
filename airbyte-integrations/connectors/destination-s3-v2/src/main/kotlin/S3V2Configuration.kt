/*
 * Copyright (c) 2024 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.s3_v2

import io.airbyte.cdk.load.command.DestinationConfiguration
import io.airbyte.cdk.load.command.DestinationConfigurationFactory
import io.airbyte.cdk.load.command.aws.AWSAccessKeyConfiguration
import io.airbyte.cdk.load.command.aws.AWSAccessKeyConfigurationProvider
import io.airbyte.cdk.load.command.aws.AWSArnRoleConfiguration
import io.airbyte.cdk.load.command.aws.AWSArnRoleConfigurationProvider
import io.airbyte.cdk.load.command.object_storage.ObjectStorageCompressionConfiguration
import io.airbyte.cdk.load.command.object_storage.ObjectStorageCompressionConfigurationProvider
import io.airbyte.cdk.load.command.object_storage.ObjectStorageFormatConfiguration
import io.airbyte.cdk.load.command.object_storage.ObjectStorageFormatConfigurationProvider
import io.airbyte.cdk.load.command.object_storage.ObjectStoragePathConfiguration
import io.airbyte.cdk.load.command.object_storage.ObjectStoragePathConfigurationProvider
import io.airbyte.cdk.load.command.object_storage.ObjectStorageUploadConfiguration
import io.airbyte.cdk.load.command.object_storage.ObjectStorageUploadConfigurationProvider
import io.airbyte.cdk.load.command.s3.S3BucketConfiguration
import io.airbyte.cdk.load.command.s3.S3BucketConfigurationProvider
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.io.OutputStream

data class S3V2Configuration<T : OutputStream>(
    // Client-facing configuration
    override val awsAccessKeyConfiguration: AWSAccessKeyConfiguration,
    override val awsArnRoleConfiguration: AWSArnRoleConfiguration,
    override val s3BucketConfiguration: S3BucketConfiguration,
    override val objectStoragePathConfiguration: ObjectStoragePathConfiguration,
    override val objectStorageFormatConfiguration: ObjectStorageFormatConfiguration,
    override val objectStorageCompressionConfiguration: ObjectStorageCompressionConfiguration<T>,

    // Temporary to expose internal config for tuning
    override val objectStorageUploadConfiguration: ObjectStorageUploadConfiguration,
    override val recordBatchSizeBytes: Long,
    override val maxMessageQueueMemoryUsageRatio: Double = 0.2,
    override val estimatedRecordMemoryOverheadRatio: Double = 1.1,
) :
    DestinationConfiguration(),
    AWSAccessKeyConfigurationProvider,
    AWSArnRoleConfigurationProvider,
    S3BucketConfigurationProvider,
    ObjectStoragePathConfigurationProvider,
    ObjectStorageFormatConfigurationProvider,
    ObjectStorageUploadConfigurationProvider,
    ObjectStorageCompressionConfigurationProvider<T>

@Singleton
class S3V2ConfigurationFactory(
    @Value("\${airbyte.destination.record-batch-size}") private val recordBatchSizeBytes: Long
) : DestinationConfigurationFactory<S3V2Specification, S3V2Configuration<*>> {
    override fun makeWithoutExceptionHandling(pojo: S3V2Specification): S3V2Configuration<*> {
        return S3V2Configuration(
            awsAccessKeyConfiguration = pojo.toAWSAccessKeyConfiguration(),
            awsArnRoleConfiguration = pojo.toAWSArnRoleConfiguration(),
            s3BucketConfiguration = pojo.toS3BucketConfiguration(),
            objectStoragePathConfiguration = pojo.toObjectStoragePathConfiguration(),
            objectStorageFormatConfiguration = pojo.toObjectStorageFormatConfiguration(),
            objectStorageCompressionConfiguration = pojo.toCompressionConfiguration(),
            // Temporary to expose internal config for tuning
            objectStorageUploadConfiguration =
                ObjectStorageUploadConfiguration(
                    pojo.uploadPartSize
                        ?: ObjectStorageUploadConfiguration.DEFAULT_STREAMING_UPLOAD_PART_SIZE,
                    pojo.maxConcurrentUploads
                        ?: ObjectStorageUploadConfiguration.DEFAULT_MAX_NUM_CONCURRENT_UPLOADS
                ),
            recordBatchSizeBytes = recordBatchSizeBytes,
            maxMessageQueueMemoryUsageRatio = pojo.maxMessageQueueMemoryUsageRatio ?: 0.2,
            estimatedRecordMemoryOverheadRatio = pojo.estimatedRecordMemoryOverheadRatio ?: 1.1
        )
    }
}

@Suppress("UNCHECKED_CAST")
@Factory
class S3V2ConfigurationProvider<T : OutputStream>(private val config: DestinationConfiguration) {
    @Singleton
    fun get(): S3V2Configuration<T> {
        return config as S3V2Configuration<T>
    }
}
