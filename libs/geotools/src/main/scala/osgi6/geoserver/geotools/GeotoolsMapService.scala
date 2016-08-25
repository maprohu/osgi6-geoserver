package osgi6.geoserver.geotools

import java.awt.{GraphicsEnvironment, Rectangle, RenderingHints}
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.map.{Layer, MapContent}
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.geotools.renderer.lite.StreamingRenderer
import org.osgi.framework.BundleContext
import sun.java2d.HeadlessGraphicsEnvironment

/**
  * Created by pappmar on 08/08/2016.
  */
object GeotoolsMapService {

  System.setProperty("java.awt.headless", "true")

  lazy val graphicsEnvironment = {
    try {
      GraphicsEnvironment.getLocalGraphicsEnvironment
    } catch {
      case _ : Throwable =>
        new HeadlessGraphicsEnvironment(
          classOf[BundleContext]
            .getClassLoader
            .loadClass("sun.awt.X11GraphicsEnvironment")
            .newInstance()
            .asInstanceOf[GraphicsEnvironment]
        )
    }


  }

  case class Input(
    imageWidth : Int = 1024,
    imageHeight : Int = 768,
    mapArea : ReferencedEnvelope = new ReferencedEnvelope(0, 90, 0, 80, DefaultGeographicCRS.WGS84),
    layers: Iterable[Layer]
  )

  def render(input: Input) : Array[Byte] = {
    import scala.collection.JavaConversions._

    import input._

    val mapContent = new MapContent()
    try {
      mapContent.addLayers(layers)

      val render = new StreamingRenderer

      render.setMapContent(mapContent)

      val image = new BufferedImage(
        imageWidth,
        imageHeight,
        BufferedImage.TYPE_INT_ARGB
      )

      val graphics2D = graphicsEnvironment.createGraphics(image)
      try {
        graphics2D.setRenderingHint(
          RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON
        )

        val paintArea = new Rectangle(imageWidth, imageHeight)

        render.paint(
          graphics2D,
          paintArea,
          mapArea
        )

        val bos = new ByteArrayOutputStream()
        ImageIO.write(image, "png", bos)
        bos.toByteArray
      } finally {
        graphics2D.dispose()
      }
    } finally {
      mapContent.dispose()
    }
  }

}
