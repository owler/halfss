package v1.post

import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

case class TravelFormInput(body: String)

trait Mstr {
  def prefix: String
  def allowed: List[String]

  def validate(in: String): Option[String] = {
    if (allowed contains in) Some(in)
    else None
  }

  def unapply(str: String): Option[String] = {
    str match {
      case s if s.endsWith(prefix) => validate(s.stripSuffix(prefix))
      case _ => None
    }
  }
}

object Eq extends Mstr {
  val allowed = List("sex", "status", "fname", "sname", "country", "city")
  override def prefix: String = "_eq"
}

object Gt extends Mstr {
  override def prefix: String = "_gt"
  override def allowed = List("email", "birth")
}

object Lt extends Mstr {
  override def prefix: String = "_lt"
  override def allowed = List("email", "birth")
}

/**
  * Takes HTTP requests and produces JSON.
  */
class AccountController @Inject()(cc: PostControllerComponents)(implicit ec: ExecutionContext)
  extends PostBaseController(cc) {


  private val logger = Logger(getClass)

  def filter: Action[AnyContent] = PostAction.async { implicit request =>
    val list = request.queryString.map(l => l._1 match {
        case Eq(name) => name + "='" + l._2.head + "'"
        case Lt(name) if name == "birth" => name + "<" + l._2.head
        case Lt(name) => name + "<'" + l._2.head + "'"
        case Gt(name) if name == "birth" => name + ">" + l._2.head
        case Gt(name) => name + ">'" + l._2.head + "'"
        case _ => null
    })
    postResourceHandler.filter(list).map(
      l => Ok(Json.toJson(l))
    )
  }

  def create: Action[AnyContent] = PostAction.async { implicit request =>
    request.body.asJson match {
      case None => Future {
        BadRequest
      }
      case Some(json) => postResourceHandler.createAccount(json)
    }
  }

  def update(id: Int): Action[AnyContent] = PostAction.async { implicit request =>
    request.body.asJson match {
      case None => Future {
        BadRequest
      }
      case Some(json) => postResourceHandler.updateAccount(id, json)
    }
  }


  def suggest(id: Int): Action[AnyContent] = PostAction.async { implicit request =>
    val start = System.currentTimeMillis()
    try {
      postResourceHandler.lookupAccount(id).map {
        case None => NotFound
        case Some(user) => Ok(Json.toJson(user))
      }
    } finally {
      val delay = System.currentTimeMillis() - start
      if (delay > 100) println("user " + id + " takes ms: " + delay)
    }
  }


  def recommend(id: Int): Action[AnyContent] = PostAction.async { implicit request =>
    postResourceHandler.lookupAccount(id).map {
      case None => NotFound
      case Some(account) => Ok(Json.toJson(account))
    }
  }


  def group: Action[AnyContent] = PostAction.async { implicit request =>
    Future {
      Ok(Json.obj())
    }
  }

}
