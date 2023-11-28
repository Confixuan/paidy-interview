package forex.services.rates_ingestor
import cats.MonadError
import cats.effect.Timer
import forex.config.RatesIngestorConfig
import forex.repos
import forex.services.rates_ingestor.interpreters.RatesIngestor

object Interpreters {
  def default[F[_]: ({ type MonadErrorThrowable[T[_]] = MonadError[T, Throwable] })#MonadErrorThrowable: Timer](
      ratesDAO: repos.rates.Algebra[F],
      settings: RatesIngestorConfig
  ) =
    new RatesIngestor[F](ratesDAO, settings)
}
