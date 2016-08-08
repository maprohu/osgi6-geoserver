package osgi6.geoserver.geotools

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, File}
import java.util
import javax.imageio.ImageIO
import javax.sql.DataSource

import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.{Directives, Route}
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.map.{FeatureLayer, Layer, MapContent}
import org.geotools.referencing.CRS
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.geotools.renderer.lite.StreamingRenderer
import org.opengis.referencing.crs.CoordinateReferenceSystem

import scala.util.{Failure, Success, Try}

/**
  * Created by martonpapp on 06/08/16.
  */
object AkkaWMS {

  type LayersProvider = String => Iterable[Layer]

  def create(layers: LayersProvider) : Route = {
    import Directives._

    val route = {
      parameterMap { params =>

        val lparams = params.map({
          case (k, v) => (k.toLowerCase, v)
        })

        def ensureFixed(key: String, value: String) : Try[Unit] = {
          Try {
            require(
              lparams
                .get(key.toLowerCase)
                .forall(_.equalsIgnoreCase(value)),
              s"$key parameter must be '$value'"
            )
          }
        }

        def ensure[T](key: String, pf: PartialFunction[String, T]) : Try[T] = {
          Try {
            val value =
              lparams
                .get(key.toLowerCase)
                .map(_.toLowerCase)

            require(
              value.isDefined,
              s"$key parameter must be set"
            )

            pf.applyOrElse(
              value.get,
              { (v:String) =>
                require(false, s"unknown value for $key parameter: $v")
                ???
              }
            )
          }
        }


        val result = for {
          _ <- ensureFixed("service", "wms")
          _ <- ensureFixed("version", "1.3.0")
          r <- ensure(
            "request",
            {
              case "getmap" =>
                ensureFixed("format", "image/png").get
                ensureFixed("transparent", "true").get
                ensureFixed("styles", "").get
                val width = ensure(
                  "width",
                  PartialFunction(_.toInt)
                ).get
                val height = ensure(
                  "height",
                  PartialFunction(_.toInt)
                ).get
                val (crsCode, crs) = ensure(
                  "crs",
                  PartialFunction { c =>
                    (c, CRS.decode(c, false))
                  }
                ).get

                val bbox = ensure(
                  "bbox",
                  PartialFunction { bbs =>
                    val Array(minx, miny, maxx, maxy) = bbs.split(',').map(_.toDouble)

                    println(CRS.decode(crsCode, true).getCoordinateSystem.getAxis(0))
                    println(CRS.decode(crsCode, false).getCoordinateSystem.getAxis(0))

                    crs.getCoordinateSystem

//                    crs.getCoordinateSystem.getAxis()

//                    CRS.decode(crsCode, false)



                    val env = new ReferencedEnvelope(minx, maxx, miny, maxy, crs)
                    env
                  }
                ).get
                val ilayers = ensure(
                  "layers",
                  PartialFunction(layers)
                ).get

                import GeotoolsMapService._

                render(
                  Input(
                    imageWidth = width,
                    imageHeight = height,
                    bbox,
                    layers = ilayers
                  )
                )



            }
          )
        } yield {
          r
        }

        complete(
          result
            .map({ bs =>
              HttpEntity(
                ContentType(
                  MediaTypes.`image/png`
                ),
                bs
              )
            })
            .recover({
              case ex =>
                HttpEntity(
                  ex.getMessage
                )
            })
            .get
        )
      }
    }

    route
  }





}
