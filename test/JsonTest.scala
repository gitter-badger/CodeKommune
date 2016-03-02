package com.sksamuel.elastic4s

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.analyzers.KeywordAnalyzer
import com.sksamuel.elastic4s.mappings.FieldType.StringType
import com.sksamuel.elastic4s.testkit.ElasticSugar
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram
import org.elasticsearch.search.aggregations.bucket.missing.InternalMissing
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg
import org.elasticsearch.search.aggregations.metrics.cardinality.InternalCardinality
import org.elasticsearch.search.aggregations.metrics.max.InternalMax
import org.elasticsearch.search.aggregations.metrics.min.InternalMin
import org.elasticsearch.search.aggregations.metrics.scripted.InternalScriptedMetric
import org.elasticsearch.search.aggregations.metrics.sum.InternalSum
import org.elasticsearch.search.aggregations.metrics.valuecount.InternalValueCount
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.JavaConverters._

class AggregationsTest extends FreeSpec with Matchers with ElasticSugar {

  client.execute {
    create index "aggregations" mappings {
      mapping("breakingbad") fields (
        "job" typed StringType analyzer KeywordAnalyzer
        )
    }
  }.await

  client.execute(
    bulk(
      index into "aggregations/breakingbad" fields("name" -> "walter white", "job" -> "meth kingpin", "age" -> 50, "actor" -> "bryan"),
      index into "aggregations/breakingbad" fields("name" -> "hank schrader", "job" -> "dea agent", "age" -> 55, "actor" -> "dean"),
      index into "aggregations/breakingbad" fields("name" -> "jesse pinkman", "job" -> "meth sidekick", "age" -> 30),
      index into "aggregations/breakingbad" fields("name" -> "gus fring", "job" -> "meth kingpin", "age" -> 60),
      index into "aggregations/breakingbad" fields("name" -> "steven gomez", "job" -> "dea agent", "age" -> 50),
      index into "aggregations/breakingbad" fields("name" -> "saul goodman", "job" -> "lawyer", "age" -> 55),
      index into "aggregations/breakingbad" fields("name" -> "Huell Babineaux", "job" -> "heavy", "age" -> 43, "actor" -> "lavell"),
      index into "aggregations/breakingbad" fields("name" -> "mike ehrmantraut", "job" -> "heavy", "age" -> 45),
      index into "aggregations/breakingbad" fields("name" -> "lydia rodarte quayle", "job" -> "meth sidekick", "age" -> 40),
      index into "aggregations/breakingbad" fields("name" -> "todd alquist", "job" -> "meth sidekick", "age" -> 26)
    )
  ).await

  refresh("aggregations")
  blockUntilCount(10, "aggregations")

