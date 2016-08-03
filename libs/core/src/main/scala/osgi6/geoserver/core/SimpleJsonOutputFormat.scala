package osgi6.geoserver.core

import java.io.OutputStream

import net.opengis.wfs.FeatureCollectionType
import org.geoserver.wms.{GetFeatureInfoRequest, WMS}
import org.geoserver.wms.featureinfo.GetFeatureInfoOutputFormat
import org.geotools.feature.FeatureCollection

import scala.collection.JavaConversions._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.opengis.feature.Feature

/**
  * Created by pappmar on 03/08/2016.
  */
class SimpleJsonOutputFormat(wms: WMS) extends GetFeatureInfoOutputFormat("text/json") {
  override def write(results: FeatureCollectionType, request: GetFeatureInfoRequest, out: OutputStream): Unit = {
    val json = JArray(
      results.getFeature.asInstanceOf[java.util.List[FeatureCollection[org.opengis.feature.`type`.FeatureType, Feature]]].to[List].map({ featureCollection =>
        val i = featureCollection.features()
        val items = Iterator
          .continually(
            if (i.hasNext) {
              Some(i.next())
            } else {
              None
            }
          )
          .takeWhile(_.isDefined)
          .map(_.get)
          .map({ feature =>
            JObject(
              feature.getProperties.to[List].flatMap({ property =>
                (Option(property.getValue) match {
                  case Some(v : String) =>
                    Some(JString(v))
                  case Some(v : java.math.BigDecimal) =>
                    Some(JDecimal(v))
                  case Some(v : java.lang.Integer) =>
                    Some(JInt(BigInt(v)))
                  case Some(v : java.math.BigInteger) =>
                    Some(JInt(v))
                  case Some(v : java.lang.Long) =>
                    Some(JLong(v))
                  case Some(v : java.lang.Double) =>
                    Some(JDouble(v))
                  case Some(v : java.lang.Boolean) =>
                    Some(JBool(v))
                  case Some(v : Number) =>
                    Some(JDouble(v.doubleValue()))
                  case None =>
                    None
                  case _ =>
                    None
                }).map({ v =>
                  property.getName.getLocalPart -> v
                })
              })
            )
          })


        JObject(
          "type" -> JString(featureCollection.getSchema.getName.getLocalPart),
          "features" -> JArray(
            items.to[List]
          )
        )
      })
    )

    out.write(compact(render(json)).getBytes(wms.getCharSet))
  }
}
