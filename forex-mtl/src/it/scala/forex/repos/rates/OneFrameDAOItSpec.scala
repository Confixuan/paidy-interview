package forex.repos.rates
import cats.effect.{ ContextShift, IO, Resource }
import forex.DockerOneFrameService
import forex.config.{ HttpConfig, OneFrameConfig }
import forex.repos.rates.interpreters.OneFrameDAO
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

class OneFrameDAOItSpec extends AnyWordSpec with Matchers with DockerOneFrameService with EitherValues {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val cs: ContextShift[IO]         = IO.contextShift(ec)
  val httpClient: Resource[IO, Client[IO]]  = BlazeClientBuilder[IO](ec).resource
  val settings: OneFrameConfig = OneFrameConfig(
    token = "10dc303535874aeccc86a8251e6992f5",
    http = HttpConfig(
      host = "localhost",
      port = OneFramePort,
      timeout = 10.seconds
    )
  )
  val dao = new OneFrameDAO(httpClient, settings)

  "OneFrameDAO.getAllRates" should {
    "successfully get all rates" in {
      val result = dao.getAllRates.unsafeRunSync()

      result.isRight shouldBe true
      result.value.length should be > 0
    }
  }
}
