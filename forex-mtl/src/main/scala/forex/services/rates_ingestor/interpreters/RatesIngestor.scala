package forex.services.rates_ingestor.interpreters

import cats.MonadError
import cats.effect.Timer
import com.github.blemale.scaffeine.{ Cache, Scaffeine }
import forex.config.RatesIngestorConfig
import forex.domain.Rate
import forex.repos
import forex.services.rates_ingestor.errors._
import forex.services.rates_ingestor.Algebra
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging

class RatesIngestor[F[_]: ({ type MonadErrorThrowable[T[_]] = MonadError[T, Throwable] })#MonadErrorThrowable: Timer](
    ratesDAO: repos.rates.Algebra[F],
    settings: RatesIngestorConfig
) extends Algebra[F]
    with StrictLogging {

  private val cache: Cache[Rate.Pair, Rate] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(settings.expireAfter)
      .build[Rate.Pair, Rate]()

  private def updateCache(cache: Cache[Rate.Pair, Rate], rates: Either[repos.rates.errors.Error, Seq[Rate]]): Unit =
    rates match {
      case Left(error) =>
        logger.warn(s"Failed to ingest rates, reason: $error")
      case Right(rates) =>
        logger.info(s"Successfully load ${rates.length} rates")
        cache.putAll(rates.map(r => r.pair -> r).toMap)
    }

  private def ingest: F[Unit] =
    for {
      rates <- ratesDAO.getAllRates
      _ <- updateCache(cache, rates).pure[F]
    } yield ()

  override def refreshCache: F[Unit] =
    (ingest.attempt >> implicitly[Timer[F]].sleep(settings.refreshInterval)).foreverM

  override def get(pair: Rate.Pair): F[Either[Error, Rate]] =
    cache.getIfPresent(pair).toRight[Error](Error.PairIsAbsent(pair)).pure[F]
}
