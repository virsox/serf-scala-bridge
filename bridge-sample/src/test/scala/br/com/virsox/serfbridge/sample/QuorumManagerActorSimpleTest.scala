package br.com.virsox.serfbridge.sample

import akka.actor.ActorSystem
import akka.testkit.{TestFSMRef, TestKit}
import br.com.virsox.serfbridge.server.{Member, MemberJoin, MemberLeave}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/** Single message tests for QuorumManagerActor. */
class QuorumManagerActorSimpleTest extends TestKit(ActorSystem("MySpec"))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {


  "A ManagerActor" must {

    // ---------------------------------
    val n1 = Member("n1", "172.20.0.1", "web", Map("tag1" -> "value1"))
    val n2 = Member("n2", "172.20.0.2", "web", Map("tag2" -> "value2"))
    val n3 = Member("n3", "172.20.0.3", "web", Map("tag3" -> "value3"))
    val n4 = Member("n4", "172.20.0.4", "web", Map("tag4" -> "value4"))
    val n5 = Member("n5", "172.20.0.5", "web", Map("tag5" -> "value5"))


    trait StandAloneModeTest {
      val fsm = TestFSMRef(new QuorumManagerActor("dc1", "n1", 60.seconds))
    }

    "initialize in StandAlone mode" in new StandAloneModeTest {
      fsm.stateName shouldBe StandaloneMode
      fsm.stateData shouldBe Quorum(Map.empty, Set.empty)
    }

    "stay in StandAlone mode when another node from the same datacentre joins" in new StandAloneModeTest {
      fsm ! MemberJoin("dc1", n1)
      fsm ! MemberJoin("dc1", n2)
      fsm.stateName shouldBe StandaloneMode
      fsm.stateData shouldBe Quorum(Map("dc1" -> Set(n1, n2)), Set.empty)
    }

    "stay in StandAloneMode when another node from the same datacentre leaves" in new StandAloneModeTest {
      fsm ! MemberJoin("dc1", n1)
      fsm ! MemberJoin("dc1", n2)
      fsm ! MemberLeave("dc1", "n2")
      fsm.stateName shouldBe StandaloneMode
      fsm.stateData shouldBe Quorum(Map("dc1" -> Set(n1)), Set.empty)
    }

    "move to IndependentMode when another node from a different datacentre joins" in new StandAloneModeTest {
      fsm ! MemberJoin("dc1", n1)
      fsm ! MemberJoin("dc2", n2)
      fsm.stateName shouldBe IndependentMode
      fsm.stateData shouldBe Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2)), Set.empty)
    }


    // ----------------------------------------------
    trait IndependentModeTest {
      val fsm = TestFSMRef(new QuorumManagerActor("dc1", "n1", 60.seconds))
      fsm.setState(IndependentMode, Quorum(Map("dc1" -> Set(n1, n2), "dc2" -> Set(n3)), Set.empty))
    }

    "stay in IndependentMode when nodes from the same datacentres join" in new IndependentModeTest {
      fsm ! MemberJoin("dc1", n4)
      fsm ! MemberJoin("dc2", n5)
      fsm.stateName shouldBe IndependentMode
      fsm.stateData shouldBe Quorum(Map("dc1" -> Set(n1, n2, n4), "dc2" -> Set(n3, n5)), Set.empty)
    }

    "stay in IndependentMode when a node leaves but the number of datacentres does not change" in new IndependentModeTest {
      fsm ! MemberLeave("dc1", "n2")
      fsm.stateName shouldBe IndependentMode
      fsm.stateData shouldBe Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n3)), Set.empty)
    }

    "goto StandAloneMode when a node leaves and it only remains one datacentre" in new IndependentModeTest {
      fsm ! MemberLeave("dc2", "n3")
      fsm.stateName shouldBe StandaloneMode
      fsm.stateData shouldBe Quorum(Map("dc1" -> Set(n1, n2)), Set.empty)
    }

    "goto HighAvailableTransitional when a node from another datacentre joins" in new IndependentModeTest {
      fsm ! MemberJoin("dc3", n4)
      fsm.stateName shouldBe HighAvailableTransitional
      fsm.setState(IndependentMode, Quorum(Map(
          "dc1" -> Set(n1, n2),
          "dc2" -> Set(n3),
          "dc3" -> Set(n4)
        ), Set.empty))
    }

    // ----------------------------------------------

    trait HighAvailableTransitionalTest {
      val fsm = TestFSMRef(new QuorumManagerActor("dc1", "n1", 60.seconds))
      fsm.setState(HighAvailableTransitional,
        Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2), "dc3" -> Set(n3)), Set.empty)
      )
    }

    "goto IndependentMode when a datacentre leaves" in new HighAvailableTransitionalTest {
      fsm ! MemberLeave("dc3", "n3")
      fsm.stateName shouldBe IndependentMode
      fsm.stateData shouldBe Quorum(Map("dc1" -> Set(n1), "dc2" -> Set(n2)), Set.empty)
    }


    "stay in IndependentMode even when new nodes join" in new HighAvailableTransitionalTest {
      fsm ! MemberJoin("dc4", n4)
      fsm.stateName shouldBe HighAvailableTransitional
      fsm.stateData shouldBe Quorum(Map(
        "dc1" -> Set(n1), "dc2" -> Set(n2),
        "dc3" -> Set(n3), "dc4" -> Set(n4)
      ), Set.empty)
    }

  }
}
