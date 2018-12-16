package v1.post.data

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
  * Created by owler on 8/24/2017.
  */
final case class Account(id: Int, email: String, fname: String, sname: String, phone: String, sex: String, birth: Int, country: String, city: String)

final case class AccountPost(email: Option[String], first_name: Option[String],
                          last_name: Option[String], gender: Option[String], birth_date: Option[Int]) {
  def verify: Boolean = {
    email.map(_.length <= 100).getOrElse(true) && first_name.map(_.length <= 50).getOrElse(true) &&
      last_name.map(_.length <= 50).getOrElse(true) && gender.map(g => g == "m" || g == "f").getOrElse(true)
  }
}


object Account {

  implicit val implicitWrites = new Writes[Account] {
    def writes(post: Account): JsValue = {
      Json.obj(
        "id" -> post.id,
        "email" -> post.email,
        "first_name" -> post.first_name,
        "last_name" -> post.last_name,
        "gender" -> post.gender,
        "birth_date" -> post.birth_date
      )
    }
  }

  implicit val implicitRead : Reads[Account] = (
    (JsPath \ "id").read[Int] and
    (JsPath \ "email").read[String] and
    (JsPath \ "first_name").read[String] and
    (JsPath \ "last_name").read[String] and
    (JsPath \ "gender").read[String] and
    (JsPath \ "birth_date").read[Int]
  )(Account.apply _)
}

object AccountPost {
   implicit val implicitRead : Reads[AccountPost] = (
      (JsPath \ "email").readNullable[String] and
      (JsPath \ "first_name").readNullable[String] and
      (JsPath \ "last_name").readNullable[String] and
      (JsPath \ "gender").readNullable[String] and
      (JsPath \ "birth_date").readNullable[Int]
    )(AccountPost.apply _)


}
