package colossus

import testkit._
import core._
import service.{Service, AsyncServiceClient, Callback}
import Callback.Implicits._

import akka.actor._
import akka.testkit.TestProbe

import java.net.InetSocketAddress
import scala.concurrent.duration._
import akka.util.ByteString

import RawProtocol._


class ConnectionHandlerSpec extends ColossusSpec {

  "Server Connection Handler" must {
    "bind to worker on creation" in {
      val probe = TestProbe()
      class MyHandler extends BasicSyncHandler with ServerConnectionHandler {
        override def onBind() {
          probe.ref ! "BOUND"
        }

        def shutdownRequest(){}

        def receivedData(data: DataBuffer) {}
      }
      withIOSystemAndServer(Delegator.basic(() => new MyHandler)) { (io, server) => {
        val c = TestClient(io, TEST_PORT)
        probe.expectMsg(100.milliseconds, "BOUND")
      }
      }
    }

    "unbind on disconnect" in {
      val probe = TestProbe()
      class MyHandler extends BasicSyncHandler with ServerConnectionHandler{
        override def onUnbind() {
          probe.ref ! "UNBOUND"
        }

        override def connectionTerminated(cause: DisconnectCause) {
          println(s"Terminated: $cause")
        }
        def receivedData(data: DataBuffer){}
        def shutdownRequest(){}
      }
      withIOSystemAndServer(Delegator.basic(() => new MyHandler)){ (io, server) => {
          val c = TestClient(io, TEST_PORT, connectionAttempts = PollingDuration.NoRetry)
          c.disconnect()
          TestClient.waitForStatus(c, ConnectionStatus.NotConnected)
          probe.expectMsg(500.milliseconds, "UNBOUND")
        }
      }
    }
  }

  "Client Connection Handler" must {

    class MyHandler(probe: ActorRef, sendBind: Boolean, sendUnbind: Boolean, disconnect: Boolean = false) extends BasicSyncHandler with ClientConnectionHandler{
      override def onBind() {
        if (sendBind) probe ! "BOUND"
      }
      override def onUnbind() {
        if (sendUnbind) probe ! "UNBOUND"
      }

      override def connected(endpoint: WriteEndpoint) {
        if (disconnect) endpoint.disconnect()
      }

      override def connectionTerminated(cause: DisconnectCause) {
        println(s"TERMINATED: $cause")
      }

      def receivedData(data: DataBuffer){}
      def connectionFailed(){}
    }

    "bind to worker" in {
      val probe = TestProbe()
      withIOSystem{ implicit io =>
        //obvious this will fail to connect, but we don't care here
        io ! IOCommand.BindAndConnectWorkerItem(new InetSocketAddress("localhost", TEST_PORT), _ => new MyHandler(probe.ref, true, false))
        probe.expectMsg(100.milliseconds, "BOUND")
      }
    }

    "automatically unbind on manual disconnect" in {
      val probe = TestProbe()
      class MyHandler extends BasicSyncHandler with ClientConnectionHandler{
        override def onUnbind() {
          probe.ref ! "UNBOUND"
        }

        override def connected(endpoint: WriteEndpoint) {
          endpoint.disconnect()
        }
        def receivedData(data: DataBuffer){}
        def connectionFailed(){}
      }
      withIOSystem{ implicit io =>
        withServer(Service.become[Raw]("test", TEST_PORT){case x => x}) {
          io ! IOCommand.BindAndConnectWorkerItem(new InetSocketAddress("localhost", TEST_PORT), _ => new MyHandler)
          probe.expectMsg(250.milliseconds, "UNBOUND")
        }
      }
    }

    "automatically unbind on disrupted connection" in {
      val probe = TestProbe()
      withIOSystem{ implicit io =>
        val server = Service.become[Raw]("test", TEST_PORT){case x => x}
        withServer(server) {
          io ! IOCommand.BindAndConnectWorkerItem(
            new InetSocketAddress("localhost", TEST_PORT), _ => new MyHandler(probe.ref, true, true)
          )
          probe.expectMsg(500.milliseconds, "BOUND")
        }
        end(server)
        probe.expectMsg(500.milliseconds, "UNBOUND")
      }

    }

    "automatically unbind on failure to connect" in {
      val probe = TestProbe()
      withIOSystem{ implicit io =>
        io ! IOCommand.BindAndConnectWorkerItem(
          new InetSocketAddress("localhost", TEST_PORT), _ => new MyHandler(probe.ref, true, true)
        )
        probe.expectMsg(250.milliseconds, "BOUND")
        probe.expectMsg(250.milliseconds, "UNBOUND")
      }
    }

    "NOT automatically unbind with ManualUnbindHandler mixin on disrupted connection" in {
      val probe = TestProbe()
      class MyHandler extends BasicSyncHandler with ClientConnectionHandler with ManualUnbindHandler{
        override def onUnbind() {
          probe.ref ! "UNBOUND"
        }

        def receivedData(data: DataBuffer){}
        def connectionFailed(){}
      }
      withIOSystem{ implicit io =>
        withServer(Service.become[Raw]("test", TEST_PORT){case x => x}) {
          io ! IOCommand.BindAndConnectWorkerItem(new InetSocketAddress("localhost", TEST_PORT), _ => new MyHandler)
          probe.expectNoMsg(200.milliseconds)
        }
        probe.expectNoMsg(200.milliseconds)
      }
    }

    "NOT automatically unbind on failed connection with ManualUnbindHandler" in {
      val probe = TestProbe()
      class MyHandler extends BasicSyncHandler with ClientConnectionHandler with ManualUnbindHandler{
        override def onUnbind() {
          probe.ref ! "UNBOUND"
        }

        def receivedData(data: DataBuffer){}
        def connectionFailed(){}
      }
      withIOSystem{ implicit io =>
        io ! IOCommand.BindAndConnectWorkerItem(new InetSocketAddress("localhost", TEST_PORT), _ => new MyHandler)
        probe.expectNoMsg(200.milliseconds)
      }

    }
  }


}

