package v1.post.data

import play.api.libs.json.Json

final case class Group (sex: Option[String], status: Option[String], interests: Option[String], country: Option[String], city: Option[String], count: Int)

object Group {
  implicit val writes = Json.writes[Group]
  implicit val reads = Json.reads[Group]
}