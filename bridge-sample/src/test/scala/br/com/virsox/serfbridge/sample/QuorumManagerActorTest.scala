package br.com.virsox.serfbridge.sample

import akka.actor.ActorSystem
import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import br.com.virsox.serfbridge.server.{Member, MemberFailed, MemberJoin, MemberLeave}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/** Multi messages QuorumManagerActor tests. */
class QuorumManagerActorTest extends TestKit(ActorSystem("MySpec"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "A ManagerActor actor" must {

    val n1 = Member("n1", "172.20.0.1", "web", Map("tag1" -> "value1"))
    val n2 = Member("n2", "172.20.0.2", "web", Map("tag2" -> "value2"))
    val n3 = Member("n3", "172.20.0.3", "web", Map("tag3" -> "value3"))
    val n4 = Member("n4", "172.20.0.4", "web", Map("tag4" -> "value4"))
    val n5 = Member("n5", "172.20.0.5", "web", Map("tag5" -> "value5"))


    "stop if the nodes leaves the cluster" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 5.seconds))
      val probe = TestProbe()
      probe watch manager

      manager ! MemberJoin("dc1", n1)
      manager ! MemberLeave("dc1", "n1")
      probe.expectTerminated(manager, 1.second)
    }


    "goto HAMode Transitional when nodes from at least three distinct datacentres join the cluster" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 5.seconds))
      manager ! MemberJoin("dc1", n1)
      manager ! MemberJoin("dc2", n2)
      manager ! MemberJoin("dc3", n3)
      manager ! QueryMembers()

      expectMsg(QueryMembersResponse(HighAvailableTransitional,
        Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2), "dc3" -> Set(n3)), Set.empty)))
    }

    "stay in HAMode Transitional even if another member join but the timeout does not pass" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 5.seconds))
      manager ! MemberJoin("dc1", n1)
      manager ! MemberJoin("dc2", n2)
      manager ! MemberJoin("dc3", n3)
      manager ! MemberJoin("dc4", n4)
      manager ! QueryMembers()

      expectMsg(QueryMembersResponse(HighAvailableTransitional,
          Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2), "dc3" -> Set(n3), "dc4" -> Set(n4)),
        Set.empty)))
    }

    "automatically goto HighAvailableMode when nodes from at least three distinct datacentres join the cluster" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 2.seconds))
      manager ! SubscribeTransitionCallBack(testActor)
      expectMsg(CurrentState(manager, StandaloneMode))

      manager ! MemberJoin("dc1", n1)
      manager ! MemberJoin("dc2", n2)
      expectMsg(Transition(manager, StandaloneMode, IndependentMode))

      manager ! MemberJoin("dc3", n3)
      expectMsg(Transition(manager, IndependentMode, HighAvailableTransitional))

      within(2.seconds, 4.seconds) {
        expectMsg(Transition(manager, HighAvailableTransitional, HighAvailableMode))
      }

      manager ! QueryMembers()
      expectMsg(QueryMembersResponse(HighAvailableMode,
        Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2), "dc3" -> Set(n3)), Set.empty)))
    }


    "stay in HighAvailableMode if a single node fails" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 1.seconds))

      manager ! MemberJoin("dc1", n1)
      manager ! MemberJoin("dc2", n2)
      manager ! MemberJoin("dc3", n3)
      expectNoMsg(2.seconds) // wait for transition to HighAvailableMode

      manager ! MemberFailed("dc3", "n3")
      expectNoMsg(2.seconds)

      manager ! QueryMembers()
      expectMsg(QueryMembersResponse(HighAvailableMode,
        Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2), "dc3" -> Set.empty), Set(("dc3", "n3")) )))
    }


    "stay in HighAvailableMode if two failed nodes return within the timeout" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 1.seconds))

      manager ! MemberJoin("dc1", n1)
      manager ! MemberJoin("dc2", n2)
      manager ! MemberJoin("dc3", n3)
      expectNoMsg(2.seconds) // wait for transition to HighAvailableMode

      manager ! MemberFailed("dc2", "n2")
      manager ! MemberFailed("dc3", "n3")
      expectNoMsg(100.milliseconds)
      manager ! MemberJoin("dc2", n2) // they return within the timeout
      manager ! MemberJoin("dc3", n3)

      manager ! QueryMembers()
      expectMsg(QueryMembersResponse(HighAvailableMode,
        Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2), "dc3" -> Set(n3)), Set.empty)))
    }

    "stay in HighAvailableMode if one of two failed nodes returns within the timeout" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 1.seconds))

      manager ! MemberJoin("dc1", n1)
      manager ! MemberJoin("dc2", n2)
      manager ! MemberJoin("dc3", n3)
      expectNoMsg(2.seconds) // wait for transition to HighAvailableMode

      manager ! MemberFailed("dc2", "n2")
      manager ! MemberFailed("dc3", "n3")
      manager ! MemberJoin("dc2", n2) // it returns within the timeout
      expectNoMsg(2.seconds)            // waits to confirm "n3" failure

      manager ! QueryMembers()
      expectMsg(QueryMembersResponse(HighAvailableMode,
        Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2), "dc3" -> Set.empty), Set(("dc3", "n3")))))
    }


    "goto HaltedMode if two nodes fail" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 1.seconds))

      manager ! MemberJoin("dc1", n1)
      manager ! MemberJoin("dc2", n2)
      manager ! MemberJoin("dc3", n3)
      expectNoMsg(2.seconds) // wait for transition to HighAvailableMode

      manager ! MemberFailed("dc2", "n2")
      manager ! MemberFailed("dc3", "n3")
      expectNoMsg(2.seconds)

      manager ! QueryMembers()
      expectMsg(QueryMembersResponse(HaltedMode,
        Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(), "dc3" -> Set()), Set(("dc2", "n2"), ("dc3", "n3")))))
    }

    "goto HaltedMode if the manager is no longer in a majority partition" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 1.seconds))
      manager ! MemberJoin("dc1", n1)
      manager ! MemberJoin("dc2", n2)
      manager ! MemberJoin("dc3", n3)
      manager ! MemberJoin("dc4", n4)
      manager ! MemberJoin("dc5", n5)
      expectNoMsg(2.seconds) // wait for transition to HighAvailableMode

      manager ! MemberFailed("dc2", "n2")
      manager ! MemberFailed("dc3", "n3")
      manager ! MemberFailed("dc4", "n4")
      expectNoMsg(2.seconds)

      manager ! QueryMembers()
      expectMsg(QueryMembersResponse(
        HaltedMode,
        Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(), "dc3" -> Set(), "dc4" -> Set(), "dc5" -> Set(n5)),
               Set(("dc2", "n2"), ("dc3", "n3"), ("dc4", "n4")))
      ))
    }

    "goto HighAvailableMode from HaltedMode if a failed node re-join the cluster" in {
      val manager = system.actorOf(QuorumManagerActor.props("dc1", "n1", 1.seconds))

      manager ! MemberJoin("dc1", n1)
      manager ! MemberJoin("dc2", n2)
      manager ! MemberJoin("dc3", n3)
      expectNoMsg(2.seconds) // wait for transition to HighAvailableMode

      manager ! MemberFailed("dc2", "n2")
      manager ! MemberFailed("dc3", "n3")
      expectNoMsg(2.seconds) // wait for transition to HaltedMode

      manager ! MemberJoin("dc2", n2)

      manager ! QueryMembers()
      expectMsg(QueryMembersResponse(HighAvailableMode,
        Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2), "dc3" -> Set()), Set(("dc3", "n3")))))
    }
  }


}
