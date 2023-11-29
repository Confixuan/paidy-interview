package forex.services.rates_ingestor

import cats.effect.{ ContextShift, IO, Timer }
import forex.config.RatesIngestorConfig
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.repos.rates.errors
import forex.repos.rates.interpreters.OneFrameDAO
import forex.services.rates_ingestor.errors.Error.PairIsAbsent
import forex.services.rates_ingestor.interpreters.RatesIngestor
import org.mockito.Mockito.when
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{ BeforeAndAfter, EitherValues, PrivateMethodTester }
import org.scalatestplus.mockito.MockitoSugar.mock

import scala.concurrent.duration.DurationInt
import java.time.OffsetDateTime
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

class RateIngestorSpec
    extends AnyWordSpec
    with Matchers
    with PrivateMethodTester
    with EitherValues
    with Eventually
    with BeforeAndAfter {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val cs: ContextShift[IO]         = IO.contextShift(ec)
  implicit val timer: Timer[IO]             = IO.timer(ec)

  "RatesIngestor.refreshCache" should {
    "refresh the cache periodically" in {
      val ts1 = OffsetDateTime.now
      val rate1 =
        Rate(
          pair = Rate.Pair(
            from = Currency.USD,
            to = Currency.JPY
          ),
          price = Price(BigDecimal(1.0)),
          timestamp = Timestamp(ts1)
        )
      val ts2 = OffsetDateTime.now
      val rate2 =
        Rate(
          pair = Rate.Pair(
            from = Currency.USD,
            to = Currency.JPY
          ),
          price = Price(BigDecimal(2.0)),
          timestamp = Timestamp(ts2)
        )
      val mockRatesDAO = mock[OneFrameDAO[IO]]
      val mockFunction = mock[() => Either[errors.Error, Seq[Rate]]]
      when(mockFunction.apply()).thenReturn(
        Right(Seq(rate1)),
        Right(Seq(rate2))
      )
      when(mockRatesDAO.getAllRates).thenReturn(
        IO.delay(mockFunction.apply()),
      )

      val settings = RatesIngestorConfig(
        expireAfter = 2.seconds,
        refreshInterval = 2.seconds
      )
      val ingestor = new RatesIngestor[IO](mockRatesDAO, settings)
      val fiber    = ingestor.refreshCache.start.unsafeRunSync()

      implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 500.millis)
      eventually {
        ingestor.get(Rate.Pair(from = Currency.USD, to = Currency.JPY)).unsafeRunSync().value shouldBe rate1
      }

      eventually {
        ingestor.get(Rate.Pair(from = Currency.USD, to = Currency.JPY)).unsafeRunSync().value shouldBe rate2
      }
      fiber.cancel.unsafeRunSync()
    }

    "continue though one ingest failed" in {
      val ts = OffsetDateTime.now
      val rate =
        Rate(
          pair = Rate.Pair(
            from = Currency.USD,
            to = Currency.JPY
          ),
          price = Price(BigDecimal(1.0)),
          timestamp = Timestamp(ts)
        )
      val mockRatesDAO = mock[OneFrameDAO[IO]]
      val mockFunction = mock[() => Either[errors.Error, Seq[Rate]]]
      when(mockFunction.apply())
        .thenThrow(new RuntimeException())
        .thenReturn(Right(Seq(rate)))
      when(mockRatesDAO.getAllRates).thenReturn(
        IO.delay(mockFunction.apply()),
      )

      val settings = RatesIngestorConfig(
        expireAfter = 2.seconds,
        refreshInterval = 2.seconds
      )
      val ingestor = new RatesIngestor[IO](mockRatesDAO, settings)
      val fiber    = ingestor.refreshCache.start.unsafeRunSync()

      implicit val patienceConfig = PatienceConfig(timeout = 5.seconds, interval = 500.millis)
      eventually {
        ingestor.get(Rate.Pair(from = Currency.USD, to = Currency.JPY)).unsafeRunSync().isLeft shouldBe true
      }

      eventually {
        ingestor.get(Rate.Pair(from = Currency.USD, to = Currency.JPY)).unsafeRunSync().value shouldBe rate
      }
      fiber.cancel.unsafeRunSync()
    }
  }

  "RatesIngestor.get" should {
    val ts = OffsetDateTime.now
    val rate =
      Rate(
        pair = Rate.Pair(
          from = Currency.USD,
          to = Currency.JPY
        ),
        price = Price(BigDecimal(1.0)),
        timestamp = Timestamp(ts)
      )
    val mockRatesDAO = mock[OneFrameDAO[IO]]
    when(mockRatesDAO.getAllRates).thenReturn(IO.pure(Right(Seq(rate))))

    val settings = RatesIngestorConfig(
      expireAfter = 1.minute,
      refreshInterval = 1.minute
    )

    "successfully get the result if the key was cached" in {
      val ingestor = new RatesIngestor[IO](mockRatesDAO, settings)
      val ingest   = PrivateMethod[IO[Unit]](Symbol("ingest"))

      ingestor.invokePrivate(ingest()).unsafeRunSync() shouldBe ()
      ingestor.get(Rate.Pair(from = Currency.USD, to = Currency.JPY)).unsafeRunSync().value shouldBe rate
    }

    "fail to get the result if the key was absent" in {
      val ingestor = new RatesIngestor[IO](mockRatesDAO, settings)
      val ingest   = PrivateMethod[IO[Unit]](Symbol("ingest"))

      ingestor.invokePrivate(ingest()).unsafeRunSync() shouldBe ()
      ingestor
        .get(Rate.Pair(from = Currency.JPY, to = Currency.USD))
        .unsafeRunSync()
        .left
        .value shouldBe a[PairIsAbsent]
    }
  }
}
