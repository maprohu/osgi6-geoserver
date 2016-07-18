/*
 * h2gis-gs is an extension to geoserver to connect H2GIS a spatial library 
 * that brings spatial support to the H2 database engine.
 *
 * h2gis-gs  is distributed under GPL 3 license. It is produced by the DECIDE
 * team of the Lab-STICC laboratory <http://www.labsticc.fr/> CNRS UMR 6285.
 *
 * Copyright (C) 2015-2016 Lab-STICC (CNRS UMR 6285)
 *
 * h2gis-gs  is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * h2gis-gs  is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * h2gis-gs. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */
package org.orbisgis.geoserver.h2gis.datastore;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.ParseException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import static junit.framework.TestCase.assertNotNull;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.jdbc.VirtualTable;
import org.geotools.referencing.CRS;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Function;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.Intersects;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 *
 * @author Erwan Bocher
 */
public class H2GISTest extends H2GISDBTestSetUp {

    private Statement st;

    @Before
    public void setUpStatement() throws Exception {
        st = ds.getDataSource().getConnection().createStatement();
    }

    @After
    public void tearDownStatement() throws Exception {
        st.close();
    }

    @Test
    public void createSpatialTables() throws SQLException {
        st.execute("DROP SCHEMA IF EXISTS h2gis; COMMIT;");
        st.execute("CREATE SCHEMA h2gis;");
        st.execute("DROP TABLE IF EXISTS h2gis.geomtable; COMMIT;");

        String sql = "CREATE TABLE h2gis.geomtable (id int AUTO_INCREMENT(1) PRIMARY KEY, "
                + "the_geom POINT)";
        st.execute(sql);

        sql = "INSERT INTO h2gis.geomtable VALUES ("
                + "0,ST_GeomFromText('POINT(12 0)',4326));";
        st.execute(sql);

        ResultSet rs = st.executeQuery("select count(id) from h2gis.geomtable");

        assertTrue(rs.next());
        assertTrue(rs.getInt(1) == 1);
        rs.close();

        rs = st.executeQuery("select * from h2gis.geomtable;");
        rs.next();
        assertTrue(rs.getInt(1) == 0);
        assertEquals("POINT (12 0)", rs.getString(2));
        rs.close();
        st.execute("DROP TABLE h2gis.geomtable");
    }

    @Test
    public void getFeatureSchema() throws SQLException, IOException {
        st.execute("drop table if exists FORESTS");
        st.execute("CREATE TABLE FORESTS ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM MULTIPOLYGON);"
                + "INSERT INTO FORESTS VALUES(109, 'Green Forest', ST_MPolyFromText( 'MULTIPOLYGON(((28 26,28 0,84 0,"
                + "84 42,28 26), (52 18,66 23,73 9,48 6,52 18)),((59 18,67 18,67 13,59 13,59 18)))', 101));");

        SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("FORESTS");
        SimpleFeatureType schema = fs.getSchema();
        Query query = new Query(schema.getTypeName(), Filter.INCLUDE);
        assertEquals(1, fs.getCount(query));
        assertEquals(3, schema.getAttributeCount());
        assertEquals("THE_GEOM", schema.getGeometryDescriptor().getLocalName());
        assertEquals("FID", schema.getDescriptor(0).getLocalName());
        assertEquals("NAME", schema.getDescriptor(1).getLocalName());
        assertEquals("THE_GEOM", schema.getDescriptor(2).getLocalName());
        st.execute("drop table FORESTS");
    }

