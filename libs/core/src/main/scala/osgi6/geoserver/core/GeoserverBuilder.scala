package osgi6.geoserver.core

import java.io.File
import java.util

import org.geoserver.catalog.ProjectionPolicy
import org.geoserver.catalog.impl._
import org.geoserver.config.impl.GeoServerImpl
import org.geoserver.ows.{Dispatcher, OWSHandlerMapping}
import org.geoserver.platform.{GeoServerExtensions, GeoServerResourceLoader, Service}
import org.geoserver.wfs.{WFSXStreamLoader, WFSLoader, WFSInfoImpl}
import org.geoserver.wfs.kvp.BBoxKvpParser
import org.geoserver.wms._
import org.geoserver.wms.capabilities.{CapabilitiesKvpReader, Capabilities_1_3_0_Response}
import org.geoserver.wms.featureinfo._
import org.geoserver.wms.legendgraphic.{GetLegendGraphicKvpReader, PNGLegendGraphicResponse, PNGLegendOutputFormat}
import org.geoserver.wms.map._
import org.geotools.data.{DataAccessFactory, DataStore}
import org.geotools.factory.{FactoryIteratorProvider, GeoTools, Hints}
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.geotools.util.{Converter, ConverterFactory, Version}
import org.springframework.context.support.StaticApplicationContext
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
class GeoserverBuilder(workdir: File, dataStoreMap : Map[String, DataStore]) {

  val dafClass = classOf[DataAccessFactory]

//  val aisFactory : DataAccessFactory = new AisDataAccessFactory(
//    dataStoreMap
//  )

  System.setProperty("org.geotools.referencing.forceXY", "true")


//  GeoTools.addFactoryIteratorProvider(new FactoryIteratorProvider {
//    override def iterator[T](category: Class[T]): util.Iterator[T] = {
//      category match {
//        case `dafClass` =>
//          Iterator.single[T](aisFactory.asInstanceOf[T])
//        case _ =>
//          Iterator.empty
//      }
//    }
//  })

  val ctx = new StaticApplicationContext()
  def regNamed(name:String, bean:Any):Unit = {
    ctx.getBeanFactory.registerSingleton(name, bean)
  }
  def reg(bean:Any):Unit = {
    regNamed(bean.getClass.getCanonicalName, bean)
  }

  new GeoServerExtensions().setApplicationContext(ctx)

  val watermark = new WatermarkInfoImpl
  watermark.setEnabled(true)
  watermark.setURL(getClass.getResource("/emsa.png").toExternalForm)
  watermark.setTransparency(64)

  val geoserver = new GeoServerImpl
  val wmsInfo = new WMSInfoImpl()
  wmsInfo.setTitle("EMSA Public WMS")
  wmsInfo.setAbstract("EMSA Public WMS")
  wmsInfo.setWatermark(watermark)
  geoserver.add(wmsInfo)


  val catalog = new CatalogImpl
  regNamed("catalog", catalog)
  geoserver.setCatalog(catalog)
  val gsrl = new GeoServerResourceLoader(workdir)
  reg(gsrl)
  // 2.5
  GeoserverDataDirectory.setResourceLoader(gsrl)
  catalog.setResourceLoader(gsrl)

//  val wfsLoader = new WFSXStreamLoader(gsrl)
//  val wfsInfo = wfsLoader.create(geoserver)
//  geoserver.add(wfsInfo)

  val workspaceInfo = new WorkspaceInfoImpl
  workspaceInfo.setName("sofia")
  workspaceInfo.setDefault(true)
  catalog.add(workspaceInfo)

  val namespaceInfo = new NamespaceInfoImpl
  namespaceInfo.setPrefix("sofia")
  namespaceInfo.setURI("sofia")
  catalog.add(namespaceInfo)

//  def addAisLayer(layerName: String, style: Node) = {
//    val storeInfo = new DataStoreInfoImpl(catalog)
//    storeInfo.setName(s"${layerName}_store")
//    storeInfo.setWorkspace(workspaceInfo)
//    storeInfo.setType("AIS")
//    storeInfo.setEnabled(true)
//    storeInfo.setConnectionParameters(Map(
//      AisDataAccessFactory.nameParam.getName -> layerName
//    ))
//    catalog.add(storeInfo)
//
//
//    val resourceInfo = new FeatureTypeInfoImpl(catalog)
//    resourceInfo.setName(AisDataAccessFactory.featureType.getTypeName)
//    resourceInfo.setStore(storeInfo)
//    resourceInfo.setNamespace(namespaceInfo)
//    resourceInfo.setEnabled(true)
//    resourceInfo.setProjectionPolicy(ProjectionPolicy.NONE)
//    resourceInfo.setSRS("EPSG:4326")
//    resourceInfo.setLatLonBoundingBox(new ReferencedEnvelope(-90, 90, -180, 180, DefaultGeographicCRS.WGS84))
//    catalog.add(resourceInfo)
//
//    val styleFileName = s"${layerName}.sld"
//    val stylesDir = workdir / "styles"
//    stylesDir.mkdirs()
//    XML.save(
//      (stylesDir / styleFileName).absolutePath,
//      style
//    )
//
//    val styleInfo = new StyleInfoImpl(catalog)
//    styleInfo.setName(s"${layerName}_style")
//    styleInfo.setFilename(styleFileName)
//    catalog.add(styleInfo)
//
//    val layerInfo = new LayerInfoImpl
//    layerInfo.setResource(resourceInfo)
//    layerInfo.setName(layerName)
//    layerInfo.setDefaultStyle(styleInfo)
//    catalog.add(layerInfo)
//  }

  def build() = {
    val wms = new WMS(geoserver)

    val wmsService = new DefaultWebMapService(wms)


    val getCapabilities = new GetCapabilities(wms)
    wmsService.setGetCapabilities(getCapabilities)

    reg(new CapabilitiesKvpReader(wms))

    // 2.8.2
//        reg(new DimensionDefaultValueSelectionStrategyFactoryImpl)
    reg(new Capabilities_1_3_0_Response)
    reg(new BBoxKvpParser)
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

    val wms130sd = new Service("wms", wmsService, new Version("1.3.0"), List(
      "GetCapabilities",
      "GetMap",
      "GetFeatureInfo",
      "GetLegendGraphic"
    ))
    reg(wms130sd)

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

    wac
  }

}

object GeoserverStarter {

  def setupGeoserver(workdir: File, dataStoreMap : Map[String, DataStore]) = {
    val builder = new GeoserverBuilder(workdir, dataStoreMap)

    val style : scala.xml.Node =
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
                      <WellKnownName>emsa://vessel</WellKnownName>
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

    for {
      name <- dataStoreMap.keys
    } {
//      builder.addAisLayer(name, aisStyle)
    }


//    println(AisMessageProcessor.pp.format(aisStyle))



    builder.build()
  }

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


}

