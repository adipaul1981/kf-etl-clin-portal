package bio.ferlab.fhir.etl.centricTypes

import bio.ferlab.datalake.commons.config.{Configuration, DatasetConf, StorageConf}
import bio.ferlab.datalake.spark3.etl.v2.ETL
import bio.ferlab.datalake.spark3.loader.GenericLoader.read
import bio.ferlab.fhir.etl.common.Utils._
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.{DataFrame, SparkSession}

import java.time.LocalDateTime

class CommonParticipant(batchId: String, loadType: String = "incremental")(implicit configuration: Configuration) extends ETL {

  override val mainDestination: DatasetConf = conf.getDataset("common_participant")
  val filesExtract: Seq[DatasetConf] = conf.sources.filter(c => c.id.contains("normalized"))
  val hpoTermsConf: DatasetConf = conf.getDataset("hpo_terms")
  val inputStorageOutput: StorageConf = conf.getStorage("output")
  val inputHpoTermsStorage: StorageConf = conf.getStorage("hpo_terms")
  val inputMondoTermsStorage: StorageConf = conf.getStorage("mondo_terms")


  override def extract(lastRunDateTime: LocalDateTime = minDateTime,
                       currentRunDateTime: LocalDateTime = LocalDateTime.now())(implicit spark: SparkSession): Map[String, DataFrame] = {
    filesExtract.map(f => f.id -> read(s"${inputStorageOutput.path}${f.path}", "Parquet", Map(), None, None)).toMap
  }

  override def transform(data: Map[String, DataFrame],
                         lastRunDateTime: LocalDateTime = minDateTime,
                         currentRunDateTime: LocalDateTime = LocalDateTime.now())(implicit spark: SparkSession): Map[String, DataFrame] = {
    val patientDF = data("normalized_patient")

    val hpoTermsConf = conf.sources.find(c => c.id == "hpo_terms").getOrElse(throw new RuntimeException("Hpo Terms Conf not found"))
    val mondoTermsConf = conf.sources.find(c => c.id == "mondo_terms").getOrElse(throw new RuntimeException("Mondo Terms Conf not found"))

    val allHpoTerms = read(s"${inputHpoTermsStorage.path}${hpoTermsConf.path}", "Json", Map(), None, None)
    val allMondoTerms = read(s"${inputMondoTermsStorage.path}${mondoTermsConf.path}", "Json", Map(), None, None)

    val transformedParticipant =
      patientDF
        .addStudy(data("normalized_researchstudy"))
        .addBiospecimen(data("normalized_specimen"))
        .addDiagnosisPhenotypes(data("normalized_condition"))(allHpoTerms, allMondoTerms)
    //        .addOutcomes(data("normalized_observation"))
    //TODO add Families

    transformedParticipant.show(false)
    Map("common_participant" -> transformedParticipant)
  }

  override def load(data: Map[String, DataFrame],
                    lastRunDateTime: LocalDateTime = minDateTime,
                    currentRunDateTime: LocalDateTime = LocalDateTime.now())(implicit spark: SparkSession): Map[String, DataFrame] = {
    println(s"COUNT: ${data("common_participant").count()}")
    val dataToLoad = Map("common_participant" -> data("common_participant")
      .repartition(1, col("study_id"))
      .sortWithinPartitions("fhir_id").toDF())
    super.load(dataToLoad)
  }
}
