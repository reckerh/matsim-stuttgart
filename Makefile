
JAR := matsim-duesseldorf-*.jar

$(JAR):
	mvn package

# Required files
scenarios/input/network.osm.pbf:
	curl https://download.geofabrik.de/europe/germany/nordrhein-westfalen/duesseldorf-regbez-latest.osm.pbf\
	  -o scenarios/input/network.osm.pbf

scenarios/input/gtfs.zip:
	curl https://openvrr.de/dataset/c415abd6-3b63-4a1f-8a17-9b77cf5f09ec/resource/52d90889-8b74-4f1e-b3ee-1dc8af70d164/download/2020_03_03_google_transit_verbundweit_inkl_spnv.zip\
	  -o scenarios/input/gtfs.zip


# Creates input files for the scenario
prepare: $(JAR) scenarios/input/network.osm.pbf scenarios/input/gtfs.zip

	java -jar $(JAR) prepare network
	java -jar $(JAR) prepare transit
	java -jar $(JAR) prepare population\
	 --population ../../shared-svn/komodnext/matsim-input-files/duesseldorf-senozon/optimizedPopulation_filtered.xml.gz\
	 --attributes  ../../shared-svn/komodnext/matsim-input-files/duesseldorf-senozon/personAttributes.xml.gz