    @Test
    public void getBoundingBox() throws SQLException, IOException, ParseException {
        st.execute("drop table if exists FORESTS");
        st.execute("CREATE TABLE FORESTS ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM POLYGON);"
                + "INSERT INTO FORESTS VALUES(109, 'Green Forest', 'POLYGON((0 0,10 0,10 10, 0 10, 0 0))');");

        SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("FORESTS");
        SimpleFeatureType schema = fs.getSchema();
        Query query = new Query(schema.getTypeName(), Filter.INCLUDE);
        ReferencedEnvelope bounds = fs.getBounds(query);
        if (bounds == null) {
            FeatureCollection<SimpleFeatureType, SimpleFeature> collection = fs.getFeatures(query);
            bounds = collection.getBounds();
        }
        assertTrue(JTS.toEnvelope(wKTReader.read("POLYGON((0 0,10 0,10 10, 0 10, 0 0))")).boundsEquals2D(bounds, 0.01));

        st.execute("drop table FORESTS");
    }

    @Test
    public void getFeatures() throws SQLException, IOException {
        st.execute("drop table if exists LANDCOVER");
        st.execute("CREATE TABLE LANDCOVER ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM POLYGON);"
                + "INSERT INTO LANDCOVER VALUES(1, 'Green Forest', 'POLYGON((110 330, 210 330, 210 240, 110 240, 110 330))');"
                + "INSERT INTO LANDCOVER VALUES(2, 'Cereal', 'POLYGON((200 220, 310 220, 310 160, 200 160, 200 220))');"
                + "INSERT INTO LANDCOVER VALUES(3, 'Building', 'POLYGON((90 130, 140 130, 140 110, 90 110, 90 130))');");

        SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("LANDCOVER");
        SimpleFeatureCollection features = fs.getFeatures(Filter.INCLUDE);

        SimpleFeatureIterator iterator = features.features();

        double sumArea = 0;
        int sumFID = 0;
        try {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                sumArea += geom.getArea();
                sumFID += (Integer) feature.getAttribute("FID");
            }
        } finally {
            iterator.close();
        }
        assertEquals(16600.0, sumArea, 0.1);
        assertEquals(6, sumFID, 0.1);
        st.execute("drop table LANDCOVER");
    }

    @Test
    public void getFeaturesFilter() throws SQLException, IOException {
        st.execute("drop table if exists LANDCOVER");
        st.execute("CREATE TABLE LANDCOVER ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM POLYGON);"
                + "INSERT INTO LANDCOVER VALUES(1, 'Green Forest', 'POLYGON((110 330, 210 330, 210 240, 110 240, 110 330))');"
                + "INSERT INTO LANDCOVER VALUES(2, 'Cereal', 'POLYGON((200 220, 310 220, 310 160, 200 160, 200 220))');"
                + "INSERT INTO LANDCOVER VALUES(3, 'Building', 'POLYGON((90 130, 140 130, 140 110, 90 110, 90 130))');");

        SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("LANDCOVER");
        SimpleFeatureCollection features = fs.getFeatures(Filter.INCLUDE);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Function sum = ff.function("Collection_Sum", ff.property("FID"));
        Object value = sum.evaluate(features);
        assertEquals(6L, value);
        st.execute("drop table LANDCOVER");
    }
    
    @Test
    public void getFeaturesFilter2() throws Exception {
        st.execute("drop table if exists LANDCOVER");
        st.execute("CREATE TABLE LANDCOVER ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM POLYGON);"
                + "INSERT INTO LANDCOVER VALUES(1, 'Green Forest', 'POLYGON((110 330, 210 330, 210 240, 110 240, 110 330))');"
                + "INSERT INTO LANDCOVER VALUES(2, 'Cereal', 'POLYGON((200 220, 310 220, 310 160, 200 160, 200 220))');"
                + "INSERT INTO LANDCOVER VALUES(3, 'Building', 'POLYGON((90 130, 140 130, 140 110, 90 110, 90 130))');");

        SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("LANDCOVER");
        Filter filter = CQL.toFilter("FID >2");
        SimpleFeatureCollection features = fs.getFeatures(filter);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Function sum = ff.function("Collection_Sum", ff.property("FID"));
        Object value = sum.evaluate(features);
        assertEquals(3L, value);
        st.execute("drop table LANDCOVER");
    }
    
    @Test
    public void getFeaturesFilter3() throws SQLException, IOException {
        try {
            st.execute("drop table if exists LANDCOVER");
            st.execute("CREATE TABLE LANDCOVER ( FID INTEGER, CODE INTEGER,"
                    + " THE_GEOM POLYGON);"
                    + "INSERT INTO LANDCOVER VALUES(1, -1, 'POLYGON((110 330, 210 330, 210 240, 110 240, 110 330))');"
                    + "INSERT INTO LANDCOVER VALUES(2, 3, 'POLYGON((200 220, 310 220, 310 160, 200 160, 200 220))');"
                    + "INSERT INTO LANDCOVER VALUES(3, -1, 'POLYGON((90 130, 140 130, 140 110, 90 110, 90 130))');");
            
            SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("LANDCOVER");
            Filter filter = CQL.toFilter("FID < abs(CODE)" );
            SimpleFeatureCollection features = fs.getFeatures(filter);
            assertTrue(features.size()==1);
            st.execute("drop table LANDCOVER");
        } catch (CQLException ex) {
            Logger.getLogger(H2GISTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    @Test
    public void testBboxFilter() throws SQLException, IOException, CQLException, ParseException {
        st.execute("drop table if exists LANDCOVER");
        st.execute("CREATE TABLE LANDCOVER ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM POINT);"
                + "INSERT INTO LANDCOVER VALUES(1, 'Green Forest', 'POINT(5 5)');"
                + "INSERT INTO LANDCOVER VALUES(2, 'Cereal', 'POINT(200 220)');"
                + "INSERT INTO LANDCOVER VALUES(3, 'Building', 'POINT(90 130)');");
        FilterFactory ff = CommonFactoryFinder.getFilterFactory(null);
        BBOX bbox = ff.bbox("THE_GEOM", 0, 0, 10, 10, "EPSG:4326");
        FeatureCollection fc = ds.getFeatureSource("LANDCOVER").getFeatures(bbox);
        assertTrue(fc.size()==1);
        SimpleFeature[] features = (SimpleFeature[]) fc.toArray(new SimpleFeature[fc.size()]);
        assertTrue(features[0].getDefaultGeometry().equals(wKTReader.read("POINT(5 5)")));
        st.execute("drop table LANDCOVER");
    }
    
    @Test
    public void testIntersectsFilter() throws Exception {
        st.execute("drop table if exists LANDCOVER");
        st.execute("CREATE TABLE LANDCOVER ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM POINT);"
                + "INSERT INTO LANDCOVER VALUES(1, 'Green Forest', 'POINT(5 5)');"
                + "INSERT INTO LANDCOVER VALUES(2, 'Cereal', 'POINT(200 220)');"
                + "INSERT INTO LANDCOVER VALUES(3, 'Building', 'POINT(90 130)');");
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);
        Intersects is = ff.intersects(ff.property("THE_GEOM"), ff.literal(wKTReader.read("POINT(5 5)")));
        FeatureCollection fc = ds.getFeatureSource("LANDCOVER").getFeatures(is);
        assertTrue(fc.size() == 1);
        SimpleFeature[] features = (SimpleFeature[]) fc.toArray(new SimpleFeature[fc.size()]);
        assertTrue(features[0].getDefaultGeometry().equals(wKTReader.read("POINT(5 5)")));
        st.execute("drop table LANDCOVER");       
    }
    
    @Test
    public void testNoCRS() throws Exception {
        st.execute("drop table if exists LANDCOVER");
        st.execute("CREATE TABLE LANDCOVER ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM POLYGON);"
                + "INSERT INTO LANDCOVER VALUES(1, 'Green Forest', 'POLYGON((110 330, 210 330, 210 240, 110 240, 110 330))');"
                + "INSERT INTO LANDCOVER VALUES(2, 'Cereal', 'POLYGON((200 220, 310 220, 310 160, 200 160, 200 220))');"
                + "INSERT INTO LANDCOVER VALUES(3, 'Building', 'POLYGON((90 130, 140 130, 140 110, 90 110, 90 130))');");

        SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("LANDCOVER");
        SimpleFeatureCollection features = fs.getFeatures(Filter.INCLUDE);
        CoordinateReferenceSystem crs = features.getBounds().getCoordinateReferenceSystem();
        assertNull(crs);
        st.execute("drop table LANDCOVER");
    }

    @Test
    public void testWithCRS() throws Exception {
        st.execute("drop table if exists FORESTS");
        st.execute("CREATE TABLE FORESTS ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM MULTIPOLYGON);"
                + "INSERT INTO FORESTS VALUES(109, 'Green Forest', ST_MPolyFromText( 'MULTIPOLYGON(((28 26,28 0,84 0,"
                + "84 42,28 26), (52 18,66 23,73 9,48 6,52 18)),((59 18,67 18,67 13,59 13,59 18)))', 4326));");

        SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("FORESTS");
        SimpleFeatureCollection features = fs.getFeatures(Filter.INCLUDE);
        CoordinateReferenceSystem crs = features.getBounds().getCoordinateReferenceSystem();
        assertNotNull(crs);
        assertEquals("EPSG:4326", CRS.lookupIdentifier(crs, true));
        st.execute("drop table FORESTS");
    }
    
    @Test
    public void testVirtualTable() throws SQLException, IOException, ParseException {
        st.execute("drop table if exists LANDCOVER");
        st.execute("CREATE TABLE LANDCOVER ( FID INTEGER, NAME CHARACTER VARYING(64),"
                + " THE_GEOM POLYGON);"
                + "INSERT INTO LANDCOVER VALUES(1, 'Green Forest', 'POLYGON((110 330, 210 330, 210 240, 110 240, 110 330))');"
                + "INSERT INTO LANDCOVER VALUES(2, 'Cereal', 'POLYGON((200 220, 310 220, 310 160, 200 160, 200 220))');"
                + "INSERT INTO LANDCOVER VALUES(3, 'Building', 'POLYGON((90 130, 140 130, 140 110, 90 110, 90 130))');");
        VirtualTable vTable = new VirtualTable("LANDCOVER_CEREAL", "SELECT * FROM PUBLIC.LANDCOVER WHERE FID=2");
        vTable.addGeometryMetadatata("THE_GEOM", Polygon.class, 4326);
        ds.createVirtualTable(vTable);
        SimpleFeatureType type = ds.getSchema("LANDCOVER_CEREAL");
        assertNotNull(type);        
        assertNotNull(type.getGeometryDescriptor());
        FeatureSource fsView = ds.getFeatureSource("LANDCOVER_CEREAL");
        ReferencedEnvelope env = fsView.getBounds();
        assertNotNull(env);
        assertTrue(JTS.toEnvelope(wKTReader.read("POLYGON((200 220, 310 220, 310 160, 200 160, 200 220))")).boundsEquals2D(env, 0.01));
        ds.dropVirtualTable("LANDCOVER_CEREAL");        
        st.execute("drop table LANDCOVER");
    }
    
    
//    @Test
//    public void testH2GISFileTable() throws SQLException, IOException {
//        st.execute("drop table if exists LANDCOVER");
//        st.execute("CALL FILE_TABLE('" + H2GISTest.class.getResource("landcover.shp").getPath() + "', 'LANDCOVER');");
//        assertTrue(st.execute("SELECT * FROM LANDCOVER LIMIT 0;"));
//        SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("LANDCOVER");
//        SimpleFeatureType schema = fs.getSchema();
//        Query query = new Query(schema.getTypeName(), Filter.INCLUDE);
//        assertEquals(3, fs.getCount(query));
//        st.execute("drop table LANDCOVER");
//    }
}
