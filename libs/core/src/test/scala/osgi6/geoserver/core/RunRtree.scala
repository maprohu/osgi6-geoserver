package osgi6.geoserver.core

import com.github.davidmoten.rtree.{Entry, RTree}
import com.github.davidmoten.rtree.geometry.{Geometries, Geometry}

import scala.collection.JavaConversions._

/**
  * Created by pappmar on 15/07/2016.
  */
object RunRtree {

  def main(args: Array[String]) {

    val rtree =
      RTree.create[Int, Geometry]()
      .add(1, Geometries.rectangleGeographic(0, 0, 1, 1))

    val result : Iterable[Entry[Int, Geometry]] =
      rtree.search(
        Geometries.pointGeographic(0.5, 0.5)
      ).toBlocking.toIterable

    println(result)


  }

}
