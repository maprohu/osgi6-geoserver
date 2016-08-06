package osgi6.geoserver.geotools

import java.io.File
import java.util
import javax.sql.DataSource

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import org.geotools.map.FeatureLayer
import org.geotools.styling.BasicPolygonStyle
import org.orbisgis.geoserver.h2gis.datastore.H2GISEmbeddedDataStoreFactory
import osgi6.actor.ActorSystemActivator
import osgi6.geoserver.geotools.AkkaWMS.LayersProvider
import osgi6.h2gis.impl.H2GisActivator
import scala.collection.JavaConversions._

/**
  * Created by martonpapp on 06/08/16.
  */
object RunGtWms {

  def main(args: Array[String]) {
//    System.setProperty("org.geotools.referencing.forceXY", "true")

    implicit val actorSystem = ActorSystemActivator.create("wms")
    implicit val materializer = ActorMaterializer()

    import Directives._

    val dataSource = H2GisActivator.createDataSource(
      new File(
        "../wupdata-osgi/target/bshh2gisstore/h2gis"
      )
    )

    val dsf =
      new H2GISEmbeddedDataStoreFactory {
        override protected def createH2GisDataSource(): DataSource = dataSource.dataSource()
      }

    val store = dsf.createDataStore(
      new util.HashMap[String, Serializable](
        Map[String, Serializable]()
      )
    )

    val iceStyle = new BasicPolygonStyle()

    val layersProvider : LayersProvider = { name =>
      val layer = new FeatureLayer(store.getFeatureSource(s"ICE_${name}"), iceStyle)

      Iterable(
        layer
      )
    }


    val wmsRoute = AkkaWMS.create(layersProvider)

    val route =
      pathSingleSlash {
        complete {
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            index.render
          )
        }
      } ~
      path( "wms" ) {
        logRequestResult("wms") {
          wmsRoute
        }
      }

    Http().bindAndHandle(
      route,
      "0.0.0.0",
      9978
    )

  }

  def index = {
    import scalatags.Text.all._


    html(
      head(
        link(
          rel := "stylesheet",
          href := "http://openlayers.org/en/v3.17.1/css/ol.css",
          `type` := "text/css"
        ),
        script(
          src := "http://openlayers.org/en/v3.17.1/build/ol.js",
          `type` := "text/javascript"
        )
      ),
      body(
        div(
          id := "map",
          width := 1000,
          height := 600
        ),
        script(
          `type` := "text/javascript",
          """
            var map = new ol.Map({
              target: 'map',
              layers: [
                new ol.layer.Image({
//                  source: new ol.source.OSM()
                  source: new ol.source.ImageWMS({
                    url: 'http://demo.boundlessgeo.com/geoserver/wms',
                    ratio: 1,
                    params: {
                      'LAYERS': 'ne:NE1_HR_LC_SR_W_DR'
                    }
                  })
                }),
                new ol.layer.Image({
                  source: new ol.source.ImageWMS({
                    url: '/wms',
                    ratio: 1,
                    params: {'LAYERS': '20150217'}
                  })
                })
              ],
              view: new ol.View({
//                projection: 'EPSG:4326',
                center: [0,0],
                zoom: 2
              })
            });
          """
        )
      )
    )
  }



}
