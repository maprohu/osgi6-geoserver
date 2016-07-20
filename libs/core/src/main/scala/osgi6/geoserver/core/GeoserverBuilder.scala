package osgi6.geoserver.core

import java.awt.RenderingHints.Key
import java.io.{File, Serializable}
import java.util

import maprohu.scalaext.common.Stateful
import org.geoserver.catalog.{Catalog, ProjectionPolicy, StoreInfo, StyleInfo}
import org.geoserver.catalog.impl._
import org.geoserver.config.impl.GeoServerImpl
import org.geoserver.ows.kvp.BooleanKvpParser
import org.geoserver.ows._
import org.geoserver.platform.{GeoServerExtensions, GeoServerResourceLoader, Service}
import org.geoserver.wfs.{WFSInfoImpl, WFSLoader, WFSXStreamLoader}
import org.geoserver.wfs.kvp.BBoxKvpParser
import org.geoserver.wms._
import org.geoserver.wms.capabilities.{CapabilitiesKvpReader, Capabilities_1_3_0_Response, GetCapabilitiesResponse}
import org.geoserver.wms.featureinfo._
import org.geoserver.wms.legendgraphic.{GetLegendGraphicKvpReader, PNGLegendGraphicResponse, PNGLegendOutputFormat}
import org.geoserver.wms.map._
import org.geotools.data.DataAccessFactory.Param
import org.geotools.data.{DataAccess, DataAccessFactory, DataStore}
import org.geotools.factory.{FactoryIteratorProvider, GeoTools, Hints}
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.geotools.util.{Converter, ConverterFactory, Version}
import org.opengis.feature
import org.opengis.feature.Feature
import org.springframework.context.support.StaticApplicationContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.StaticWebApplicationContext
import sbt.io.Path._

// 2.5
import org.vfny.geoserver.global.GeoserverDataDirectory
// 2.8
//import org.geoserver.wms.dimension.impl.DimensionDefaultValueSelectionStrategyFactoryImpl

import scala.collection.JavaConversions._
import scala.xml.{Node, XML}

/**
 * Created by pappmar on 14/10/2015.
 */

//object GeoserverStarter {
//
//  def setupGeoserver(workdir: File) = {
//    val builder = new GeoserverBuilder(workdir)
//
//    val style : scala.xml.Node =
//      <StyledLayerDescriptor version="1.0.0"
//                             xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd"
//                             xmlns="http://www.opengis.net/sld"
//                             xmlns:ogc="http://www.opengis.net/ogc"
//                             xmlns:xlink="http://www.w3.org/1999/xlink"
//                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
//        <NamedLayer>
//          <Name>Simple point</Name>
//          <UserStyle>
//            <Title>GeoServer SLD Cook Book: Simple point</Title>
//            <FeatureTypeStyle>
//              <Rule>
//                <PointSymbolizer>
//                  <Graphic>
//                    <Mark>
//                      <WellKnownName>emsa://vessel</WellKnownName>
//                      <Fill>
//                        <CssParameter name="fill">#FF0000</CssParameter>
//                      </Fill>
//                    </Mark>
//                    <Size>6</Size>
//                  </Graphic>
//                </PointSymbolizer>
//              </Rule>
//            </FeatureTypeStyle>
//          </UserStyle>
//        </NamedLayer>
//      </StyledLayerDescriptor>
//
////    for {
////      name <- dataStoreMap.keys
////    } {
//////      builder.addAisLayer(name, aisStyle)
////    }
//
//
////    println(AisMessageProcessor.pp.format(aisStyle))
//
//
//
//    builder.build()
//  }

