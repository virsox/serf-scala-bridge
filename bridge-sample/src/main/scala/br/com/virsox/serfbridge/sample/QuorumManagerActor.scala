package br.com.virsox.serfbridge.sample

import akka.actor.{LoggingFSM, Props}
import br.com.virsox.serfbridge.server.{Member, MemberFailed, MemberJoin, MemberLeave}

import scala.concurrent.duration._

/** Represents the data (state) managed by the actor. */
sealed trait ManagerData

/**
  * Encapsulate quorum information of a multi-datacenter cluster of nodes. It is used to maintain the cluster
  * state and to track failed members. Based on these data, it is also determines if there is a majority of
  * datacenters available. This information can be used for multi-datacenter systems to establish if the actor
  * is running on a datacenter that belongs to a majority partition.
  *
  * @param members Members that are part the cluster. Map of the key is the datacenter name, and the value
  *                are all members that are part of this datacenter.
  * @param failedMembers Set containing all members current in failed state. The set contains pairs
  *                      (datacenter name, node name).
  */
final case class Quorum(members: Map[String, Set[Member]], failedMembers: Set[(String, String)]) extends ManagerData {

  /**
    * New member has joined the cluster (or failed node recovered).
    * @param dc Datacenter name.
    * @param member Member information.
    * @return New quorum data.
    */
  def joined(dc: String, member: Member) =
    Quorum(members updated (dc, members.getOrElse(dc, Set.empty) + member), failedMembers - ((dc, member.name)))

  /**
    * Member has left the cluster.
    * @param dc Datacenter name.
    * @param name Member name.
    * @return New quorum data.
    */
  def left(dc: String, name: String) = {
    val dcMembers = members.getOrElse(dc, Set.empty).filterNot(_.name == name)
    if (dcMembers.isEmpty) {
      Quorum(members - dc, failedMembers)
    } else {
      Quorum(members updated (dc, dcMembers), failedMembers)
    }
  }

  /***
    * Member is in a failing state.
    * @param dc Datacenter name.
    * @param name Member name.
    * @return New quorum data.
    */
  def failing(dc: String, name: String) = Quorum(members, failedMembers + ((dc, name)))

  /**
    * Member has failed.
    * @param dc Datacenter name.
    * @param name Member name.
    * @return New quorum data.
    */
  def failed(dc: String, name: String) = Quorum(
    members updated (dc, members.getOrElse(dc, Set.empty).filterNot(_.name == name)),
    failedMembers
  )

  /**
    * Number of active datacenters (which have active members).
    * @return Number of active datacenters.
    */
  def dcActive = members.count((p) => !p._2.isEmpty)

  /**
    * Total number of datacenters (which have been considered by algorithm).
    * @return Total number of datacenters.
    */
  def dcTotal  = members.keySet.size

  /**
    * Determines if the current datacenter partition is a majority.
    * @return true if the actors sits in a datacenter that belongs to a majority partition.
    */
  def isMajority = dcActive >= Math.ceil(dcTotal / 2.0).toInt

  /***
    * Determines if a node is in the "failing" state - this is true if the node is in both
    * members and failedMembers sets.
    * @param dc Datacenter name.
    * @param name Node name.
    * @return true if the node is in "failing" state.
    */
  def isFailing(dc: String, name: String) =
    failedMembers.contains((dc, name)) && members.getOrElse(dc, Set.empty).exists(_.name == name)

}

// ---------------------------------------------------
// ------------ Actor messages -----------------------
// ---------------------------------------------------

// these messages are internal messages processed by the actor
// (in addition to the SerfMessage)

/** Timeout for the HATransitional state. */
final case class HATransitionalTimeout()

/** Timeout for one of the failing members. */
final case class MemberFailedTimeout(dc: String, name: String)

/** Message used to request the actor state (used for testing) */
final case class QueryMembers()

/** Message containing the actor state (used for testing) */
final case class QueryMembersResponse(state: ManagerState, quorum: Quorum)



// ---------------------------------------------------
// ------------ Actor states -------------------------
// ---------------------------------------------------

/** Actor state. */
sealed trait ManagerState

/** Standalone mode - if there's only one datacentre. */
case object StandaloneMode extends ManagerState

/**
  * Independent mode - if there are two datacentres. In this case, there is no way of determining
  * if a datacentre has crashed or there is a network partition. Because of this, one datacentre can't
  * assume the workload of the other, but they can continue to work as independent system (favoring
  * availability over consistency).
  */
case object IndependentMode extends ManagerState

/**
  * Transitional state to the HighAvailable mode. This state exists to avoid unecessary
  * transitions between IndependentMode and HighAvailableMode (which, in theory, could use a lot of
  * resources).
  */
case object HighAvailableTransitional extends ManagerState

/**
  * High availability mode. This state assumes that there are more than three datacentres running
  * the system, which enables majority determination. The idea is that datacentres in the majority
  * partition should assume the workload of the other partitions.
  */
case object HighAvailableMode extends ManagerState

/**
  * Halted mode. This state represents a situation in which the actor was part of a cluster composed
  * of three or more datacenters, but now it is in a minority partition of the cluster. If the system
  * is in this state, it should halt all processing because the other datacenters are assuming its
  * current workload.
  */
