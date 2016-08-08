package osgi6.geoserver.geotools

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.util
import javax.imageio.ImageIO
import javax.sql.DataSource

import ogsi6.libs.h2gis.H2GisUtil
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.map.{FeatureLayer, MapContent}
import org.geotools.referencing.crs.{DefaultGeocentricCRS, DefaultGeographicCRS}
import org.geotools.renderer.lite.StreamingRenderer
import org.geotools.styling.BasicPolygonStyle
import org.orbisgis.geoserver.h2gis.datastore.H2GISEmbeddedDataStoreFactory
import osgi6.h2gis.impl.H2GisActivator

import scala.collection.JavaConversions._

/**
  * Created by martonpapp on 05/08/16.
  */
object RunGtRender {

  case class Input(
    imageWidth : Int = 1024,
    imageHeight : Int = 768,
    mapArea : ReferencedEnvelope = new ReferencedEnvelope(0, 45, 0, 80, DefaultGeographicCRS.WGS84)
  )

  def main(args: Array[String]) {

    val (ds, dsClose) = H2GisUtil.createDataSource(
      new File(
        "../wupdata-osgi/target/bshh2gisstore/h2gis"
      )
    )

    val dsf =
      new H2GISEmbeddedDataStoreFactory {
        override protected def createH2GisDataSource(): DataSource = ds
      }

    val input = Input()

    import input._

    val mapContent = new MapContent()

    val iceStyle = new BasicPolygonStyle()

    val store = dsf.createDataStore(
      new util.HashMap[String, Serializable]()
    )

    val layer = new FeatureLayer(store.getFeatureSource("ICE_20150217"), iceStyle)
    mapContent.addLayer(layer)

    val render = new StreamingRenderer
    render.setMapContent(mapContent)


    val image = new BufferedImage(
      imageWidth,
      imageHeight,
      BufferedImage.TYPE_INT_ARGB
    )

    val graphics2D = image.createGraphics()
    val paintArea = new Rectangle(imageWidth, imageHeight)

    render.paint(
      graphics2D,
      paintArea,
      mapArea
    )

    ImageIO.write(image, "png", new File("../osgi6-geoserver/target/render.png"))



  }


}