//  def aisZoomRule(
//    name: String,
//    minDenom : Option[Long],
//    maxDenom : Option[Long],
//    size : Int
//  ) =
//    <Rule>
//      <Name>{name}</Name>
//      {minDenom.map(d => <MinScaleDenominator>{d.toString}</MinScaleDenominator>).toSeq}
//      {maxDenom.map(d => <MaxScaleDenominator>{d.toString}</MaxScaleDenominator>).toSeq}
//      <PointSymbolizer>
//        <Graphic>
//          <Mark>
//            <WellKnownName>emsa://vessel</WellKnownName>
//            <Fill>
//              <CssParameter name="fill">#0000FF</CssParameter>
//            </Fill>
//            <Stroke>
//              <CssParameter name="stroke">#000000</CssParameter>
//              <CssParameter name="stroke-width">1</CssParameter>
//            </Stroke>
//          </Mark>
//          <Size>{size}</Size>
//          <Rotation><ogc:PropertyName>{AisDataAccessFactory.attrCourseOverGround}</ogc:PropertyName>4</Rotation>
//        </Graphic>
//      </PointSymbolizer>
//      <TextSymbolizer>
//        <Label>
//          <ogc:PropertyName>{AisDataAccessFactory.attrVesselName}</ogc:PropertyName>
//        </Label>
//        <Font>
//          <CssParameter name="font-family">Bitstream Vera Sans</CssParameter>
//          <CssParameter name="font-size">12</CssParameter>
//          <CssParameter name="font-style">normal</CssParameter>
//          <CssParameter name="font-weight">normal</CssParameter>
//        </Font>
//        <LabelPlacement>
//          <PointPlacement>
//            <AnchorPoint>
//              <AnchorPointX>0.0</AnchorPointX>
//              <AnchorPointY>0.0</AnchorPointY>
//            </AnchorPoint>
//            <Displacement>
//              <DisplacementX>5</DisplacementX>
//              <DisplacementY>5</DisplacementY>
//            </Displacement>
//          </PointPlacement>
//        </LabelPlacement>
//        <Fill>
//          <CssParameter name="fill">#000000</CssParameter>
//        </Fill>
//      </TextSymbolizer>
//    </Rule>
//
//  def aisStyle =
//    <StyledLayerDescriptor version="1.0.0"
//                           xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd"
//                           xmlns="http://www.opengis.net/sld"
//                           xmlns:ogc="http://www.opengis.net/ogc"
//                           xmlns:xlink="http://www.w3.org/1999/xlink"
//                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
//      <NamedLayer>
//        <Name>frontex</Name>
//        <UserStyle>
//          <Title>Frontex</Title>
//          <FeatureTypeStyle>
//            {
//            aisZoomRule(
//              name = "rule0",
//              minDenom = None,
//              maxDenom = Some(1000000),
//              size = 20
//            )
//            }
//            {
//            aisZoomRule(
//              name = "rule1",
//              minDenom = Some(1000000),
//              maxDenom = Some(17000000),
//              size = 15
//            )
//            }
//            {
//            aisZoomRule(
//              name = "rule2",
//              minDenom = Some(17000000),
//              maxDenom = None,
//              size = 10
//            )
//            }
//          </FeatureTypeStyle>
//        </UserStyle>
//      </NamedLayer>
//    </StyledLayerDescriptor>
//
//
//}

object GeoserverBuilder {

  case class Input(
    workdir : File,
    defaultWorkspace : String = "defaultws"
  )

  var input : Input = null

  type DA = DataAccess[_ <: org.opengis.feature.`type`.FeatureType, _ <: Feature]
  type AddFactory = DA => String
  val ParamName = "GS_FACTORY"

