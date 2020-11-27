package org.matsim.stuttgart.prepare;

import org.apache.log4j.Logger;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.Envelope2D;
import org.geotools.referencing.CRS;
import org.geotools.util.factory.Hints;
import org.junit.Test;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;


public class GeoTiffParserTest {

	private static final Logger log = Logger.getLogger(GeoTiffParserTest.class);

	private static final String senozonNetworkPath = "projects\\matsim-stuttgart\\stuttgart-v0.0-snz-original\\optimizedNetwork.xml.gz";

	private static Rectangle2D.Double getBBox(Path sharedSvn) {

		var senozonNetwork = NetworkUtils.createNetwork();
		new MatsimNetworkReader(senozonNetwork).readFile(sharedSvn.resolve(senozonNetworkPath).toString());

		var bbox = BoundingBox.fromNetwork(senozonNetwork);

		log.info(bbox.toString());
		return bbox.toRectangle();
	}

	private static Rectangle2D getBBoxFixed() {
		var minX = 446195.36318702064;
		var minY = 5355122.108383886;
		var maxX = 577593.2167098892;
		var maxY = 5482076.161082436;
		return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
	}

	@Test
	public void test() throws IOException, TransformException, FactoryException {

		var file = new File("C:\\Users\\Janek\\Downloads\\97824c12f357f50638d665b5a58707cd82857d57\\eu_dem_v11_E40N20\\eu_dem_v11_E40N20.TIF");
		var reader = new GeoTiffReader(file, new Hints(Hints.CRS, CRS.decode("EPSG:3035")));

		var coverage = (GridCoverage2D) reader.read(null);
		var crs = coverage.getCoordinateReferenceSystem2D();
		var epsg = CRS.lookupEpsgCode(crs, true);
		var envelope = coverage.getEnvelope();
		var image = coverage.getRenderedImage();

		var sharedSvn = Paths.get("C:\\Users\\Janek\\repos\\shared-svn");
		//var bbox = getBBox(sharedSvn);
		var bbox = getBBoxFixed();
		var bboxEnvelope = new Envelope2D(CRS.decode("EPSG:25832", true), bbox);
		var bboxEnvelopeTransformed = CRS.transform(bboxEnvelope, crs);
		var bboxAsPixels = coverage.getGridGeometry().worldToGrid(new Envelope2D(null, bboxEnvelopeTransformed.toRectangle2D())); // the bounding box is in Utm32
		var bboxAsPixelsAsRectangle = bboxAsPixels.getBounds();
		var theData = image.getData(bboxAsPixelsAsRectangle);

		assertNotNull(image);
	}

	@Test
	public void test2() throws IOException, FactoryException, TransformException {

		var file = new File("C:\\Users\\Janek\\Downloads\\srtm_38_03\\srtm_38_03.tif");
		var reader = new GeoTiffReader(file);

		var coverage = (GridCoverage2D) reader.read(null);
		var crs = coverage.getCoordinateReferenceSystem2D();
		var epsg = CRS.lookupEpsgCode(crs, true);
		var envelope = coverage.getEnvelope();
		var image = coverage.getRenderedImage();

		var bbox = getBBoxFixed();
		var bboxEnvelope = new Envelope2D(CRS.decode("EPSG:25832"), bbox);

		// this needs to be lon first, since the tif is also encoded longitude first
		var bboxEnvelopeTransformed = CRS.transform(bboxEnvelope, CRS.decode("EPSG:4326", true));

		var bboxAsPixels = coverage.getGridGeometry().worldToGrid(new Envelope2D(crs, bboxEnvelopeTransformed.toRectangle2D()));
		var subData = image.getData(bboxAsPixels.getBounds());
		assertNotNull(subData);

		var gridPosition = coverage.getGridGeometry().worldToGrid(new DirectPosition2D(9.182932, 48.775845));
		double[] outPixel = new double[1];
		image.getData().getPixel(gridPosition.x, gridPosition.y, outPixel);
		assertNotNull(outPixel);


	}
}
