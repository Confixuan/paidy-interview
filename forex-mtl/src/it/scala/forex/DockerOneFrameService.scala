package forex

import com.spotify.docker.client.messages.PortBinding
import com.whisk.docker.testkit.scalatest.DockerTestKitForAll
import com.whisk.docker.testkit.{ Container, ContainerSpec, DockerReadyChecker, ManagedContainers }
import org.scalatest.{ BeforeAndAfterAll, Suite }

trait DockerOneFrameService extends DockerTestKitForAll with BeforeAndAfterAll { this: Suite =>

  override def afterAll(): Unit = {
    println("Stop all containers")
    containerManager.stop()
  }

  val OneFramePort = 8080

  val oneFrameContainer: Container = ContainerSpec("paidyinc/one-frame:latest")
    .withPortBindings(OneFramePort -> PortBinding.of("0.0.0.0", OneFramePort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("http4s v0.20.13 on blaze v0.14.9 started at"))
    .toContainer

  override val managedContainers: ManagedContainers = oneFrameContainer.toManagedContainer
}