  lazy val instance = {
    val dafClass = classOf[DataAccessFactory]



    case class Factories(
      seq: Int = 0,
      map: Map[String, DA] = Map()
    )

    val factories = Stateful(Factories())


    val addFactory : AddFactory = { da =>
      factories.transform({ f =>
        val id =
          f.seq.toString
        (
          id,
          f.copy(
            seq = f.seq + 1,
            map = f.map.updated(id, da)
          )
        )
      })
    }

    val dataAccessFactory : DataAccessFactory = new DataAccessFactory{

      override def createDataStore(params: util.Map[String, Serializable]): DA = {
        factories.extract.map(params(ParamName).asInstanceOf[String])
      }

      override def getDisplayName: String = ""

      override def getDescription: String = ""

      override def canProcess(params: util.Map[String, Serializable]): Boolean = true

      override def isAvailable: Boolean = true

      override def getParametersInfo: Array[Param] = Array()

      override def getImplementationHints: util.Map[Key, _] = Map[Key, AnyRef]()

    }

    GeoTools.addFactoryIteratorProvider(new FactoryIteratorProvider {
      override def iterator[T](category: Class[T]): util.Iterator[T] = {
        category match {
          case `dafClass` =>
            Iterator.single[T](dataAccessFactory.asInstanceOf[T])
          case _ =>
            Iterator.empty
        }
      }
    })

    System.setProperty("org.geotools.referencing.forceXY", "true")

    val ctx = new StaticApplicationContext()
    def regNamed(name:String, bean:Any):Unit = {
      ctx.getBeanFactory.registerSingleton(name, bean)
    }
    def reg(bean:Any):Unit = {
      regNamed(bean.getClass.getCanonicalName, bean)
    }

    new GeoServerExtensions().setApplicationContext(ctx)

    val geoserver = new GeoServerImpl
    val wmsInfo = new WMSInfoImpl()
    geoserver.add(wmsInfo)


    val catalog = new LocalWorkspaceCatalog(new CatalogImpl)
    regNamed("catalog", catalog)
    geoserver.setCatalog(catalog)
    val gsrl = new GeoServerResourceLoader(input.workdir)
    reg(gsrl)
    // 2.5
    GeoserverDataDirectory.setResourceLoader(gsrl)
    catalog.setResourceLoader(gsrl)

    //  val wfsLoader = new WFSXStreamLoader(gsrl)
    //  val wfsInfo = wfsLoader.create(geoserver)
    //  geoserver.add(wfsInfo)




    val wms = new WMS(geoserver)

    val wmsService = new DefaultWebMapService(wms)


    val getCapabilities = new GetCapabilities(wms)
    wmsService.setGetCapabilities(getCapabilities)

    reg(new CapabilitiesKvpReader(wms))

    // 2.8.2
    //        reg(new DimensionDefaultValueSelectionStrategyFactoryImpl)
    reg(new Capabilities_1_3_0_Response)
    reg(new GetCapabilitiesResponse(wms))
    reg(new BBoxKvpParser)
    reg(new BooleanKvpParser("transparent"))
    //    reg(new LegendSampleImpl(catalog, gsrl))
    reg(new PNGLegendOutputFormat)

    val htmlFeatureInfoOutputFormat = new HTMLFeatureInfoOutputFormat(wms)
    //    val gml2FeatureInfoOutputFormat = new GML2FeatureInfoOutputFormat(wms)
    //    val gml3FeatureInfoOutputFormat = new GML3FeatureInfoOutputFormat(wms)


    wms.setApplicationContext(ctx)
    wmsService.setApplicationContext(ctx)
    reg(new RenderedImageMapOutputFormat(wms))
    //    reg(new OpenLayersMapOutputFormat(wms))
    reg(new PNGMapResponse(wms))
    reg(new RawMapResponse)
    reg(new GetFeatureInfoResponse(wms, htmlFeatureInfoOutputFormat))



    val getMap = new GetMap(wms)
    wmsService.setGetMap(getMap)
    reg(new GetMapKvpRequestReader(wms))


    val getFeatureInfo = new GetFeatureInfo
    wmsService.setGetFeatureInfo(getFeatureInfo)
    reg(new GetFeatureInfoKvpReader(wms))
    reg(htmlFeatureInfoOutputFormat)
    //    reg(gml2FeatureInfoOutputFormat)
    //    reg(gml3FeatureInfoOutputFormat)
    val basicLayerIdentifier = new VectorBasicLayerIdentifier(wms)
    reg(new VectorRenderingLayerIdentifier(wms, basicLayerIdentifier))
    reg(new RasterCleaner)

    val getLegendGraphic = new GetLegendGraphic(wms)
    wmsService.setGetLegendGraphic(getLegendGraphic)
    reg(new GetLegendGraphicKvpReader(wms))
    reg(new PNGLegendGraphicResponse)
    reg(new LocalWorkspaceCallback(geoserver))
    reg(new LocalWorkspaceCatalogFilter(catalog))
    regNamed("umows", new LocalWorkspaceURLMangler("ows"))
    regNamed("umwms", new LocalWorkspaceURLMangler("wms"))
    reg(new WMSWorkspaceQualifier(catalog))

    val wms130sd = new Service("wms", wmsService, new Version("1.3.0"), List(
      "GetCapabilities",
      "GetMap",
      "GetFeatureInfo",
      "GetLegendGraphic"
    ))
    regNamed("wms130", wms130sd)

    val wms111sd = new Service("wms", wmsService, new Version("1.1.1"), List(
      "GetCapabilities",
      "GetMap",
      "GetFeatureInfo",
      "GetLegendGraphic"
    ))
    regNamed("wms111", wms111sd)

    // 2.8.2
    //        reg(new SLDHandler)

    wms.setApplicationContext(ctx)

    val dispatcher = new Dispatcher

    val wac = new StaticWebApplicationContext

    val mapping = new OWSHandlerMapping(catalog)
    mapping.setAlwaysUseFullPath(true)
    mapping.setUrlMap(Map(
      "/ows" -> dispatcher,
      "/ows/*" -> dispatcher,
      "/wms" -> dispatcher,
      "/wms/*" -> dispatcher
    ))



    wac.getBeanFactory.registerSingleton("mapping", mapping)
    mapping.setApplicationContext(wac)
    dispatcher.setApplicationContext(ctx)

    val gs = new GS(
      wac,
      catalog,
      input.workdir,
      addFactory
    )


    val ws = gs.createWS(input.defaultWorkspace,true)

    (gs, ws)

  }


