package forex.services.rates
import cats.effect.{ ContextShift, IO, Resource, Timer }
import forex.DockerOneFrameService
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import forex.config.{ HttpConfig, OneFrameConfig, RatesIngestorConfig }
import forex.domain.{ Currency, Rate }
import forex.repos.rates.interpreters.OneFrameDAO
import forex.services.rates_ingestor.interpreters.RatesIngestor
import org.scalatest.EitherValues
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

class RatesIngestorItSpec
    extends AnyWordSpec
    with Matchers
    with DockerOneFrameService
    with Eventually
    with EitherValues {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val cs: ContextShift[IO]         = IO.contextShift(ec)
  implicit val timer: Timer[IO]             = IO.timer(ec)
  val httpClient: Resource[IO, Client[IO]]  = BlazeClientBuilder[IO](ec).resource
  val daoSettings: OneFrameConfig = OneFrameConfig(
    token = "10dc303535874aeccc86a8251e6992f5",
    http = HttpConfig(
      host = "localhost",
      port = OneFramePort,
      timeout = 10.seconds
    )
  )
  val ingestorSettings = RatesIngestorConfig(
    expireAfter = 3.seconds,
    refreshInterval = 3.seconds
  )
  val dao      = new OneFrameDAO(httpClient, daoSettings)
  val ingestor = new RatesIngestor(dao, ingestorSettings)
  "RatesIngestor.refreshCache" should {
    "refresh the cache periodically" in {
      val fiber = ingestor.refreshCache.start.unsafeRunSync()

      var firstResult: Rate = null

      implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 500.millis)
      eventually {
        val result =
          ingestor.get(Rate.Pair(Currency.USD, Currency.JPY)).unsafeRunSync()
        result.isRight shouldBe true
        firstResult = result.value
      }

      // result got refreshed
      eventually {
        val result =
          ingestor.get(Rate.Pair(Currency.USD, Currency.JPY)).unsafeRunSync()
        result.isRight shouldBe true
        result should not be firstResult
      }

      fiber.cancel.unsafeRunSync()
    }
  }
}
