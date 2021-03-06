package colossus
package testkit

import colossus.service.{UnmappedCallback, Callback}
import org.scalatest.{MustMatchers, WordSpec}

import scala.util.Try

class CallbackMatchersSpec extends WordSpec with MustMatchers{

  import CallbackMatchers._

  "A CallbackMatcher" should {

    "fail to match if a Callback never executes" in {

      def cbFunc(f : Try[Int] => Unit) {

      }

      val unmapped = UnmappedCallback(cbFunc)

      var execd = false
      val result = new CallbackEvaluateTo[Int](a => execd = true).apply(unmapped)
      result.matches must equal(false)
      execd must equal(false)

    }

    "fail to match if the 'evaluate' function throws" in {
      var execd = false
      val cb = Callback.successful("success!")
      val eval = (a : String) => {
        execd = true
        a must equal("awesome!")
      }
      val result = new CallbackEvaluateTo[String](eval).apply(cb)
      result.matches must equal(false)
      execd must equal(true)
    }

    "fail to match if a Callback reports a failure" in {
      val cb = Callback.failed(new Exception("bam"))
      var execd = false
      val result = new CallbackEvaluateTo[Any](a => execd = true).apply(cb)
      result.matches must equal(false)
      execd must equal(false)
    }

    "success if the callback successfully executes the evaluate function" in {
      var execd = false
      val cb = Callback.successful("success!")
      val eval = (a : String) => {
        execd = true
        a must equal("success!")
      }
      val result = new CallbackEvaluateTo[String](eval).apply(cb)
      result.matches must equal(true)
      execd must equal(true)
    }

    "report the error for a failed callback" in {
      val cb = Callback.successful("YAY").map{t => throw new Exception("NAY")}
      var execd = false
      val eval = (a: String) => {
        execd = true
        a must equal("YAY")
      }
      val result = new CallbackEvaluateTo[String](eval).apply(cb)
      result.matches must equal(false)
      execd must equal(false)
      result.failureMessage.contains("NAY") must equal(true)
    }


  }

  "A CallbackFailTo matcher" should {
    "fail to match if a Callback never executes" in {
      def cbFunc(f : Try[Int] => Unit) {

      }

      val unmapped = UnmappedCallback(cbFunc)

      var execd = false
      val result = new CallbackFailTo[Int](a => execd = true).apply(unmapped)
      result.matches must equal(false)
      execd must equal(false)
    }

    "fail to match if the 'evaluate' function throws" in {
      var execd = false
      val cb = Callback.failed(new Exception("D'OH!"))
      val eval = (a : Throwable) => {
        execd = true
        a must equal("awesome!")
      }
      val result = new CallbackFailTo[String](eval).apply(cb)
      result.matches must equal(false)
      execd must equal(true)
    }

    "fail to match if the Callback executes successfully" in {
      var execd = false
      val cb = Callback.successful("success!")
      val eval = (a : Throwable) => {
        execd = true
        a must not be null
      }
      val result = new CallbackFailTo[String](eval).apply(cb)
      result.matches must equal(false)
      execd must equal(false)
    }

    "match if a Callback fails" in {
      var execd = false
      val cb = Callback.failed(new Exception("D'OH!"))
      val eval = (a : Throwable) => {
        execd = true
      }
      val result = new CallbackFailTo[String](eval).apply(cb)
      result.matches must equal(true)
      execd must equal(true)
    }
  }

}