  def createStyle() : scala.xml.Node =
    <StyledLayerDescriptor version="1.0.0"
                           xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd"
                           xmlns="http://www.opengis.net/sld"
                           xmlns:ogc="http://www.opengis.net/ogc"
                           xmlns:xlink="http://www.w3.org/1999/xlink"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <NamedLayer>
        <Name>Simple point</Name>
        <UserStyle>
          <Title>GeoServer SLD Cook Book: Simple point</Title>
          <FeatureTypeStyle>
            <Rule>
              <PointSymbolizer>
                <Graphic>
                  <Mark>
                    <WellKnownName>circle</WellKnownName>
                    <Fill>
                      <CssParameter name="fill">#FF0000</CssParameter>
                    </Fill>
                  </Mark>
                  <Size>6</Size>
                </Graphic>
              </PointSymbolizer>
            </Rule>
          </FeatureTypeStyle>
        </UserStyle>
      </NamedLayer>
    </StyledLayerDescriptor>

  val defaultStyle =
    <StyledLayerDescriptor version="1.0.0"
                           xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd"
                           xmlns="http://www.opengis.net/sld"
                           xmlns:ogc="http://www.opengis.net/ogc"
                           xmlns:xlink="http://www.w3.org/1999/xlink"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <NamedLayer>
        <Name>default</Name>
        <UserStyle>
          <Title>default</Title>
          <FeatureTypeStyle>
            <Rule>
              <PolygonSymbolizer>
                <Fill>
                  <CssParameter name="fill">#000080</CssParameter>
                </Fill>
              </PolygonSymbolizer>
              <PointSymbolizer>
                <Graphic>
                  <Mark>
                    <WellKnownName>circle</WellKnownName>
                    <Fill>
                      <CssParameter name="fill">#FF0000</CssParameter>
                    </Fill>
                  </Mark>
                  <Size>6</Size>
                </Graphic>
              </PointSymbolizer>
            </Rule>
          </FeatureTypeStyle>
        </UserStyle>
      </NamedLayer>
    </StyledLayerDescriptor>


}

