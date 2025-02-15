package bio.ferlab.fhir.etl.s3

import bio.ferlab.fhir.etl.config.{AWSConfig, FhirRequest}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.{HeadObjectRequest, NoSuchKeyException, PutObjectRequest}
import software.amazon.awssdk.services.s3.{S3Client, S3Configuration}

import java.io.File
import java.net.URI

object S3Utils {

  def buildS3Client(configuration: AWSConfig): S3Client = {
    S3Client.builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(configuration.accessKey, configuration.secretKey)))
      .endpointOverride(URI.create(configuration.endpoint))
      .serviceConfiguration(S3Configuration.builder()
        .pathStyleAccessEnabled(configuration.pathStyleAccess)
        .build())
      .httpClient(ApacheHttpClient.create())
      .region(Region.of(configuration.region))
      .build()
  }

  def writeFile(bucket: String, key: String, file: File)(implicit s3Client: S3Client): Unit = {
    val objectRequest = PutObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build()
    s3Client.putObject(objectRequest, RequestBody.fromFile(file))
  }

  def exists(bucket: String, key: String)(implicit s3Client: S3Client): Boolean = {
    try {
      s3Client.headObject(HeadObjectRequest.builder
        .bucket(bucket)
        .key(key)
        .build)
      true
    } catch {
      case _: NoSuchKeyException =>
        false
    }
  }

  def buildKey(fhirRequest: FhirRequest): String = {
    s"raw/fhir/${fhirRequest.`type`.toLowerCase()}/study=${fhirRequest.tag}/${fhirRequest.schema}.avro"
  }
}