case object HaltedMode extends ManagerState

object QuorumManagerActor {
  def props(actorDc: String, actorId: String, timeout: FiniteDuration) =
    Props(new QuorumManagerActor(actorDc, actorId, timeout))
}


/**
  * Actor that illustrates the implementation of a (datacenter) quorum algorithm using the functionalities provided
  * by Serf and the scala-serf bridge. The actor monitors members of the cluster and tracks the datacenters to which
  * they belong. By doing so, it transitions between states (as described in the states documentation).
  *
  * @param actorDc Datacentre at which this actor is running.
  * @param actorId Name of the ndoe at which this actor is running.
  * @param timeout Timeout. Used for transitioning from HighAvailableTransitional to HighAvailableMode, and for
  *                considering a node as failed.
  */
class QuorumManagerActor(actorDc: String, actorId: String, timeout: FiniteDuration)
  extends LoggingFSM[ManagerState, ManagerData] {


  startWith(StandaloneMode, Quorum(Map.empty, Set.empty))

  // ------------- Standalone Mode --------------------
  when(StandaloneMode) {
    case Event(MemberJoin(dc, member), quorum: Quorum) if dc == actorDc =>
      stay using (quorum joined (dc, member))

    case Event(MemberJoin(dc, member), quorum: Quorum) if dc != actorDc =>
      goto(IndependentMode) using (quorum joined (dc, member))

    // if the associated member is gracefully shutting down, the manager also shutdown
    case Event(MemberLeave(dc, name), _) if name == actorId =>
      stop()

    case Event(MemberLeave(dc, name), quorum: Quorum) =>
      stay using (quorum left (dc, name))
  }


  // ------------- Independent Mode --------------------
  when (IndependentMode) {
    case Event(MemberJoin(dc, member), quorum: Quorum) =>
      val newQuorum = quorum joined(dc, member)
      if (newQuorum.dcTotal < 3) stay using newQuorum
      else goto(HighAvailableTransitional) using newQuorum

    case Event(MemberLeave(dc, name), quorum: Quorum) =>
      val newQuorum = quorum left(dc, name)
      if (newQuorum.dcTotal == 1) goto(StandaloneMode) using newQuorum
      else stay using newQuorum
  }

  onTransition {
    case IndependentMode -> HighAvailableTransitional => setTimer("ha_timer", HATransitionalTimeout, timeout)
    case HighAvailableTransitional -> IndependentMode => cancelTimer("ha_timer")
  }


  // ------------- HAMode Transitional -----------------
  // wait for a timeout before moving to HAMode to make sure that
  // this is not temporary / oscillatory
  when (HighAvailableTransitional) {
    case Event(HATransitionalTimeout, quorum) => goto(HighAvailableMode) using quorum

    case Event(MemberLeave(dc, name), quorum: Quorum) =>
      val newQuorum = quorum left(dc, name)
      if (newQuorum.dcTotal < 3) goto(IndependentMode) using newQuorum
      else stay using newQuorum

    case Event(MemberJoin(dc, member), quorum: Quorum) =>
      stay using (quorum joined(dc, member))
  }


  // ------------- High Available Mode -----------------
  when (HighAvailableMode) {
    case Event(MemberJoin(dc, member), quorum: Quorum) =>
      if (quorum.isFailing(dc, member.name)) {
        cancelTimer(s"name_${dc}_${member.name}")
      }
      stay using (quorum joined (dc, member))

    case Event(MemberLeave(dc, name), quorum: Quorum) =>
      val newQuorum = quorum left(dc, name)
      if (newQuorum.dcTotal < 3) goto(IndependentMode) using newQuorum
      else stay using newQuorum


    case Event(MemberFailed(dc, name), quorum: Quorum) =>

      // in case of a member failed, set its state to "failing".
      // This is a temporary state - the quorum algorithm determines that a member has failed
      // if the member is in failing state and no member join messages has been received
      // within a certain timeout. In other words, the timer give failed members a chance to
      // return, and therefore, avoid unnecessary transitions.
      setTimer(s"name_${dc}_${name}", MemberFailedTimeout(dc, name), timeout)
      stay using (quorum failing(dc, name))

    case Event(MemberFailedTimeout(dc, name), quorum: Quorum) =>
      val newQuorum = quorum failed(dc, name)
      if (newQuorum.isMajority) stay using newQuorum
      else goto(HaltedMode) using newQuorum

  }

  // ------------- Halted Mode -----------------
  when (HaltedMode) {
    case Event(MemberJoin(dc, member), quorum: Quorum) =>
      val newQuorum = quorum joined(dc, member)
      if (newQuorum.isMajority) goto(HighAvailableMode) using newQuorum
      else stay using newQuorum

    case Event(MemberLeave(dc, name), quorum: Quorum)  => stay using (quorum left(dc, name))
    case Event(MemberFailed(dc, name), quorum: Quorum) => stay using (quorum failed(dc, name))
  }


  whenUnhandled {
    case Event(q: QueryMembers, data: Quorum) =>
      sender() ! QueryMembersResponse(stateName, data)
      stay
  }


  initialize()
}
