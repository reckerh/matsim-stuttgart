package org.matsim.stuttgart.prepare;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.testcases.MatsimTestUtils;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ElevationReaderTest {
    private final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:4326");

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    @Test
    public void testSingleFile() {

        var stuttgartElevationMap = testUtils.getClassInputDirectory() + "stuttgart_elevation.tif";


        var reader = new ElevationReader(List.of(stuttgartElevationMap), transformation);

        var elevation = reader.getElevationAt(new Coord(9.17968, 48.77863));

        assertEquals(249.1, elevation, 0.1);
    }

    @Test
    public void testTwoFiles() {

        var stuttgartElevationMap = testUtils.getClassInputDirectory() + "stuttgart_elevation.tif";
        var ingolstadElevationMap = testUtils.getClassInputDirectory() + "ingolstadt_elevation.tif";

        var reader = new ElevationReader(List.of(stuttgartElevationMap, ingolstadElevationMap), transformation);

        var elevationStuttgart = reader.getElevationAt(new Coord(9.17968, 48.77863));
        assertEquals(249.1, elevationStuttgart, 0.1);

        var elevationIngolstadt = reader.getElevationAt(new Coord(11.42398, 48.76839));
        assertEquals(377.3, elevationIngolstadt, 0.1);
    }
}