class GS(
  val webApplicationContext: WebApplicationContext,
  val catalog: Catalog,
  val workdir: File,
  val addFactory : GeoserverBuilder.AddFactory
) {
  def createWS(name: String, default : Boolean = false) : WS = {
    val workspaceInfo = new WorkspaceInfoImpl
    workspaceInfo.setName(name)
    workspaceInfo.setDefault(default)
    catalog.add(workspaceInfo)

    val namespaceInfo = new NamespaceInfoImpl
    namespaceInfo.setPrefix(name)
    namespaceInfo.setURI(name)
    catalog.add(namespaceInfo)

    new WS(
      this,
      workspaceInfo,
      namespaceInfo
    )
  }

  def addStyle(
    name: String,
    style: Node
  ) : StyleInfoImpl = {

    val styleFileName = s"${name}.sld"
    val stylesDir = workdir / "styles"
    stylesDir.mkdirs()
    XML.save(
      (stylesDir / styleFileName).absolutePath,
      style
    )

    val styleInfo = new StyleInfoImpl(catalog)
    styleInfo.setName(s"${name}_style")
    styleInfo.setFilename(styleFileName)
    catalog.add(styleInfo)

    styleInfo
  }
}

class WS(
  gs: GS,
  workspaceInfo: WorkspaceInfoImpl,
  namespaceInfo: NamespaceInfoImpl
) {
  import gs._


  def addLayer(
    layerName: String,
    dataAccessId: String,
    styleInfo: StyleInfo
  ) = {
    val storeInfo = new DataStoreInfoImpl(catalog)
    storeInfo.setName(layerName)
    storeInfo.setWorkspace(workspaceInfo)
    storeInfo.setType(layerName)
    storeInfo.setEnabled(true)
    storeInfo.setConnectionParameters(Map[String, Serializable](
      GeoserverBuilder.ParamName -> dataAccessId
    ))
    catalog.add(storeInfo)

    val resourceInfo = new FeatureTypeInfoImpl(catalog)
    resourceInfo.setName(layerName)
    resourceInfo.setStore(storeInfo)
    resourceInfo.setNamespace(namespaceInfo)
    resourceInfo.setEnabled(true)
    resourceInfo.setProjectionPolicy(ProjectionPolicy.NONE)
    resourceInfo.setSRS("EPSG:4326")
    resourceInfo.setLatLonBoundingBox(new ReferencedEnvelope(-180, 180, -90, 90, DefaultGeographicCRS.WGS84))
//    resourceInfo.setProjectionPolicy(ProjectionPolicy.REPROJECT_TO_DECLARED)
//    resourceInfo.setSRS("EPSG:3395")
//    resourceInfo.setNativeCRS(DefaultGeographicCRS.WGS84)
//    resourceInfo.setNativeBoundingBox(new ReferencedEnvelope(-180, 180, -85, 85, DefaultGeographicCRS.WGS84))
//    resourceInfo.setLatLonBoundingBox(resourceInfo.getNativeBoundingBox)//.transform(resourceInfo.getCRS, true))
//    resourceInfo.setLatLonBoundingBox(new ReferencedEnvelope(-180, 180, -85, 85, DefaultGeographicCRS.WGS84))
    resourceInfo.setNativeBoundingBox(resourceInfo.getLatLonBoundingBox.transform(resourceInfo.getCRS, true))
    catalog.add(resourceInfo)

    val layerInfo = new LayerInfoImpl
    layerInfo.setResource(resourceInfo)
    layerInfo.setName(layerName)
    layerInfo.setDefaultStyle(styleInfo)
    catalog.add(layerInfo)
  }
}