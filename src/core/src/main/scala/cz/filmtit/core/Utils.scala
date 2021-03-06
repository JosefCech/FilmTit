package cz.filmtit.core

/**
 * @author Joachim Daiber
 *
 *
 *
 */

object Utils {

  /**
   * map method for Tuples
   *
   * e.g. (1, 2) map (_ * 2)
   **/
  implicit def t2mapper[X, A <: X, B <: X](t: (A,B)) = new {
    def map[R](f: X => R) = (f(t._1), f(t._2))
  }

}
