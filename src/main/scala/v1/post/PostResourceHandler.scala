package v1.post

import java.util.{Calendar, GregorianCalendar}
import javax.inject.Inject

import play.api.MarkerContext
import play.api.libs.json._
import play.api.mvc._
import v1.post.data._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Controls access to the backend data
  */
class PostResourceHandler @Inject()(
                                     postRepository: PostRepository)(implicit ec: ExecutionContext) {
  private val logger =
    org.slf4j.LoggerFactory.getLogger("application.PostResourceHandler")

  private val now = postRepository.getNow

  def createAccount(json: JsValue)(implicit mc: MarkerContext): Future[Result] = {
    Try {
      Json.fromJson[Account](json).get
    }
      .map(u => u) match {
      case Success(u) if verify(u) => postRepository.createAccount(u)
      case Failure(e) =>
        println(e)
        Future {
          Results.BadRequest
        }
      case _ => Future {
        Results.BadRequest
      }
    }
  }

  def updateLikes(json: JsValue)(implicit mc: MarkerContext): Future[Result] = {
    Try {
      Json.fromJson[LikesPost](json).get
    }
      .map(u => u) match {
      case Success(u) if true => postRepository.updateLikes(u)
      case Failure(e) =>
        println(e)
        Future {
          Results.BadRequest
        }
      case _ => Future {
        Results.BadRequest
      }
    }
  }


  def updateAccount(id: Int, json: JsValue)(implicit mc: MarkerContext): Future[Result] = {
    if (isNullField(json, List("email", "sex", "birth"))) {
      Future {
        Results.BadRequest
      }
    } else {

      Try {
        Json.fromJson[AccountPost](json).get
      }
        .map(u => u) match {
        case Success(userPost) if userPost.verify =>
          postRepository.updateAccount(id, userPost)
        case Failure(e) =>
          println(e)
          Future {
            Results.BadRequest
          }
        case _ =>
          println("failed verify for id = " + id)
          Future {
            Results.BadRequest
          }
      }
    }
  }

  def isNullField(json: JsValue, list: List[String]): Boolean = {
    if (list.isEmpty) {
      false
    } else {
      (json \ list.head).toOption match {
        case Some(JsNull) => true
        case _ => isNullField(json, list.tail)
      }
    }
  }

  def verify(u:  Account): Boolean = {
    u.id >= 0 && u.email.length <= 100 &&
      u.fname.forall(_.length <= 50) &&
      u.sname.forall(_.length <= 50) && (u.sex == Option("m") || u.sex == Option("f"))
  }

  def filter(keys: Iterable[String], list: Iterable[String], limit: Option[Int])(implicit mc: MarkerContext): Future[List[Account]] = {
    postRepository.filter(keys, list, limit)
  }

  def group(keys: Iterable[String], list: Iterable[String], limit: Option[Int], order: Boolean)(implicit mc: MarkerContext): Future[List[Group]] = {
    postRepository.group(keys, list, limit, order)
  }

  def lookupAccount(id: Int)(implicit mc: MarkerContext): Future[Option[Account]] = {
    postRepository.getAccount(id)
  }

 def recommend(id: Int, list: List[String], limit: Option[Int])(implicit mc: MarkerContext): Future[Option[List[Account]]] = {
    postRepository.recommend(id, list, limit)
  }

  def suggest(id: Int, list: List[String], limit: Option[Int])(implicit mc: MarkerContext): Future[Option[List[Account]]] = {
    postRepository.suggest(id, list, limit)
  }


  def relativeTimeStamp(age: Int): Long = {
    val c = new GregorianCalendar()
    c.setTimeInMillis(now)
    c.add(Calendar.YEAR, -age)
    c.getTimeInMillis / 1000
  }



}