  "terms aggregation" - {
    "should group by field" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation terms "agg1" field "job"
        }
      }.await
      resp.totalHits shouldBe 10
      val agg = resp.aggregations.getAsMap.get("agg1").asInstanceOf[StringTerms]
      agg.getBuckets.size shouldBe 5
      agg.getBucketByKey("meth kingpin").getDocCount shouldBe 2
      agg.getBucketByKey("meth sidekick").getDocCount shouldBe 3
      agg.getBucketByKey("dea agent").getDocCount shouldBe 2
      agg.getBucketByKey("lawyer").getDocCount shouldBe 1
      agg.getBucketByKey("heavy").getDocCount shouldBe 2
    }

    "should only include matching documents in the query" in {
      val resp = client.execute {
        // should match 3 documents
        search in "aggregations/breakingbad" query prefixQuery("name" -> "s") aggregations {
          aggregation terms "agg1" field "job"
        }
      }.await
      resp.totalHits shouldBe 3
      val aggs = resp.aggregations.getAsMap.get("agg1").asInstanceOf[StringTerms]
      aggs.getBuckets.size shouldBe 2
      aggs.getBucketByKey("dea agent").getDocCount shouldBe 2
      aggs.getBucketByKey("lawyer").getDocCount shouldBe 1
    }
  }

  "avg aggregation" - {
    "should average by field" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation avg "agg1" field "age"
        }
      }.await
      resp.totalHits shouldBe 10
      val agg = resp.aggregations.getAsMap.get("agg1").asInstanceOf[InternalAvg]
      agg.getValue shouldBe 45.4
    }
    "should only include matching documents in the query" in {
      val resp = client.execute {
        // should match 3 documents
        search in "aggregations/breakingbad" query prefixQuery("name" -> "g") aggregations {
          aggregation avg "agg1" field "age"
        }
      }.await
      resp.totalHits shouldBe 3
      val agg = resp.aggregations.getAsMap.get("agg1").asInstanceOf[InternalAvg]
      agg.getValue shouldBe 55
    }
  }

  "cardinality aggregation" - {
    "should count distinct values" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation cardinality "agg1" field "job"
        }
      }.await
      resp.totalHits shouldBe 10
      val aggs = resp.aggregations.getAsMap.get("agg1").asInstanceOf[InternalCardinality]
      aggs.getValue shouldBe 5
    }
  }

  "missing aggregation" - {
    "should return documents missing a value" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation missing "agg1" field "actor"
        }
      }.await
      resp.totalHits shouldBe 10
      val aggs = resp.aggregations.getAsMap.get("agg1").asInstanceOf[InternalMissing]
      aggs.getDocCount shouldBe 7
    }
  }

  "max aggregation" - {
    "should count max value for field" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation max "agg1" field "age"
        }
      }.await
      resp.totalHits shouldBe 10
      val aggs = resp.aggregations.getAsMap.get("agg1").asInstanceOf[InternalMax]
      aggs.getValue shouldBe 60
    }
  }

  "min aggregation" - {
    "should count min value for field" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation min "agg1" field "age"
        }
      }.await
      resp.totalHits shouldBe 10
      val aggs = resp.aggregations.getAsMap.get("agg1").asInstanceOf[InternalMin]
      aggs.getValue shouldBe 26
    }
  }

  "sum aggregation" - {
    "should sum values for field" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation sum "agg1" field "age"
        }
      }.await
      resp.totalHits shouldBe 10
      val aggs = resp.aggregations.getAsMap.get("agg1").asInstanceOf[InternalSum]
      aggs.getValue shouldBe 454.0
    }
  }

  "value count aggregation" - {
    "should sum values for field" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation count "agg1" field "age"
        }
      }.await
      resp.totalHits shouldBe 10
      val aggs = resp.aggregations.getAsMap.get("agg1").asInstanceOf[InternalValueCount]
      aggs.getValue shouldBe 10
    }
  }

  "histogram aggregation" - {
    "should create histogram by field" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation histogram "h" field "age" interval 10
        }
      }.await
      resp.totalHits shouldBe 10

      val buckets = resp.aggregations.get[Histogram]("h").getBuckets.asScala
      buckets.size shouldBe 5
      buckets.find(_.getKey == 20).get.getDocCount shouldBe 1
      buckets.find(_.getKey == 30).get.getDocCount shouldBe 1
      buckets.find(_.getKey == 40).get.getDocCount shouldBe 3
      buckets.find(_.getKey == 50).get.getDocCount shouldBe 4
      buckets.find(_.getKey == 60).get.getDocCount shouldBe 1
    }

    "should use offset" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation histogram "h" field "age" interval 10 offset 5
        }
      }.await
      resp.totalHits shouldBe 10

      val buckets = resp.aggregations.get[Histogram]("h").getBuckets.asScala
      buckets.size shouldBe 4
      buckets.find(_.getKey == 25).get.getDocCount shouldBe 2
      buckets.find(_.getKey == 35).get.getDocCount shouldBe 2
      buckets.find(_.getKey == 45).get.getDocCount shouldBe 3
      buckets.find(_.getKey == 55).get.getDocCount shouldBe 3
    }

    "should respect min_doc_count" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation histogram "agg1" field "age" interval 10 minDocCount 2
        }
      }.await
      resp.totalHits shouldBe 10
      val buckets = resp.aggregations.get[Histogram]("agg1").getBuckets.asScala
      buckets.size shouldBe 2
      buckets.find(_.getKey == 40).get.getDocCount shouldBe 3
      buckets.find(_.getKey == 50).get.getDocCount shouldBe 4
    }

    "should respect ordering" in {
      val resp = client.execute {
        search in "aggregations/breakingbad" aggregations {
          aggregation histogram "agg1" field "age" interval 10 order Histogram.Order.COUNT_DESC
        }
      }.await
      resp.totalHits shouldBe 10
      val buckets = resp.aggregations.get[Histogram]("agg1").getBuckets.asScala
      buckets.size shouldBe 5
      buckets.head.getKeyAsString shouldBe "50"
      buckets.tail.head.getKeyAsString shouldBe "40"
    }
  }

}