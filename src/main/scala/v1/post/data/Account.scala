package v1.post.data

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
  * Created by owler on 12/16/2018.
  */
final case class Account(id: Int, email: String, fname: String, sname: String, phone: String, sex: String, birth: Int, country: String, city: String)

final case class AccountPost(email: Option[String], fname: Option[String], sname: Option[String], phone: Option[String], sex: Option[String], birth: Option[Int], country: Option[String], city: Option[String]) {
  def verify: Boolean = {
    email.map(_.length <= 100).getOrElse(true) && fname.map(_.length <= 50).getOrElse(true) &&
      sname.map(_.length <= 50).getOrElse(true) && sex.map(g => g == "m" || g == "f").getOrElse(true)
  }
}


object Account {

  implicit val implicitWrites = new Writes[Account] {
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
  }

  implicit val implicitRead : Reads[Account] = (
    (JsPath \ "id").read[Int] and
    (JsPath \ "email").read[String] and
    (JsPath \ "fname").read[String] and
    (JsPath \ "sname").read[String] and
    (JsPath \ "phone").read[String] and
    (JsPath \ "sex").read[String] and
    (JsPath \ "birth").read[Int] and
    (JsPath \ "country").read[String] and
    (JsPath \ "city").read[String]
  )(Account.apply _)
}

object AccountPost {
   implicit val implicitRead : Reads[AccountPost] = (
      (JsPath \ "email").readNullable[String] and
        (JsPath \ "fname").readNullable[String] and
        (JsPath \ "sname").readNullable[String] and
        (JsPath \ "phone").readNullable[String] and
        (JsPath \ "sex").readNullable[String] and
        (JsPath \ "birth").readNullable[Int] and
        (JsPath \ "country").readNullable[String] and
        (JsPath \ "city").readNullable[String]
    )(AccountPost.apply _)

}
