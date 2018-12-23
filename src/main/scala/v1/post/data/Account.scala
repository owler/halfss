package v1.post.data

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
  * Created by owler on 12/16/2018.
  */
final case class Account(id: Int, joined: Int, status: Option[String], email: String, fname: Option[String], sname: Option[String], phone: Option[String], sex: String, birth: Int, country: Option[String], city: Option[String], interests: Option[List[String]], likes: Option[List[Like]], premium: Option[Premium] )

final case class AccountPost(joined: Option[Int], status: Option[String], email: Option[String], fname: Option[String], sname: Option[String], phone: Option[String], sex: Option[String], birth: Option[Int], country: Option[String], city: Option[String], interests: Option[List[String]], likes: Option[List[Like]], premium: Option[Premium]) {
  def verify: Boolean = {
    email.map(_.length <= 100).getOrElse(true) && fname.map(_.length <= 50).getOrElse(true) &&
      sname.map(_.length <= 50).getOrElse(true) && sex.map(g => g == "m" || g == "f").getOrElse(true)
  }
}

final case class Like(id: Int, ts: Int)
object Like {
  implicit val writes = Json.writes[Like]
  implicit val reads = Json.reads[Like]

}
final case class Premium(start: Int, finish: Int)
object Premium {
  implicit val writes = Json.writes[Premium]
  implicit val reads = Json.reads[Premium]
}
object Account {

  implicit val writes = Json.writes[Account]
 /* implicit val implicitWrites = new Writes[Account] {
    def writes(post: Account): JsValue = {
      Json.obj(
        "id" -> post.id,
        "email" -> post.email,
        "fname" -> post.fname,
        "sname" -> post.sname,
        "phone" -> post.phone,
        "sex" -> post.sex,
        "birth" -> post.birth,
        "country" -> post.country,
        "city" -> post.city
      )
    }
  }*/

  implicit val implicitRead : Reads[Account] = (
    (JsPath \ "id").read[Int] and
    (JsPath \ "joined").read[Int] and
    (JsPath \ "status").readNullable[String] and
    (JsPath \ "email").read[String] and
    (JsPath \ "fname").readNullable[String] and
    (JsPath \ "sname").readNullable[String] and
    (JsPath \ "phone").readNullable[String] and
    (JsPath \ "sex").read[String] and
    (JsPath \ "birth").read[Int] and
    (JsPath \ "country").readNullable[String] and
    (JsPath \ "city").readNullable[String] and
    (JsPath \ "interests").readNullable[List[String]] and
    (JsPath \ "likes").readNullable[List[Like]] and
    (JsPath \ "premium").readNullable[Premium]
  )(Account.apply _)
}

object AccountPost {
   implicit val implicitRead : Reads[AccountPost] = (
     (JsPath \ "joined").readNullable[Int] and
     (JsPath \ "status").readNullable[String] and
      (JsPath \ "email").readNullable[String] and
        (JsPath \ "fname").readNullable[String] and
        (JsPath \ "sname").readNullable[String] and
        (JsPath \ "phone").readNullable[String] and
        (JsPath \ "sex").readNullable[String] and
        (JsPath \ "birth").readNullable[Int] and
        (JsPath \ "country").readNullable[String] and
        (JsPath \ "city").readNullable[String] and
        (JsPath \ "interests").readNullable[List[String]] and
        (JsPath \ "likes").readNullable[List[Like]] and
        (JsPath \ "premium").readNullable[Premium]
    )(AccountPost.apply _)

}
