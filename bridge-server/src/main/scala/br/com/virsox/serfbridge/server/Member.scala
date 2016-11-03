package br.com.virsox.serfbridge.server

/***
  * Information about a cluster member.
  * @param name Member name.
  * @param address Member IP address.
  * @param role Member role.
  * @param tags Attached tags.
  */
final case class Member(name: String, address: String, role: String, tags: Map[String, String])

/** Represents a message sent by Serf */
sealed trait SerfMessage

/**
  * Member join. Sent when a new node joins the cluster.
  * @param dc Member datacenter.
  * @param member Member information.
  */
final case class MemberJoin  (dc: String, member: Member) extends SerfMessage

/**
  * Member update. Sent when the node metadata is updated.
  * @param dc Member datacenter.
  * @param member Updated member information.
  */
final case class MemberUpdate(dc: String, member: Member) extends SerfMessage

/**
  * Member leave. Sent when a node leaves the cluster.
  * @param dc Member datacenter.
  * @param name Member name.
  */
final case class MemberLeave (dc: String, name: String)   extends SerfMessage

/**
  * Member failed. Sent when a failure is detected.
  * @param dc Member datacenter.
  * @param name Member name.
  */
final case class MemberFailed(dc: String, name: String)   extends SerfMessage

/***
  * Member reap. Sent to reap a member after it left the cluster or after a failure.
  * The reap is message is sent after a configurable timeout.
  * @param dc Member datacenter.
  * @param name Member name.
  */
final case class MemberReap  (dc: String, name: String)   extends SerfMessage

